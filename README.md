# ERP School — Multi-Tenant School ERP SaaS

**Database + REST APIs only.** No frontend.

## What is included

Modular Spring Boot 3 monolith with Flyway schema and JWT-secured APIs:

- Foundation: tenants (schools), users, generated usernames + temporary passwords, refresh tokens, audit
- SaaS subscriptions with **manual payment-slip verification** (no online gateway)
- Campuses, classes, sections, subjects, teacher assignments, timetable
- Students and **one parent login with many children**
- Lecture-based student attendance (tied to timetable slots)
- Teacher attendance with optional **salary deduction**
- Homework (no online exams/quizzes)
- Offline exam sessions + published results (auto grade / pass-fail)
- Monthly fee challans, payment proofs, account-officer approve/reject
- Staff salaries (teachers, principal, account officers, school admin — not support staff)
- Announcements, in-app notifications, calendar/events
- School / platform dashboards and reports

**Not in this product:** online exams, transport/buses/drivers, guards, cleaners.

## Stack

| Layer | Choice |
| --- | --- |
| Language | Java 21 |
| Framework | Spring Boot 3.4 |
| Build | Maven |
| Database | PostgreSQL (Neon) |
| Migrations | Flyway `V1` + `V2` |
| Security | Spring Security + JWT + BCrypt |
| API docs | springdoc OpenAPI |
| Mapping / boilerplate | Lombok + MapStruct |
| Tests | JUnit 5 + Mockito |

## Multi-tenant rule (backend-enforced)

- `tenants` = schools
- Every school-owned row has `tenant_id`
- School users cannot pass another school’s `tenantId` — the JWT tenant wins
- Hibernate `tenantFilter` is enabled for school-scoped sessions
- Parents only see linked children; students only see their own record
- ERP Owner may send `X-Tenant-Id` (or `tenantId` query/body) to pin a school

## Database

Flyway on startup:

- `src/main/resources/db/migration/V1__phase1_foundation.sql`
- `src/main/resources/db/migration/V2__erp_modules.sql`

The app connects to **Neon PostgreSQL only**. There is no H2 / SQLite / PGlite fallback. Missing credentials or a non-Neon URL fail startup.

JDBC URL (no password in git):

```text
jdbc:postgresql://ep-lively-rain-ay8mipsd-pooler.c-5.us-east-2.aws.neon.tech/neondb?sslmode=require
```

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

Flyway applies V1+V2 against Neon. Logs must show:

`Neon connection and Flyway V1+V2 verification succeeded`

If SSL drops (`Connection terminated unexpectedly` / `ECONNRESET`): wake the Neon compute, check IP allowlist, try the non-pooler host. Do not add `channelBinding=require` until connectivity is proven.

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

Demo school **GVS** also seeds: Main campus, Grade 5-A, Mathematics, Monday 08:00 lecture, student↔parent link, fee structure, paid subscription.

Demo school users have `mustChangePassword=true`. Generated accounts return the temporary password **once**.

## API map (`/api/v1`)

Auth, tenants, users, audit — unchanged from Phase 1 (`/auth`, `/tenants`, `/users`, `/audit-logs`).

| Area | Base path |
| --- | --- |
| Subscriptions | `/subscriptions` |
| Campuses | `/campuses` |
| Classes / sections / subjects / timetable | `/classes`, `/subjects`, `/teacher-assignments`, `/timetable` |
| Students / parents | `/students`, `/parents/me/children` |
| Attendance | `/attendance` |
| Homework | `/homework` |
| Offline exams | `/exams` |
| Fees | `/fees` |
| Salaries | `/salaries` |
| Announcements | `/announcements` |
| Calendar | `/calendar` |
| Notifications | `/notifications` |
| Files | `/files` |
| School payment settings | `/school-settings` |
| Dashboards / reports | `/dashboard`, `/reports` |
| Role portals | `/portals/teacher`, `/principal`, `/account`, `/parent`, `/student` |

HTTP samples: `http/phase1-api.http`, `http/erp-modules.http`

## Module layout

```
com.erpschool
  auth, tenant, user, audit, security, common, config, bootstrap
  subscription   manual SaaS payment verification
  campus         school campuses
  academic       classes, sections, subjects, assignments, timetable
  student        students + parent links
  attendance     lecture student attendance + teacher attendance/deductions
  homework
  exam           offline results
  fee            structures, challans, payment proofs
  salary         staff profiles + monthly payroll
  announcement, notification, calendar, settings, file, report, portal
```

## Tests

```bash
mvn test
```

Covered without a live database: JWT, login, tenant isolation, grade/challan math, parent/student access rules.

Live API checks: start the app (Flyway migrates Neon) then use Swagger or the HTTP files.
