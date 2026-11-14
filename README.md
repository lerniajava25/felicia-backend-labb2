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
   for the demo accounts (see Accounts). `SEED_ENABLED=true` turns on the demo data;
   seeding is off by default, so it only happens where `.env` says so. `.env` is
   git-ignored and is read by the app at startup (`spring.config.import` in
   `application.properties`).

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

These are local development values from `compose.yaml`, not real credentials. The port is
bound to `127.0.0.1` only, so the database can't be reached from other computers.

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

When the database is empty and `SEED_ENABLED=true`, the app creates these demo accounts at startup:

| Username | Role  |
|----------|-------|
| admin    | ADMIN |
| pippi    | AGENT |
| billy    | USER  |
| olle     | USER  |

Sign in at http://localhost:8080/login with a username from the table and the
`SEED_PASSWORD` from your own `.env` file. Usernames are not case-sensitive. Log out with
the "Log out" button in the nav bar.

The password itself is never stored in the repository, and the database only holds
bcrypt hashes of it.

## Roles

| Role  | May do                                                                                    |
|-------|-------------------------------------------------------------------------------------------|
| USER  | Create tickets, see their own tickets and their details, comment on their own tickets     |
| AGENT | See all tickets and their details, assign a ticket to themselves, change status, comment  |
| ADMIN | Everything an agent can do, plus create users, change roles and enable/disable users      |

URL access is enforced in `SecurityConfig` (anything not listed is denied), with the role
hierarchy ADMIN > AGENT > USER.
