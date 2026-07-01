# Task Manager API

Многопользовательский REST-сервис для управления задачами в проектах: JWT-аутентификация, проекты с ролями участников, доски, колонки и задачи. Есть минимальный веб-клиент и Swagger UI.

## Стек

- Java 17, Spring Boot 3
- Spring Security + JWT
- Spring Data JPA, Liquibase
- H2 (dev/test), PostgreSQL (prod)
- Thymeleaf + Bootstrap (веб-клиент)
- springdoc-openapi (Swagger)

## Быстрый старт

### Требования

- JDK 17+
- Maven 3.9+

### Запуск в dev-режиме (H2)

```bash
mvn spring-boot:run
```

Приложение: [http://localhost:8080](http://localhost:8080)

- Веб-клиент: `/login`, `/register`, `/projects`
- Swagger UI: [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)
- H2 Console: [http://localhost:8080/h2-console](http://localhost:8080/h2-console) (JDBC URL: `jdbc:h2:mem:taskmanager`)



### Запуск с PostgreSQL (prod)

```bash
set SPRING_PROFILES_ACTIVE=prod
set DB_URL=jdbc:postgresql://localhost:5432/taskmanager
set DB_USER=postgres
set DB_PASSWORD=postgres
set JWT_SECRET=your-256-bit-secret-key-for-production-use
mvn spring-boot:run
```



### Тесты

```bash
mvn test
```



## Основные API-группы


| Группа   | Эндпоинты                                                                |
| -------- | ------------------------------------------------------------------------ |
| Auth     | `POST /api/auth/register`, `POST /api/auth/login`                        |
| Profile  | `GET/PUT /api/users/me`                                                  |
| Projects | `GET/POST /api/projects`, `GET/PUT/DELETE /api/projects/{id}`            |
| Members  | `GET/POST /api/projects/{id}/members`, `PUT/DELETE .../members/{userId}` |
| Boards   | CRUD под `/api/projects/{projectId}/boards`, `GET /api/boards/{boardId}` |
| Columns  | CRUD под `/api/boards/{boardId}/columns`                                 |
| Tasks    | CRUD, `PATCH /api/tasks/{id}/move`, фильтрация через query-параметры     |




## Роли в проекте

- **OWNER** — полный доступ, управление участниками
- **EDITOR** — создание/изменение досок, колонок и задач
- **VIEWER** — только чтение



## Демо-сценарий

1. Зарегистрируйтесь на `/register`
2. Создайте проект на `/projects`
3. Добавьте доску и колонки («Бэклог», «В работе», «Готово»)
4. Создайте задачи, назначьте исполнителя, переместите задачу через выпадающий список
5. Проверьте фильтрацию по приоритету и названию

ш

## Структура проекта

```
src/main/java/com/taskmanager/
├── config/          # OpenAPI
├── domain/          # JPA-сущности
├── repository/      # Spring Data
├── security/        # JWT, Security
├── service/         # Бизнес-логика
└── web/
    ├── api/         # REST-контроллеры
    ├── ui/          # Thymeleaf-страницы
    └── exception/   # Обработка ошибок
```



## Формат ошибок

```json
{
  "status": 403,
  "message": "Access denied",
  "timestamp": "2026-07-01T12:00:00Z"
}
```

