# Helpdesk

## About

A helpdesk web application built with Spring Boot, Spring Data JPA, Spring Security and
Thymeleaf. Users report tickets, agents handle them and admins manage users.

## How to run

Requirements: Java 26 and Docker.

```bash
SEED_PASSWORD=yourpassword ./mvnw spring-boot:run
```

`SEED_PASSWORD` is required: it is the password for the demo accounts (see Accounts).

Spring Boot's Docker Compose support starts PostgreSQL from `compose.yaml`. The app is
available at http://localhost:8080.

Run the tests (Testcontainers starts its own PostgreSQL container):

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

All accounts use the password from the `SEED_PASSWORD` environment variable. It is never
stored in the project files.

## Roles

To be added.

