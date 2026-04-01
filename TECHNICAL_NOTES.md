# Technical Architecture & Implementation Notes

## Обзор архитектуры

VkMidSprint состоит из трех основных слоев:

```
┌─────────────────────────────────────────────────────┐
│        gRPC API Layer (KeyValueGrpcService)         │
│        - Request/Response обработка                  │
│        - Validation и error handling                 │
│        - Server-side streaming                       │
└────────────────┬────────────────────────────────────┘
                 │
┌────────────────▼────────────────────────────────────┐
│    Business Logic Layer (KeyValueService)            │
│    - Composition of Tarantool operations             │
│    - Response normalization                          │
│    - Async handling (CompletableFuture)              │
└────────────────┬────────────────────────────────────┘
                 │
┌────────────────▼────────────────────────────────────┐
│   Tarantool Integration Layer                        │
│   - LuaExpression: Lua-скрипты                      │
│   - TarantoolClient: Async Tarantool API             │
│   - ByteValueConverter: Type conversion              │
│   - TarantoolResponseParser: Response parsing        │
└─────────────────────────────────────────────────────┘
```

## Детальное описание компонентов

### 1. LuaExpression.java

**Назначение:** Централизованное хранилище всех Lua выражений для Tarantool операций.

**Основные методы:**
- `replace()` — INSERT/UPDATE операция
- `selectByKey()` — SELECT по одному ключу
- `rangeByKey()` — SELECT по диапазону ключей с фильтрацией и сортировкой
- `delete()` — DELETE по ключу
- `count()` — COUNT всех записей

**Реализация range:**
```lua
local key_since, key_to = ...
local result = {}
for _, tuple in box.space.kv:pairs() do
    if tuple[1] >= key_since and tuple[1] <= key_to then
        table.insert(result, tuple)
    end
end
table.sort(result, function(a, b) return a[1] < b[1] end)
return result
```

**Почему Lua в Java?**
- Lua выполняется атомарно на стороне Tarantool
- Позволяет избежать множественных round-trips к БД
- Поддерживает сложные операции (range, фильтрация, сортировка)
- Уменьшает нагрузку на сеть

### 2. TarantoolResponseParser.java

**Назначение:** Нормализация и парсинг ответов от Tarantool с учетом различных форм сериализации.

**Ключевая проблема:** Tarantool через gRPC-клиент может вернуть ответ в виде:
- `[[key, value]]` — нормальный формат
- `[[[key, value]]]` — дополнительная вложенность от eval()
- `[key, value]` — иногда без оборачивания

**Решение:**
```java
public static List<?> getFirstTuple(List<?> tuples) {
    Object firstTuple = tuples.getFirst();
    if (firstTuple instanceof List<?> list) {
        // Обработка вложенности: [[[key,value]]] -> [[key,value]]
        if (list.size() == 1 && list.getFirst() instanceof List<?> nestedTuple) {
            return nestedTuple;
        }
        return list;
    }
    return Collections.emptyList();
}
```

**Методы:**
- `extractTuples(response)` — получить список кортежей
- `getFirstTuple(tuples)` — достать первый кортеж с нормализацией
- `getValue(tuple, index)` — безопасно получить поле tuple
- `normalizeTuples(tuples)` — нормализовать всю коллекцию
- `asTuple(obj)` — привести объект к tuple с проверкой типов

### 3. ByteValueConverter.java

**Назначение:** Преобразование значений между Java и Tarantool типами.

**Проблема:** Tarantool может вернуть `byte[]` в разных форматах:

```json
// Формат 1: Нативный byte[]
"value": [123, 52, 51, 52]

// Формат 2: Map с числовыми ключами (JSON десериализация)
"value": {
    "0": 123,
    "1": 52,
    "2": 51,
    "3": 52
}
```

**Решение:**
```java
public static ByteString toByteString(Object value) {
    if (value instanceof byte[] bytes) {
        return ByteString.copyFrom(bytes);
    }
    if (value instanceof Map<?, ?> map) {
        return mapToByteString(map);
    }
    // ... другие case'ы
}

private static ByteString mapToByteString(Map<?, ?> map) {
    // Находим максимальный индекс
    int maxIndex = -1;
    for (Object key : map.keySet()) {
        Integer index = parseIndex(key);
        if (index != null && index > maxIndex) {
            maxIndex = index;
        }
    }
    // Восстанавливаем byte[] из map
    byte[] bytes = new byte[maxIndex + 1];
    for (Map.Entry<?, ?> entry : map.entrySet()) {
        Integer index = parseIndex(entry.getKey());
        if (index != null && index >= 0 && index < bytes.length) {
            bytes[index] = ((Number) entry.getValue()).byteValue();
        }
    }
    return ByteString.copyFrom(bytes);
}
```

### 4. KeyValueService.java

**Назначение:** Бизнес-логика работы с Tarantool, асинхронное управление.

**Ключевые особенности:**

1. **Асинхронность:**
```java
public TarantoolResponse<List<?>> get(String key) throws Exception {
    CompletableFuture<TarantoolResponse<List<?>>> future = 
        boxClient.eval(expression, input);
    TarantoolResponse<List<?>> response = future.get(); // Блокируем
    return response;
}
```

2. **Трансформация параметров в Lua:**
```java
String expression = LuaExpression.selectByKey();
List<?> input = List.of(key); // Передаем как список параметров
// Lua получит как: local key = ...
```

3. **Range операция:**
```java
public TarantoolResponse<List<?>> range(String keySince, String keyTo) 
        throws Exception {
    String expression = LuaExpression.rangeByKey();
    List<?> input = List.of(keySince, keyTo);
    // Lua получит: local key_since, key_to = ...
    CompletableFuture<TarantoolResponse<List<?>>> future = 
        boxClient.eval(expression, input);
    return future.get();
}
```

### 5. KeyValueGrpcService.java

**Назначение:** gRPC API обработчики, валидация, стриминг.

**Server Streaming (Range):**

```java
@Override
public void range(Keyvalue.RangeRequest request, 
                  StreamObserver<Keyvalue.RangeResponse> responseObserver) {
    // Валидация
    if (keySince.compareTo(keyTo) > 0) {
        responseObserver.onError(Status.INVALID_ARGUMENT.asRuntimeException());
        return;
    }
    
    try {
        // Получаем диапазон из сервиса
        TarantoolResponse<List<?>> response = keyValueService.range(keySince, keyTo);
        List<?> tuples = TarantoolResponseParser.extractTuples(response);
        
        // Стримим каждый кортеж
        for (Object tupleObj : tuples) {
            List<?> tuple = TarantoolResponseParser.asTuple(tupleObj);
            Keyvalue.RangeResponse rangeResp = Keyvalue.RangeResponse.newBuilder()
                .setKey(tuple.get(0).toString())
                .setValue(ByteValueConverter.toByteString(tuple.get(1)))
                .build();
            responseObserver.onNext(rangeResp); // Отправляем клиенту
        }
        
        responseObserver.onCompleted(); // Закрываем stream
    } catch (Exception e) {
        responseObserver.onError(e);
    }
}
```

## Особенности реализации

### Проблема 1: Response Nesting

**Симптом:** При запросе `select{key}` от Tarantool можно получить:
```
[[[key1, value1], [key2, value2]]]  // 3 уровня вложенности
[[key1, value1]]                     // 2 уровня (нормально)
[key1, value1]                       // 1 уровень (прямой tuple)
```

**Решение:** Функция `getFirstTuple()` с check на вложенность:
```java
if (list.size() == 1 && list.getFirst() instanceof List<?> nestedTuple) {
    return nestedTuple; // Разворачиваем лишние уровни
}
```

### Проблема 2: Bytes Encoding

**Симптом:** Отправляем `PUT("key", "1234")` → получаем при GET байты `215,109,248`

**Причина:** Protobuf field `bytes value` ожидает base64:
```json
// Отправляем как обычную строку
{"key":"test", "value":"1234"}

// Tarantool получает base64-декодированное: D7 6D F8 (tres элемента)
// При GET вернется MAP: {"0":215, "1":109, "2":248}
```

**Решение:** 
- Или отправлять base64: `{"key":"test", "value":"MTIzNA=="}`
- Или добавить `PutString` RPC с автоматическим UTF-8 кодированием

### Проблема 3: Hash Index для Range

**Симптом:** Range операция медленная, неоптимальная

**Причина:** В `init.lua` используется `type='hash'` индекс:
```lua
s:create_index('primary', {type='hash', parts={'key'}})
```

Hash индекс не поддерживает лексикографическое упорядочение.

**Решение (текущее):** Используем `box.space.kv:pairs()` с фильтрацией в Lua.

**Оптимизация (будущая):** Добавить `tree` индекс для range-операций:
```lua
if not box.space.kv:index('tree') then
    s:create_index('tree', {type='tree', parts={'key'}})
end
```

### Проблема 4: Type Safety в Proto

**Дизайн:** Разные RPC для разных типов возврата

```protobuf
// Вариант 1: raw bytes
rpc Get(GetRequest) returns (GetResponse);
message GetResponse {
    bytes value = 1;  // Raw bytes, клиент декодирует сам
}

// Вариант 2: UTF-8 string
rpc GetString(GetRequest) returns (GetStringResponse);
message GetStringResponse {
    string value = 1;  // Уже декодированная строка
}
```

**Преимущество:** Явность API, не нужно гадать о кодировании.

## Performance Considerations

### 1. Async/Await

```
❌ ПЛОХО (блокирует thread):
List result = blockingStub.get(...);

✅ ХОРОШО (с CompletableFuture):
CompletableFuture<Response> future = asyncStub.get(...);
future.thenAccept(response -> ...);
```

Текущая реализация использует `.get()` на `CompletableFuture`, что блокирует. Для масштабирования можно использовать `Project Reactor` или `CompletionStage`.

### 2. Batch Operations

Для больших диапазонов:
```java
// Текущее: loading всех tuple'ов в памяти
List<?> allTuples = ... // 1 миллион записей
for (Object t : allTuples) { // Полная итерация
    responseObserver.onNext(response);
}

// Оптимизация: lazy evaluation
// Добавить пагинацию в RangeRequest
```

### 3. Connection Pooling

Tarantool клиент использует connection pooling. Проверить параметры:
```yaml
tarantool:
  pool-size: 10  # max connections
  timeout: 5000  # ms
```

## Testing Strategy

### Unit Tests
```java
@Test
void testByteValueConversion() {
    // Test Map -> byte[] conversion
    Map<String, Number> input = Map.of("0", 123, "1", 45);
    ByteString result = ByteValueConverter.toByteString(input);
    assertEquals(result.toByteArray()[0], 123);
}
```

### Integration Tests
```java
@Testcontainers
class KeyValueIntegrationTest {
    @Container
    static GenericContainer<?> tarantool = 
        new GenericContainer<>("tarantool:latest")
            .withExposedPorts(3301);
    
    @Test
    void testPutGet() {
        // Реальная работа с Tarantool
    }
}
```

## Security Notes

⚠️ **Важно для production:**

1. **SQL Injection в Lua:**
```java
// ❌ НЕБЕЗОПАСНО (если key из user input)
String expression = "return box.space.kv:select{'" + key + "'}";

// ✅ БЕЗОПАСНО (параметризованно)
String expression = "local key = ...; return box.space.kv:select{key}";
List<?> input = List.of(key);
```

2. **Authentication:**
```yaml
tarantool:
  user: ${TARANTOOL_USER:appuser}
  password: ${TARANTOOL_PASSWORD:apppass}
```

3. **TLS для gRPC:**
```yaml
grpc:
  server:
    port: 9091
    enable-reflection: false  # Отключить в production!
    # ssl:
    #   enabled: true
    #   cert-chain: /path/to/cert.pem
    #   private-key: /path/to/key.pem
```

## Миграция и обновления

При изменении schema Tarantool:

```lua
-- init.lua (идемпотентный скрипт)
if not box.space.kv then
    local s = box.schema.space.create('kv')
    -- ... старая конфигурация
end

-- Добавляем новый индекс
if not box.space.kv:index('range_idx') then
    box.space.kv:create_index('range_idx', {
        type='tree', 
        parts={'key'}
    })
end
```

---

**Документ актуален на:** 02.04.2026

