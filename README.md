# Helpdesk

## About

A helpdesk web application built with Spring Boot, Spring Data JPA, Spring Security and
Thymeleaf. Users report tickets, agents handle them and admins manage users.

## How to run

Requirements: Java 26 and Docker.

1. Create your local settings file from the template:

   ```bash
   cp .env.example .env
   ```

   Open `.env` and set `SEED_PASSWORD` to a password of your choice. It is the password
   for the demo accounts (see Accounts). `.env` is git-ignored and is read by the app at
   startup (`spring.config.import` in `application.properties`).

2. Start the app:

   ```bash
   ./mvnw spring-boot:run
   ```

   Spring Boot's Docker Compose support starts PostgreSQL from `compose.yaml`. The app is
   available at http://localhost:8080.

The seed password is only used when the database is empty. To start over with a fresh
database (for example after changing `SEED_PASSWORD`), stop the app and run:

```bash
docker compose down
```

### Database

The development database runs in Docker and can be opened in any database tool:

| Setting  | Value        |
|----------|--------------|
| Host     | `localhost`  |
| Port     | `5432`       |
| Database | `mydatabase` |
| User     | `myuser`     |
| Password | `secret`     |

These are local development values from `compose.yaml`, not real credentials.

### Tests

Run the tests (Testcontainers starts its own PostgreSQL container, so Docker must be
running). The tests do not need `.env`:

```bash
./mvnw test
```

### Git hooks

A pre-push hook in `.githooks/pre-push` runs the tests before every push, to any branch.
If Docker is not running or a test fails, the push is cancelled. Turn it on once after
cloning:

```bash
git config core.hooksPath .githooks
```

## Accounts

When the database is empty, the app creates these demo accounts at startup:

| Username | Role  |
|----------|-------|
| admin    | ADMIN |
| pippi    | AGENT |
| billy    | USER  |
| olle     | USER  |

All accounts use the `SEED_PASSWORD` from your `.env` file. The password itself is never
stored in the repository, and the database only holds bcrypt hashes of it.

## Roles

To be added.
