# Quick Start Guide

Быстрый гайд для локального запуска VkMidSprint за 5 минут.

## ⚡ 5-Minute Setup

### Шаг 1: Клонирование (1 мин)

```bash
git clone https://github.com/slavacom/VkMidSprint.git
cd VkMidSprint
```

### Шаг 2: Запуск Tarantool (2 мин)

```bash
docker-compose up -d
```

Проверка:
```bash
docker-compose ps
# Should see: tarantool RUNNING
```

### Шаг 3: Сборка приложения (1 мин)

```bash
./gradlew clean build
```

Или для Windows (PowerShell):
```powershell
.\gradlew.bat clean build
```

### Шаг 4: Запуск приложения (1 мин)

```bash
./gradlew bootRun
```

Вы должны увидеть:
```
Started VkMidSprintApplication in X.XXX seconds
Listening gRPC server on port 9091
```

## ✅ Проверка работоспособности

Откройте новый терминал и проверьте операции:

### 1. Put (сохранить данные)

```bash
grpcurl -plaintext \
  -d '{"key":"hello","value":"d29ybGQ="}' \
  localhost:9091 kv.KeyValueService/Put
```

Ответ:
```json
{
  "success": true
}
```

### 2. Get (получить raw bytes)

```bash
grpcurl -plaintext \
  -d '{"key":"hello"}' \
  localhost:9091 kv.KeyValueService/Get
```

Ответ:
```json
{
  "value": "d29ybGQ=",
  "found": true
}
```

### 3. GetString (получить как строку)

```bash
grpcurl -plaintext \
  -d '{"key":"hello"}' \
  localhost:9091 kv.KeyValueService/GetString
```

Ответ:
```json
{
  "value": "world",
  "found": true
}
```

### 4. Count (подсчитать записи)

```bash
grpcurl -plaintext \
  -d '{}' \
  localhost:9091 kv.KeyValueService/Count
```

Ответ:
```json
{
  "count": 1
}
```

### 5. Range (streaming)

```bash
grpcurl -plaintext \
  -d '{"key_since":"a","key_to":"z"}' \
  localhost:9091 kv.KeyValueService/Range
```

Ответ:
```json
{
  "key": "hello",
  "value": "d29ybGQ="
}
```

### 6. Delete (удалить)

```bash
grpcurl -plaintext \
  -d '{"key":"hello"}' \
  localhost:9091 kv.KeyValueService/Delete
```

Ответ:
```json
{
  "success": true
}
```

## 🛠️ Команды для разработки

### Компиляция
```bash
./gradlew compileJava
```

### Запуск тестов
```bash
./gradlew test
```

### Зависимости
```bash
./gradlew dependencies
```

### Очистка build
```bash
./gradlew clean
```

### IDE integration
```bash
./gradlew idea         # IntelliJ IDEA
./gradlew eclipse      # Eclipse
```

## 📋 Шпаргалка

### Остановить приложение
```bash
Ctrl+C
```

### Остановить Tarantool
```bash
docker-compose down
```

### Посмотреть логи Tarantool
```bash
docker-compose logs tarantool -f
```

### Очистить БД и restart
```bash
docker-compose down -v
docker-compose up -d
```

### Подключиться к Tarantool напрямую
```bash
docker exec -it vkmidsprint-tarantool-1 tarantool
# Таран обеспечивается CLI интерфейсом
```

## 🔗 Полезные ссылки

- **README:** [README.md](./README.md) — полное описание проекта
- **Contributing:** [CONTRIBUTING.md](./CONTRIBUTING.md) — гайд разработки
- **Technical:** [TECHNICAL_NOTES.md](./TECHNICAL_NOTES.md) — архитектура
- **Internship:** [INTERNSHIP_INFO.md](./INTERNSHIP_INFO.md) — информация о стажировке

## 🐛 Troubleshooting

### Ошибка: Port already in use 9091

```bash
# Найти процесс
lsof -i :9091

# Убить процесс (если это старый запуск)
kill -9 <PID>
```

### Ошибка: docker-compose not found

```bash
# Установить Docker Desktop или:
pip install docker-compose
```

### Ошибка: gradlew permission denied

```bash
# На Linux/Mac:
chmod +x gradlew

# На Windows (PowerShell):
# Уже должен быть executable
```

### Ошибка: Connection refused localhost:3301

```bash
# Проверить статус
docker-compose ps

# Если не запущен:
docker-compose up -d

# Смотреть логи:
docker-compose logs tarantool
```

### Ошибка: grpcurl not installed

```bash
# Установить (macOS):
brew install grpcurl

# Установить (Ubuntu):
apt-get install grpcurl

# Установить (Windows, via Go):
go install github.com/fullstorydev/grpcurl/cmd/grpcurl@latest
```

## 📞 Нужна помощь?

1. Читай [README.md](./README.md)
2. Смотри [TECHNICAL_NOTES.md](./TECHNICAL_NOTES.md)
3. Создай Issue на GitHub
4. Напиши куратору

---

**Готово! Теперь вы можете начать разработку. 🎉**

