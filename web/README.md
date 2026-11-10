# Atrium School ERP — Web

Production web client for the Spring Boot `/api/v1` backend.

## Run (with the API)

```bash
# terminal 1 — JDK 21 + Maven
export SPRING_DATASOURCE_URL='jdbc:postgresql://localhost:5432/erp_school'
export SPRING_DATASOURCE_USERNAME=postgres
export SPRING_DATASOURCE_PASSWORD='…'
mvn spring-boot:run

# terminal 2
cd web
npm install
npm run dev
```

Vite proxies `/api` to `http://127.0.0.1:8080`. Override with `VITE_API_ORIGIN`.

Open the printed local URL, choose a role, and sign in with a real account.

## Demo accounts (after backend seed)

| Portal | Username | Password |
| --- | --- | --- |
| /admin | erp.owner | Owner@12345 |
| School Admin | GVS-ADM-0001 | ChangeMe@123 |
| Principal | GVS-PRN-0001 | ChangeMe@123 |
| Teacher | GVS-TCH-0001 | ChangeMe@123 |
| Account Officer | GVS-ACC-0001 | ChangeMe@123 |
| Parent | GVS-PAR-0001 | ChangeMe@123 |
| Student | GVS-STU-0001 | ChangeMe@123 |

School users must change the temporary password (`mustChangePassword`).
