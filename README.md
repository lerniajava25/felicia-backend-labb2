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

| Setting  | Value                                       |
|----------|---------------------------------------------|
| Host     | `localhost`                                 |
| Port     | `5432`                                      |
| Database | `mydatabase`                                |
| User     | `myuser`                                    |
| Password | see `POSTGRES_PASSWORD` in `compose.yaml`   |

These are local development values, not real credentials. The port is bound to
`127.0.0.1` only, so the database can't be reached from other computers.

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
bcrypt hashes of it. Admins can create more accounts under "Manage users".

## Roles

| Role  | May do | Where |
|-------|--------|-------|
| USER  | Create tickets, see their own tickets and their details, comment on their own tickets. A reply to a ticket that waits for them moves it back to "In progress". | "My tickets" (`/tickets`), "New ticket" (`/tickets/new`), a ticket (`/tickets/{id}`) |
| AGENT | See all tickets and their details, assign a ticket to themselves or take it over from another agent, change status (closing a new ticket needs a reason), comment | "All tickets" (`/agent/tickets`), "Assigned to me" (`/agent/tickets/mine`), the agent tools on a ticket (`/tickets/{id}`) |
| ADMIN | Everything an agent can do, plus create users, change roles and enable/disable users. An admin can't change their own account, and the last enabled admin can't be demoted or disabled. | Everything an agent has, plus "Manage users" (`/admin/users`, `/admin/users/new`, `/admin/users/{id}/edit`) |

URL access is enforced in `SecurityConfig` (anything not listed is denied), with the role
hierarchy ADMIN > AGENT > USER.

## Tests

Run the tests (Testcontainers starts its own PostgreSQL container, so Docker must be
running). The tests do not need `.env`:

```bash
./mvnw test
```

## Design decisions

**Structure.** Packages by feature (`ticket`, `user`, `security`, `common`). Requests go
controller → service → repository; controllers only handle HTTP, and every rule about who
may do what lives in the services.

**Patterns.**
- *Repository* (Spring Data JPA) for all database access.
- *Form objects* (`NewTicketForm`, `EditUserForm` …) hold only the fields a user may send,
  so extra request parameters can't change status, owner or role (mass assignment).
- *View records* (`TicketRow`, `TicketDetails` …) are built in the service; templates
  never see entities (`open-in-view=false`).
- *Filter chain*: Spring Security's filters plus our own `CurrentUserRefreshFilter`.

**Security.**
- Deny by default: a URL without a rule in `SecurityConfig` is refused.
- Role hierarchy ADMIN > AGENT > USER, checked both on URLs and with `@PreAuthorize`.
- Another user's ticket gives **404, not 403**, so ticket ids can't be probed.
- Passwords hashed with bcrypt; one generic login error; failed logins are logged
  without the password; CSRF on for every form; `th:text` only.

**Concurrency.**
- A version field (optimistic locking) on assign, status change and user edits: an
  outdated page changes nothing and shows a message.
- A database lock on the last-admin check, so two admins can't demote each other at once.
- A unique constraint on usernames, which wins even if two admins create the same user.

**Sessions.** `CurrentUserRefreshFilter` reloads the logged-in user on every request, so a
role change or a disabled account takes effect immediately. Cost: one extra query per request.
