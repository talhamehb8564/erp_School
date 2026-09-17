# ERP School — Multi-Tenant School ERP SaaS

Phase 1 is **database + APIs only**. No frontend. Later phases (subscription, students, attendance, fees, …) will be added on this foundation.

## What Phase 1 includes

- Modular Spring Boot 3 monolith (Java 21, Maven, PostgreSQL, Flyway)
- **School = tenant**. Every school-owned row is isolated by `tenant_id` in SQL and in every service query
- JWT access tokens + hashed refresh tokens
- Login / refresh / logout / change password / me
- Role-based authorization (`ERP_OWNER`, `SCHOOL_ADMIN`, `PRINCIPAL`, `TEACHER`, `ACCOUNT_OFFICER`, `PARENT`, `STUDENT`)
- School (tenant) create / list / update / activate / suspend / disable
- Administration user management with generated username + temporary password
- Audit log foundation
- Bean Validation, global error JSON, Swagger UI
- Unit tests for auth, JWT, tenant isolation, password/token utilities

## Stack

| Layer | Choice |
| --- | --- |
| Language | Java 21 |
| Framework | Spring Boot 3.4 |
| Build | Maven |
| Database | PostgreSQL (Neon) |
| Migrations | Flyway |
| Security | Spring Security + JWT + BCrypt |
| API docs | springdoc OpenAPI |
| Mapping / boilerplate | Lombok + MapStruct (on the compiler classpath) |
| Tests | JUnit 5 + Mockito |

## Multi-tenant rule (enforced in the backend)

- `tenants` = schools
- ERP Owner users have `tenant_id = NULL` (platform scope)
- Every other user **must** belong to one tenant
- School users cannot pass another school’s `tenantId` — the JWT tenant wins
- Hibernate filter `tenantFilter` is enabled for school-scoped sessions
- Suspended / disabled / expired schools cannot log in (ERP Owner still can)

## Database

Flyway runs `src/main/resources/db/migration/V1__phase1_foundation.sql` on startup.

Tables:

- `tenants`
- `users`
- `username_sequences` (generates `GVS-TCH-0001`, `GVS-STU-0002`, …)
- `refresh_tokens` (opaque token, SHA-256 stored)
- `audit_logs`

The app connects to **Neon PostgreSQL only**. There is no H2 / SQLite / PGlite fallback. Missing credentials or a non-Neon URL fail startup.

JDBC URL:

```text
jdbc:postgresql://ep-lively-rain-ay8mipsd-pooler.c-5.us-east-2.aws.neon.tech/neondb?sslmode=require
```

Do **not** commit the database password. Copy `.env.example` and export:

```bash
export PGHOST=ep-lively-rain-ay8mipsd-pooler.c-5.us-east-2.aws.neon.tech
export PGDATABASE=neondb
export PGUSER=neondb_owner
export PGPASSWORD='your-neon-password'
export PGSSLMODE=require
export DB_URL="jdbc:postgresql://${PGHOST}/${PGDATABASE}?sslmode=require"
export DB_USERNAME="$PGUSER"
export DB_PASSWORD="$PGPASSWORD"
```

## Run

Requirements: **JDK 21** and **Maven 3.9+**.

```bash
mvn test
mvn spring-boot:run
```

Flyway runs `V1__phase1_foundation.sql` against Neon on startup. Logs must show `Neon connection and Flyway V1 verification succeeded`.

- API: http://localhost:8080/api/v1
- Swagger UI: http://localhost:8080/swagger-ui.html
- OpenAPI JSON: http://localhost:8080/v3/api-docs
- Health: http://localhost:8080/api/v1/health

Seed data is created on first boot (`app.seed.enabled=true`).

## Demo logins (after first successful boot)

| Role | Username | Password |
| --- | --- | --- |
| SaaS / ERP Owner | `erp.owner` | `Owner@12345` |
| School Admin | `GVS-ADM-0001` | `ChangeMe@123` |
| Principal | `GVS-PRN-0001` | `ChangeMe@123` |
| Teacher | `GVS-TCH-0001` | `ChangeMe@123` |
| Account Officer | `GVS-ACC-0001` | `ChangeMe@123` |
| Parent | `GVS-PAR-0001` | `ChangeMe@123` |
| Student | `GVS-STU-0001` | `ChangeMe@123` |

Demo school code: **GVS** (Green Valley School). Demo school users have `mustChangePassword=true`.

Generated accounts (create-user API) return the temporary password **once**.

## API map

### Auth — `/api/v1/auth`

| Method | Path | Access |
| --- | --- | --- |
| POST | `/login` | Public |
| POST | `/refresh` | Public (valid refresh token) |
| POST | `/logout` | Authenticated |
| POST | `/change-password` | Authenticated |
| GET | `/me` | Authenticated |

### Tenants / schools — `/api/v1/tenants`

| Method | Path | Access |
| --- | --- | --- |
| POST | `/` | ERP Owner |
| GET | `/` | ERP Owner |
| GET | `/me` | School users |
| GET | `/{id}` | ERP Owner, School Admin, Principal (own school) |
| PUT | `/{id}` | ERP Owner, School Admin (own school) |
| PATCH | `/{id}/status` | ERP Owner |

### Users — `/api/v1/users`

| Method | Path | Access |
| --- | --- | --- |
| POST | `/` | ERP Owner, School Admin |
| GET | `/` | ERP Owner, School Admin, Principal |
| GET | `/{id}` | same |
| PUT | `/{id}` | ERP Owner, School Admin |
| PATCH | `/{id}/activate` | ERP Owner, School Admin |
| PATCH | `/{id}/deactivate` | ERP Owner, School Admin |
| POST | `/{id}/reset-password` | ERP Owner, School Admin |

ERP Owner **must** pass `?tenantId=` (or `tenantId` in the create body) for school-scoped user APIs.

### Audit — `/api/v1/audit-logs`

School Admin sees only their tenant. ERP Owner may filter by `tenantId`.

## Quick curl

```bash
# Login
curl -s http://localhost:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"erp.owner","password":"Owner@12345"}'

# Authenticated call
curl -s http://localhost:8080/api/v1/tenants \
  -H "Authorization: Bearer ACCESS_TOKEN"
```

HTTP client file: `http/phase1-api.http`  
Smoke script (server must be running): `scripts/test-phase1-apis.sh`

## Module layout

```
com.erpschool
  auth        login, JWT, refresh tokens
  tenant      school tenant + isolation context
  user        accounts, roles, generated usernames
  audit       append-only activity log
  security    filters, JWT, UserDetails
  common      errors, API envelope, utilities
  config      security, CORS, OpenAPI, Hibernate tenant filter
  bootstrap   demo seed
```

Each module has entity / dto / repository / service / controller.

## Tests

```bash
mvn test
```

Covered without a live database:

- JWT claims (role + tenant)
- Login success / bad password / suspended school
- School admin cannot touch another tenant
- Cannot create `ERP_OWNER` via the user API
- Password / token hashing helpers

Live API checks: start the app (Flyway will migrate Neon) then run `scripts/test-phase1-apis.sh`.

## What is intentionally not in Phase 1

Frontend, subscription payment slips, campuses, student/parent profiles, timetable, attendance, homework, results, fees, salary, announcements, calendar, reports. Those are later phases on this schema and security model.
