# VkMidSprint — Distributed Key-Value Storage Service

> **Профильное задание первого этапа отбора на стажировку по направлению Java Developer**

## О проекте

Key-Value Storage Service — это реализация высокопроизводительного распределённого хранилища ключ-значение на основе [Tarantool](https://www.tarantool.io/), предоставляющего gRPC API для работы с данными. Проект моделирует типичные задачи, с которыми предстоит работать на стажировке: интеграция микросервисной архитектуры, асинхронная обработка данных, работа с NoSQL базами данных и построение масштабируемых сервисов.

### Ключевые компоненты

- **Tarantool** — in-memory база данных с поддержкой Lua-скриптов для сложных операций
- **gRPC** — высокопроизводительный RPC фреймворк с поддержкой streaming
- **Spring Boot** — для управления зависимостями и конфигурации
- **Lombok** — для снижения boilerplate-кода
- **Docker** — для контейнеризации приложения

## Функциональность

Реализованные операции:

| RPC метод | Описание | Тип |
|-----------|---------|-----|
| `Put(PutRequest)` | Сохранить пару ключ-значение | Unary |
| `Get(GetRequest)` | Получить значение как `bytes` | Unary |
| `GetString(GetRequest)` | Получить значение как UTF-8 строку | Unary |
| `Delete(DeleteRequest)` | Удалить запись по ключу | Unary |
| `Count()` | Подсчитать количество записей | Unary |
| `Range(RangeRequest)` | Получить все пары в диапазоне ключей | Server streaming |

## Технический стек

```
Java 25 | Spring Boot 4.0.5 | Spring gRPC 1.0.2
Tarantool 1.5.0 | Gradle 9.4.1 | Docker
```

### Зависимости

- `org.springframework.boot:spring-boot-starter-*` — основной фреймворк
- `io.tarantool:tarantool-client:1.5.0` — клиент Tarantool с поддержкой async
- `org.springframework.grpc:spring-grpc-*` — gRPC интеграция
- `org.projectlombok:lombok` — аннотации для снижения кода
- `com.google.protobuf:protoc` — компиляция .proto файлов

## Быстрый старт

### Требования

- JDK 25+
- Gradle 9.4.1+ (или использовать `gradlew`)
- Docker & Docker Compose (для запуска Tarantool)

### Инструкции по развертыванию

#### 1. Клонирование репозитория

```bash
git clone https://github.com/slavacom/VkMidSprint.git
cd VkMidSprint
```

#### 2. Запуск Tarantool через Docker Compose

```bash
docker-compose up -d
```

Это запустит Tarantool на порту `3301` и инициализирует схему базы данных через `tarantool/init.lua`.

#### 3. Компиляция и сборка проекта

```bash
./gradlew clean build
```

#### 4. Запуск приложения

```bash
./gradlew bootRun
```

gRPC сервер будет слушать на порту `9091`.

#### 5. Проверка

Для быстрой проверки можно использовать `grpcurl`:

```bash
# Сохранить значение
grpcurl -plaintext \
  -d '{"key":"test_key","value":"dGVzdF92YWx1ZQ=="}' \
  localhost:9091 kv.KeyValueService/Put

# Получить значение
grpcurl -plaintext \
  -d '{"key":"test_key"}' \
  localhost:9091 kv.KeyValueService/Get

# Получить как строку
grpcurl -plaintext \
  -d '{"key":"test_key"}' \
  localhost:9091 kv.KeyValueService/GetString

# Получить диапазон (streaming)
grpcurl -plaintext \
  -d '{"key_since":"a","key_to":"z"}' \
  localhost:9091 kv.KeyValueService/Range
```

## Архитектура проекта

```
src/
├── main/
│   ├── java/com/slavacom/vkmidsprint/
│   │   ├── service/
│   │   │   ├── KeyValueService.java          # Tarantool операции
│   │   │   ├── KeyValueGrpcService.java      # gRPC обработчики
│   │   │   └── tarantool/
│   │   │       ├── LuaExpression.java        # Centralized Lua expressions
│   │   │       ├── ByteValueConverter.java   # Преобразование типов
│   │   │       └── TarantoolResponseParser.java  # Парсинг ответов
│   │   ├── config/
│   │   │   └── TarantoolConfig.java          # Конфигурация клиента
│   │   ├── property/
│   │   │   └── TarantoolProperty.java        # Свойства из application.yml
│   │   └── VkMidSprintApplication.java       # Entry point
│   ├── proto/
│   │   └── keyvalue.proto                    # gRPC контракт
│   └── resources/
│       └── application.yml                    # Конфигурация приложения
│
├── test/
│   └── java/com/slavacom/vkmidsprint/
│       └── VkMidSprintApplicationTests.java
│
└── tarantool/
    └── init.lua                              # Инициализация БД
```

## Ключевые особенности реализации

### 1. Tarantool интеграция

- Использование Lua-скриптов для сложных операций (range scan с фильтрацией и сортировкой)
- Асинхронные операции через `CompletableFuture`
- Поддержка `varbinary` для безопасного хранения произвольных данных

### 2. Type-safe protobuf API

- Разделение `Get` (возвращает `bytes`) и `GetString` (возвращает `string`)
- Server-streaming для `Range` операции с валидацией границ
- Явная обработка ошибок через gRPC `Status`

### 3. Response parsing

- Нормализация вложенности ответов от Tarantool eval()
- Безопасное извлечение field'ов из tuple'ов
- Поддержка разных форматов кодирования (Map<String, Number> → byte[])

### 4. Centralized configuration

- Все Lua-выражения в `LuaExpression.java` для простоты модификации
- Конвертеры типов в `ByteValueConverter.java`
- Парсеры ответов в `TarantoolResponseParser.java`

## Примеры использования

### Java клиент (gRPC stub)

```java
KeyValueServiceGrpc.KeyValueServiceBlockingStub stub = 
    KeyValueServiceGrpc.newBlockingStub(channel);

// PUT
PutResponse putResp = stub.put(PutRequest.newBuilder()
    .setKey("user:123")
    .setValue(ByteString.copyFromUtf8("John Doe"))
    .build());

// GET as String
GetStringResponse getResp = stub.getString(GetRequest.newBuilder()
    .setKey("user:123")
    .build());
System.out.println(getResp.getValue()); // "John Doe"

// Range streaming
Iterator<RangeResponse> range = stub.range(RangeRequest.newBuilder()
    .setKeySince("user:")
    .setKeyTo("user:~")
    .build());

while (range.hasNext()) {
    RangeResponse item = range.next();
    System.out.println(item.getKey() + " -> " + item.getValue());
}
```

## Конфигурация

### application.yml

```yaml
spring:
  application:
    name: VkMidSprint
  main:
    allow-bean-definition-overriding: true

grpc:
  server:
    port: 9091
    enable-reflection: true

tarantool:
  host: localhost
  port: 3301
  user: appuser
  password: apppass
```

Переменные окружения:
- `TARANTOOL_USER` — имя пользователя Tarantool
- `TARANTOOL_PASSWORD` — пароль Tarantool
- `TARANTOOL_HOST` — хост (по умолчанию `localhost`)
- `TARANTOOL_PORT` — порт (по умолчанию `3301`)

## Требования к стажировке

Это задание моделирует типичные сценарии разработки на стажировке

## Тестирование

### Запуск тестов

```bash
./gradlew test
```

### Построение интеграционных тестов

При необходимости использовать [TestContainers](https://testcontainers.com/) для Tarantool:

```java
@Testcontainers
class KeyValueServiceIntegrationTest {
    @Container
    static GenericContainer<?> tarantool = new GenericContainer<>("tarantool:latest")
        .withExposedPorts(3301);
    
    // тесты...
}
```

## Лицензия

Проект распространяется под лицензией **MIT** (см. [LICENSE](./LICENSE)).

Вы свободны использовать, модифицировать и распространять этот код при условии сохранения уведомления об авторском праве.

## Автор и контакты

**Автор:** Svyatoslav (Slavacom)  
**Email:** slavacom.dev@gmail.com  
**GitHub:** [@slavacom](https://github.com/slavacom)

### Внутренние материалы

- **Задание стажировки:** Доступно в личном кабинете on the VK Careers portal  
- **Срок выполнения:** До 07.04.2026 23:59 MSK

---

## Дополнительные ресурсы

- [Tarantool Documentation](https://www.tarantool.io/en/doc/)
- [gRPC Java Guide](https://grpc.io/docs/languages/java/)
- [Spring Boot Guides](https://spring.io/guides)
- [Protocol Buffers Documentation](https://developers.google.com/protocol-buffers)
- [tarantool-java-sdk](https://github.com/tarantool/tarantool-java-sdk)
---

**Последнее обновление:** 02.04.2026  
**Версия проекта:** 0.0.1-SNAPSHOT

