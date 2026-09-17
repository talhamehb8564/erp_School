import fs from "node:fs";
import path from "node:path";
import crypto from "node:crypto";
import { fileURLToPath } from "node:url";
import express from "express";
import cors from "cors";
import jwt from "jsonwebtoken";
import bcrypt from "bcryptjs";
import pg from "pg";
import { PGlite } from "@electric-sql/pglite";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const ROOT = path.resolve(__dirname, "../..");
const MIGRATION = path.join(ROOT, "src/main/resources/db/migration/V1__phase1_foundation.sql");

const JWT_SECRET = process.env.JWT_SECRET || "erp-school-phase1-change-this-jwt-secret-key-32b";
const ACCESS_TTL = Number(process.env.JWT_ACCESS_TTL || 900);
const REFRESH_TTL = Number(process.env.JWT_REFRESH_TTL || 604800);
const PORT = Number(process.env.PORT || 8080);
const HOST = process.env.HOST || "0.0.0.0";

const NEON = {
  host: "ep-lively-rain-ay8mipsd-pooler.c-5.us-east-2.aws.neon.tech",
  port: 5432,
  user: process.env.DB_USERNAME || "neondb_owner",
  password: process.env.DB_PASSWORD || "npg_i7yZ9RzFTLsq",
  database: "neondb",
};

const ROLE_CODE = {
  SCHOOL_ADMIN: "ADM",
  PRINCIPAL: "PRN",
  TEACHER: "TCH",
  ACCOUNT_OFFICER: "ACC",
  PARENT: "PAR",
  STUDENT: "STU",
};

const USER_MANAGERS = new Set(["ERP_OWNER", "SCHOOL_ADMIN"]);
const USER_READERS = new Set(["ERP_OWNER", "SCHOOL_ADMIN", "PRINCIPAL"]);
const SCHOOL_ROLES = new Set(Object.keys(ROLE_CODE));

let dbKind = "unknown";
let query;
let execSql;
let withTransaction;

function ok(res, data, message, status = 200) {
  return res.status(status).json({
    success: true,
    message: message || undefined,
    data,
    timestamp: new Date().toISOString(),
  });
}

function fail(res, status, errorCode, message, errors) {
  return res.status(status).json({
    success: false,
    errorCode,
    message,
    errors,
    timestamp: new Date().toISOString(),
  });
}

function sha256(value) {
  return crypto.createHash("sha256").update(value).digest("hex");
}

function randomToken() {
  return crypto.randomBytes(32).toString("hex");
}

function tempPassword() {
  const upper = "ABCDEFGHJKLMNPQRSTUVWXYZ";
  const lower = "abcdefghijkmnopqrstuvwxyz";
  const digits = "23456789";
  const symbols = "@#$%";
  const all = upper + lower + digits + symbols;
  const pick = (s) => s[crypto.randomInt(s.length)];
  const chars = [pick(upper), pick(lower), pick(digits), pick(symbols)];
  for (let i = 0; i < 8; i++) chars.push(pick(all));
  for (let i = chars.length - 1; i > 0; i--) {
    const j = crypto.randomInt(i + 1);
    [chars[i], chars[j]] = [chars[j], chars[i]];
  }
  return chars.join("");
}

function userDto(row) {
  if (!row) return null;
  return {
    id: row.id,
    tenantId: row.tenant_id,
    username: row.username,
    email: row.email,
    firstName: row.first_name,
    lastName: row.last_name,
    fullName: `${row.first_name} ${row.last_name}`,
    phone: row.phone,
    role: row.role,
    status: row.status,
    mustChangePassword: row.must_change_password,
    lastLoginAt: row.last_login_at,
    createdAt: row.created_at,
  };
}

function tenantDto(row) {
  if (!row) return null;
  return {
    id: row.id,
    code: row.code,
    name: row.name,
    legalName: row.legal_name,
    email: row.email,
    phone: row.phone,
    addressLine: row.address_line,
    city: row.city,
    state: row.state,
    country: row.country,
    postalCode: row.postal_code,
    logoUrl: row.logo_url,
    website: row.website,
    status: row.status,
    academicYear: row.academic_year,
    academicSession: row.academic_session,
    timezone: row.timezone,
    notes: row.notes,
    createdAt: row.created_at,
    updatedAt: row.updated_at,
  };
}

function pageOf(rows, total, page, size) {
  const totalPages = size === 0 ? 0 : Math.ceil(total / size);
  return {
    content: rows,
    page,
    size,
    totalElements: total,
    totalPages,
    first: page === 0,
    last: totalPages === 0 || page >= totalPages - 1,
  };
}

async function tryNeon() {
  const client = new pg.Client({
    host: NEON.host,
    port: NEON.port,
    user: NEON.user,
    password: NEON.password,
    database: NEON.database,
    ssl: { rejectUnauthorized: false },
    connectionTimeoutMillis: 5000,
  });
  await client.connect();
  query = async (sql, params = []) => {
    const result = await client.query(sql, params);
    return result.rows;
  };
  execSql = async (sql) => {
    await client.query(sql);
  };
  withTransaction = async (fn) => {
    await client.query("BEGIN");
    try {
      const out = await fn();
      await client.query("COMMIT");
      return out;
    } catch (e) {
      await client.query("ROLLBACK");
      throw e;
    }
  };
  dbKind = "neon";
  console.log("Connected to Neon PostgreSQL");
}

async function usePglite() {
  const db = new PGlite();
  query = async (sql, params = []) => {
    const result = await db.query(sql, params);
    return result.rows;
  };
  execSql = async (sql) => {
    await db.exec(sql);
  };
  withTransaction = async (fn) => {
    await db.exec("BEGIN");
    try {
      const out = await fn();
      await db.exec("COMMIT");
      return out;
    } catch (e) {
      await db.exec("ROLLBACK");
      throw e;
    }
  };
  dbKind = "pglite";
  console.log("Neon unreachable from this environment — using in-memory PGlite (same SQL schema)");
}

async function flywayMigrate() {
  const tables = await query(
    "select table_name from information_schema.tables where table_schema = 'public' and table_name = 'tenants'"
  );
  if (tables.length > 0) {
    console.log("Flyway: schema already present");
    return;
  }
  let sql = fs.readFileSync(MIGRATION, "utf8");
  if (dbKind === "pglite") {
    sql = sql.replace(/CREATE EXTENSION IF NOT EXISTS pgcrypto;/g, "");
  }
  await execSql(sql);
  await query(`
    create table if not exists flyway_schema_history (
      installed_rank integer primary key,
      version varchar(50),
      description varchar(200),
      type varchar(20),
      script varchar(1000),
      checksum integer,
      installed_by varchar(100),
      installed_on timestamptz default now(),
      execution_time integer,
      success boolean
    )
  `);
  await query(
    `insert into flyway_schema_history
      (installed_rank, version, description, type, script, installed_by, execution_time, success)
     values (1, '1', 'phase1 foundation', 'SQL', 'V1__phase1_foundation.sql', $1, 1, true)`,
    [dbKind]
  );
  console.log("Flyway: applied V1__phase1_foundation.sql");
}

async function seed() {
  const existing = await query("select 1 from users where username = $1", ["erp.owner"]);
  if (existing.length === 0) {
    const hash = bcrypt.hashSync("Owner@12345", 10);
    await query(
      `insert into users (username, email, password_hash, first_name, last_name, role, status, must_change_password)
       values ('erp.owner', 'owner@erpschool.local', $1, 'ERP', 'Owner', 'ERP_OWNER', 'ACTIVE', false)`,
      [hash]
    );
    console.log("Seeded erp.owner");
  }

  let tenants = await query("select * from tenants where code = $1", ["GVS"]);
  if (tenants.length === 0) {
    tenants = await query(
      `insert into tenants (code, name, email, phone, address_line, city, country, academic_year, academic_session, status, timezone)
       values ('GVS', 'Green Valley School', 'admin@greenvalley.school', '+92-300-0000000',
               'Demo Campus, Lahore', 'Lahore', 'Pakistan', '2026-2027', '2026-2027', 'ACTIVE', 'Asia/Karachi')
       returning *`
    );
  }
  const tenant = tenants[0];
  const demo = [
    ["SCHOOL_ADMIN", "School", "Admin", "admin@greenvalley.school"],
    ["PRINCIPAL", "Ayesha", "Malik", "principal@greenvalley.school"],
    ["TEACHER", "Ali", "Raza", "teacher@greenvalley.school"],
    ["ACCOUNT_OFFICER", "Nadia", "Khan", "accounts@greenvalley.school"],
    ["PARENT", "Ahmed", "Khan", "parent@greenvalley.school"],
    ["STUDENT", "Hassan", "Khan", "student@greenvalley.school"],
  ];
  const passwordHash = bcrypt.hashSync("ChangeMe@123", 10);
  for (const [role, first, last, email] of demo) {
    const found = await query("select 1 from users where email = $1", [email]);
    if (found.length) continue;
    const username = await nextUsername(tenant.id, tenant.code, role);
    await query(
      `insert into users (tenant_id, username, email, password_hash, first_name, last_name, role, status, must_change_password)
       values ($1,$2,$3,$4,$5,$6,$7,'ACTIVE', true)`,
      [tenant.id, username, email, passwordHash, first, last, role]
    );
    console.log(`Seeded ${username}`);
  }
}

async function nextUsername(tenantId, code, role) {
  const seq = await query(
    "select next_value from username_sequences where tenant_id = $1 and role = $2",
    [tenantId, role]
  );
  let n;
  if (seq.length === 0) {
    await query(
      "insert into username_sequences (tenant_id, role, next_value) values ($1,$2,2)",
      [tenantId, role]
    );
    n = 1;
  } else {
    n = seq[0].next_value;
    await query(
      "update username_sequences set next_value = next_value + 1, updated_at = now() where tenant_id = $1 and role = $2",
      [tenantId, role]
    );
  }
  return `${String(code).toUpperCase()}-${ROLE_CODE[role]}-${String(n).padStart(4, "0")}`;
}

async function audit(actor, action, entityType, entityId, details, req) {
  await query(
    `insert into audit_logs (tenant_id, user_id, username, role, action, entity_type, entity_id, details, ip_address, user_agent)
     values ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10)`,
    [
      actor?.tenantId || null,
      actor?.id || null,
      actor?.username || null,
      actor?.role || null,
      action,
      entityType || null,
      entityId || null,
      details ? JSON.stringify(details) : null,
      req?.ip || null,
      (req?.get?.("user-agent") || "").slice(0, 300) || null,
    ]
  );
}

function signAccess(user) {
  const payload = {
    sub: user.id,
    username: user.username,
    role: user.role,
    typ: "access",
  };
  if (user.tenant_id) payload.tenantId = user.tenant_id;
  return jwt.sign(payload, JWT_SECRET, { expiresIn: ACCESS_TTL });
}

async function issueTokens(user, req) {
  const accessToken = signAccess(user);
  const refreshToken = randomToken();
  const expiresAt = new Date(Date.now() + REFRESH_TTL * 1000).toISOString();
  await query(
    `insert into refresh_tokens (user_id, tenant_id, token_hash, expires_at, user_agent, ip_address)
     values ($1,$2,$3,$4,$5,$6)`,
    [user.id, user.tenant_id, sha256(refreshToken), expiresAt, (req.get("user-agent") || "").slice(0, 300), req.ip]
  );
  return {
    accessToken,
    refreshToken,
    tokenType: "Bearer",
    expiresIn: ACCESS_TTL,
    user: userDto(user),
  };
}

function authMiddleware(req, res, next) {
  const header = req.get("authorization") || "";
  if (!header.startsWith("Bearer ")) return next();
  try {
    const claims = jwt.verify(header.slice(7), JWT_SECRET);
    if (claims.typ !== "access") return next();
    req.actor = {
      id: claims.sub,
      username: claims.username,
      role: claims.role,
      tenantId: claims.tenantId || null,
    };
  } catch {
    req.actor = null;
  }
  next();
}

function requireAuth(req, res, next) {
  if (!req.actor) return fail(res, 401, "UNAUTHORIZED", "Authentication required");
  next();
}

function requireRoles(...roles) {
  return (req, res, next) => {
    if (!req.actor) return fail(res, 401, "UNAUTHORIZED", "Authentication required");
    if (!roles.includes(req.actor.role)) {
      return fail(res, 403, "FORBIDDEN", "You do not have permission to perform this action");
    }
    next();
  };
}

function requireTenantId(actor, requested) {
  if (actor.role === "ERP_OWNER") {
    if (!requested) {
      const err = new Error("tenantId is required for this operation");
      err.status = 400;
      err.code = "TENANT_REQUIRED";
      throw err;
    }
    return requested;
  }
  if (!actor.tenantId) {
    const err = new Error("Tenant context is missing");
    err.status = 403;
    err.code = "FORBIDDEN";
    throw err;
  }
  if (requested && requested !== actor.tenantId) {
    const err = new Error("Cannot access another school's data");
    err.status = 403;
    err.code = "FORBIDDEN";
    throw err;
  }
  return actor.tenantId;
}

async function loadUser(id) {
  const rows = await query("select * from users where id = $1", [id]);
  return rows[0] || null;
}

async function assertTenantLogin(user) {
  if (user.role === "ERP_OWNER") return;
  const tenants = await query("select * from tenants where id = $1", [user.tenant_id]);
  if (!tenants.length) {
    const err = new Error("School account is not available");
    err.status = 401;
    err.code = "UNAUTHORIZED";
    throw err;
  }
  const status = tenants[0].status;
  if (status !== "ACTIVE" && status !== "PENDING") {
    const err = new Error(`School access is ${status.toLowerCase()}`);
    err.status = 401;
    err.code = "UNAUTHORIZED";
    throw err;
  }
}

const app = express();
app.set("trust proxy", true);
app.use(cors());
app.use(express.json({ limit: "1mb" }));
app.use(authMiddleware);

app.get("/api/v1/health", (req, res) => {
  ok(res, {
    status: "UP",
    phase: "1",
    service: "erp-school",
    database: dbKind,
    time: new Date().toISOString(),
  });
});

app.get("/actuator/health", (req, res) => {
  res.json({ status: "UP" });
});

app.post("/api/v1/auth/login", async (req, res, next) => {
  try {
    const username = (req.body?.username || "").trim();
    const password = req.body?.password || "";
    if (!username || !password) return fail(res, 400, "VALIDATION_ERROR", "Username and password are required");
    const users = await query("select * from users where lower(username) = lower($1)", [username]);
    const user = users[0];
    if (!user) {
      await audit({ username }, "LOGIN_FAILED", "User", null, { reason: "unknown_user" }, req);
      return fail(res, 401, "UNAUTHORIZED", "Invalid username or password");
    }
    if (user.status === "LOCKED") {
      return fail(res, 401, "UNAUTHORIZED", "Account is locked. Try again later or contact administration.");
    }
    if (user.status !== "ACTIVE") return fail(res, 401, "UNAUTHORIZED", "Account is inactive");
    if (!bcrypt.compareSync(password, user.password_hash)) {
      const attempts = Number(user.failed_login_attempts || 0) + 1;
      if (attempts >= 8) {
        await query(
          "update users set failed_login_attempts = $1, status = 'LOCKED', locked_until = now() + interval '15 minutes' where id = $2",
          [attempts, user.id]
        );
      } else {
        await query("update users set failed_login_attempts = $1 where id = $2", [attempts, user.id]);
      }
      await audit({ id: user.id, username: user.username, role: user.role, tenantId: user.tenant_id },
        "LOGIN_FAILED", "User", user.id, { reason: "bad_password" }, req);
      return fail(res, 401, "UNAUTHORIZED", "Invalid username or password");
    }
    await assertTenantLogin(user);
    await query(
      "update users set failed_login_attempts = 0, locked_until = null, last_login_at = now() where id = $1",
      [user.id]
    );
    const fresh = (await query("select * from users where id = $1", [user.id]))[0];
    await audit({ id: fresh.id, username: fresh.username, role: fresh.role, tenantId: fresh.tenant_id },
      "LOGIN_SUCCESS", "User", fresh.id, null, req);
    return ok(res, await issueTokens(fresh, req), "Login successful");
  } catch (e) {
    next(e);
  }
});

app.post("/api/v1/auth/refresh", async (req, res, next) => {
  try {
    const refreshToken = req.body?.refreshToken;
    if (!refreshToken) return fail(res, 400, "VALIDATION_ERROR", "Refresh token is required");
    const rows = await query("select * from refresh_tokens where token_hash = $1", [sha256(refreshToken)]);
    const stored = rows[0];
    if (!stored || stored.revoked || new Date(stored.expires_at) < new Date()) {
      return fail(res, 401, "UNAUTHORIZED", "Invalid refresh token");
    }
    const user = await loadUser(stored.user_id);
    if (!user || user.status !== "ACTIVE") return fail(res, 401, "UNAUTHORIZED", "Account is not active");
    await assertTenantLogin(user);
    await query("update refresh_tokens set revoked = true, revoked_at = now() where id = $1", [stored.id]);
    return ok(res, await issueTokens(user, req));
  } catch (e) {
    next(e);
  }
});

app.post("/api/v1/auth/logout", requireAuth, async (req, res, next) => {
  try {
    const refreshToken = req.body?.refreshToken;
    if (!refreshToken) return fail(res, 400, "VALIDATION_ERROR", "Refresh token is required");
    await query(
      "update refresh_tokens set revoked = true, revoked_at = now() where token_hash = $1",
      [sha256(refreshToken)]
    );
    await audit(req.actor, "LOGOUT", "User", null, null, req);
    return ok(res, null, "Logged out");
  } catch (e) {
    next(e);
  }
});

app.post("/api/v1/auth/change-password", requireAuth, async (req, res, next) => {
  try {
    const currentPassword = req.body?.currentPassword;
    const newPassword = req.body?.newPassword;
    if (!currentPassword || !newPassword) return fail(res, 400, "VALIDATION_ERROR", "Current and new password are required");
    if (newPassword.length < 8) return fail(res, 400, "VALIDATION_ERROR", "Password must be between 8 and 72 characters");
    const user = await loadUser(req.actor.id);
    if (!user) return fail(res, 401, "UNAUTHORIZED", "User not found");
    if (!bcrypt.compareSync(currentPassword, user.password_hash)) {
      return fail(res, 401, "UNAUTHORIZED", "Current password is incorrect");
    }
    if (bcrypt.compareSync(newPassword, user.password_hash)) {
      return fail(res, 400, "NEW_PASSWORD_SAME", "New password must be different from the current password");
    }
    await query(
      "update users set password_hash = $1, must_change_password = false, password_changed_at = now() where id = $2",
      [bcrypt.hashSync(newPassword, 10), user.id]
    );
    await query("update refresh_tokens set revoked = true, revoked_at = now() where user_id = $1 and revoked = false", [user.id]);
    await audit(req.actor, "PASSWORD_CHANGED", "User", user.id, null, req);
    return ok(res, null, "Password changed. Please login again.");
  } catch (e) {
    next(e);
  }
});

app.get("/api/v1/auth/me", requireAuth, async (req, res, next) => {
  try {
    const user = await loadUser(req.actor.id);
    if (!user) return fail(res, 401, "UNAUTHORIZED", "User not found");
    return ok(res, userDto(user));
  } catch (e) {
    next(e);
  }
});

app.post("/api/v1/tenants", requireRoles("ERP_OWNER"), async (req, res, next) => {
  try {
    const b = req.body || {};
    const code = String(b.code || "").trim().toUpperCase();
    if (!/^[A-Z0-9]{2,20}$/.test(code)) return fail(res, 400, "VALIDATION_ERROR", "School code must be 2-20 alphanumeric characters");
    if (!b.name) return fail(res, 400, "VALIDATION_ERROR", "name is required");
    const dup = await query("select 1 from tenants where code = $1", [code]);
    if (dup.length) return fail(res, 409, "DUPLICATE_RESOURCE", `School code already exists: ${code}`);
    const rows = await query(
      `insert into tenants (code, name, legal_name, email, phone, address_line, city, state, country, postal_code, website, academic_year, academic_session, timezone, status, created_by)
       values ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11,$12,$13,$14,'ACTIVE',$15) returning *`,
      [
        code, b.name.trim(), b.legalName || null, b.email || null, b.phone || null, b.addressLine || null,
        b.city || null, b.state || null, b.country || "Pakistan", b.postalCode || null, b.website || null,
        b.academicYear || null, b.academicSession || null, b.timezone || "Asia/Karachi", req.actor.id,
      ]
    );
    const tenant = rows[0];
    let administrator = null;
    if (b.adminFirstName && b.adminLastName) {
      const username = await nextUsername(tenant.id, tenant.code, "SCHOOL_ADMIN");
      const temporaryPassword = tempPassword();
      const created = await query(
        `insert into users (tenant_id, username, email, password_hash, first_name, last_name, phone, role, status, must_change_password, created_by)
         values ($1,$2,$3,$4,$5,$6,$7,'SCHOOL_ADMIN','ACTIVE', true, $8) returning *`,
        [tenant.id, username, b.adminEmail || null, bcrypt.hashSync(temporaryPassword, 10),
          b.adminFirstName.trim(), b.adminLastName.trim(), b.adminPhone || null, req.actor.id]
      );
      administrator = {
        user: userDto(created[0]),
        temporaryPassword,
        message: "Account created",
      };
    }
    await audit(req.actor, "TENANT_CREATED", "Tenant", tenant.id, { code: tenant.code, name: tenant.name }, req);
    return ok(res, { tenant: tenantDto(tenant), administrator }, "School created", 201);
  } catch (e) {
    next(e);
  }
});

app.get("/api/v1/tenants", requireRoles("ERP_OWNER"), async (req, res, next) => {
  try {
    const page = Number(req.query.page || 0);
    const size = Math.min(Number(req.query.size || 20), 100);
    const q = req.query.q ? `%${req.query.q}%` : null;
    const status = req.query.status || null;
    const rows = await query(
      `select * from tenants
       where ($1::text is null or status = $1)
         and ($2::text is null or name ilike $2 or code ilike $2)
       order by created_at desc
       offset $3 limit $4`,
      [status, q, page * size, size]
    );
    const total = await query(
      `select count(*)::int as n from tenants
       where ($1::text is null or status = $1)
         and ($2::text is null or name ilike $2 or code ilike $2)`,
      [status, q]
    );
    return ok(res, pageOf(rows.map(tenantDto), total[0].n, page, size));
  } catch (e) {
    next(e);
  }
});

app.get("/api/v1/tenants/me", requireAuth, async (req, res, next) => {
  try {
    if (req.actor.role === "ERP_OWNER" || !req.actor.tenantId) {
      return fail(res, 403, "FORBIDDEN", "ERP owner is not bound to a school. Use GET /api/v1/tenants/{id}");
    }
    const rows = await query("select * from tenants where id = $1", [req.actor.tenantId]);
    if (!rows.length) return fail(res, 404, "RESOURCE_NOT_FOUND", "Tenant not found");
    return ok(res, tenantDto(rows[0]));
  } catch (e) {
    next(e);
  }
});

app.get("/api/v1/tenants/:id", requireRoles("ERP_OWNER", "SCHOOL_ADMIN", "PRINCIPAL"), async (req, res, next) => {
  try {
    const rows = await query("select * from tenants where id = $1", [req.params.id]);
    if (!rows.length) return fail(res, 404, "RESOURCE_NOT_FOUND", `Tenant not found: ${req.params.id}`);
    if (req.actor.role !== "ERP_OWNER" && req.actor.tenantId !== rows[0].id) {
      return fail(res, 403, "FORBIDDEN", "Cannot access another school's data");
    }
    return ok(res, tenantDto(rows[0]));
  } catch (e) {
    next(e);
  }
});

app.put("/api/v1/tenants/:id", requireRoles("ERP_OWNER", "SCHOOL_ADMIN"), async (req, res, next) => {
  try {
    const rows = await query("select * from tenants where id = $1", [req.params.id]);
    if (!rows.length) return fail(res, 404, "RESOURCE_NOT_FOUND", `Tenant not found: ${req.params.id}`);
    if (req.actor.role !== "ERP_OWNER" && req.actor.tenantId !== rows[0].id) {
      return fail(res, 403, "FORBIDDEN", "Cannot access another school's data");
    }
    const t = rows[0];
    const b = req.body || {};
    const updated = await query(
      `update tenants set
         name = coalesce($1, name),
         legal_name = coalesce($2, legal_name),
         email = coalesce($3, email),
         phone = coalesce($4, phone),
         address_line = coalesce($5, address_line),
         city = coalesce($6, city),
         state = coalesce($7, state),
         country = coalesce($8, country),
         postal_code = coalesce($9, postal_code),
         logo_url = coalesce($10, logo_url),
         website = coalesce($11, website),
         academic_year = coalesce($12, academic_year),
         academic_session = coalesce($13, academic_session),
         timezone = coalesce($14, timezone),
         notes = coalesce($15, notes),
         updated_by = $16
       where id = $17 returning *`,
      [
        b.name ?? null, b.legalName ?? null, b.email ?? null, b.phone ?? null, b.addressLine ?? null,
        b.city ?? null, b.state ?? null, b.country ?? null, b.postalCode ?? null, b.logoUrl ?? null,
        b.website ?? null, b.academicYear ?? null, b.academicSession ?? null, b.timezone ?? null,
        req.actor.role === "ERP_OWNER" ? (b.notes ?? null) : null,
        req.actor.id, t.id,
      ]
    );
    await audit(req.actor, "TENANT_UPDATED", "Tenant", t.id, { code: t.code }, req);
    return ok(res, tenantDto(updated[0]), "School updated");
  } catch (e) {
    next(e);
  }
});

app.patch("/api/v1/tenants/:id/status", requireRoles("ERP_OWNER"), async (req, res, next) => {
  try {
    const status = req.body?.status;
    const allowed = ["PENDING", "ACTIVE", "SUSPENDED", "DISABLED", "EXPIRED"];
    if (!allowed.includes(status)) return fail(res, 400, "VALIDATION_ERROR", "Invalid status");
    const rows = await query("select * from tenants where id = $1", [req.params.id]);
    if (!rows.length) return fail(res, 404, "RESOURCE_NOT_FOUND", `Tenant not found: ${req.params.id}`);
    const previous = rows[0].status;
    const updated = await query(
      "update tenants set status = $1, updated_by = $2 where id = $3 returning *",
      [status, req.actor.id, req.params.id]
    );
    await audit(req.actor, "TENANT_STATUS_CHANGED", "Tenant", req.params.id,
      { from: previous, to: status, reason: req.body?.reason || "" }, req);
    return ok(res, tenantDto(updated[0]), "School status updated");
  } catch (e) {
    next(e);
  }
});

app.post("/api/v1/users", requireRoles("ERP_OWNER", "SCHOOL_ADMIN"), async (req, res, next) => {
  try {
    const b = req.body || {};
    if (b.role === "ERP_OWNER") return fail(res, 403, "FORBIDDEN", "Cannot create another ERP owner through this API");
    if (!SCHOOL_ROLES.has(b.role)) return fail(res, 400, "VALIDATION_ERROR", "Invalid role");
    if (!b.firstName || !b.lastName) return fail(res, 400, "VALIDATION_ERROR", "firstName and lastName are required");
    const tenantId = requireTenantId(req.actor, b.tenantId);
    const tenants = await query("select * from tenants where id = $1", [tenantId]);
    if (!tenants.length) return fail(res, 404, "RESOURCE_NOT_FOUND", `Tenant not found: ${tenantId}`);
    if (b.email) {
      const dup = await query("select 1 from users where lower(email) = lower($1)", [b.email]);
      if (dup.length) return fail(res, 409, "DUPLICATE_RESOURCE", "Email is already in use");
    }
    const username = await nextUsername(tenantId, tenants[0].code, b.role);
    const temporaryPassword = tempPassword();
    const created = await query(
      `insert into users (tenant_id, username, email, password_hash, first_name, last_name, phone, role, status, must_change_password, created_by)
       values ($1,$2,$3,$4,$5,$6,$7,$8,'ACTIVE', true, $9) returning *`,
      [tenantId, username, b.email || null, bcrypt.hashSync(temporaryPassword, 10),
        b.firstName.trim(), b.lastName.trim(), b.phone || null, b.role, req.actor.id]
    );
    await audit(req.actor, "USER_CREATED", "User", created[0].id, { username, role: b.role }, req);
    return ok(res, {
      user: userDto(created[0]),
      temporaryPassword,
      message: "Account created. Share the temporary password securely. The user must change it after login.",
    }, "User created", 201);
  } catch (e) {
    next(e);
  }
});

app.get("/api/v1/users", requireRoles("ERP_OWNER", "SCHOOL_ADMIN", "PRINCIPAL"), async (req, res, next) => {
  try {
    const tenantId = requireTenantId(req.actor, req.query.tenantId);
    const page = Number(req.query.page || 0);
    const size = Math.min(Number(req.query.size || 20), 100);
    const role = req.query.role || null;
    const status = req.query.status || null;
    const q = req.query.q ? `%${req.query.q}%` : null;
    const rows = await query(
      `select * from users
       where tenant_id = $1
         and ($2::text is null or role = $2)
         and ($3::text is null or status = $3)
         and ($4::text is null or username ilike $4 or first_name ilike $4 or last_name ilike $4 or coalesce(email,'') ilike $4)
       order by created_at desc
       offset $5 limit $6`,
      [tenantId, role, status, q, page * size, size]
    );
    const total = await query(
      `select count(*)::int as n from users
       where tenant_id = $1
         and ($2::text is null or role = $2)
         and ($3::text is null or status = $3)
         and ($4::text is null or username ilike $4 or first_name ilike $4 or last_name ilike $4 or coalesce(email,'') ilike $4)`,
      [tenantId, role, status, q]
    );
    return ok(res, pageOf(rows.map(userDto), total[0].n, page, size));
  } catch (e) {
    next(e);
  }
});

app.get("/api/v1/users/:id", requireRoles("ERP_OWNER", "SCHOOL_ADMIN", "PRINCIPAL"), async (req, res, next) => {
  try {
    const user = await loadUser(req.params.id);
    if (!user) return fail(res, 404, "RESOURCE_NOT_FOUND", `User not found: ${req.params.id}`);
    if (user.role !== "ERP_OWNER") {
      const tenantId = requireTenantId(req.actor, req.query.tenantId);
      if (user.tenant_id !== tenantId) return fail(res, 404, "RESOURCE_NOT_FOUND", `User not found: ${req.params.id}`);
    } else if (req.actor.role !== "ERP_OWNER") {
      return fail(res, 404, "RESOURCE_NOT_FOUND", `User not found: ${req.params.id}`);
    }
    return ok(res, userDto(user));
  } catch (e) {
    next(e);
  }
});

app.put("/api/v1/users/:id", requireRoles("ERP_OWNER", "SCHOOL_ADMIN"), async (req, res, next) => {
  try {
    const user = await loadUser(req.params.id);
    if (!user || user.role === "ERP_OWNER") return fail(res, 404, "RESOURCE_NOT_FOUND", `User not found: ${req.params.id}`);
    const tenantId = requireTenantId(req.actor, req.query.tenantId);
    if (user.tenant_id !== tenantId) return fail(res, 404, "RESOURCE_NOT_FOUND", `User not found: ${req.params.id}`);
    const b = req.body || {};
    if (b.email) {
      const dup = await query("select id from users where lower(email) = lower($1) and id <> $2", [b.email, user.id]);
      if (dup.length) return fail(res, 409, "DUPLICATE_RESOURCE", "Email is already in use");
    }
    const updated = await query(
      `update users set
         first_name = coalesce($1, first_name),
         last_name = coalesce($2, last_name),
         email = coalesce($3, email),
         phone = coalesce($4, phone),
         updated_by = $5
       where id = $6 returning *`,
      [b.firstName ?? null, b.lastName ?? null, b.email ?? null, b.phone ?? null, req.actor.id, user.id]
    );
    await audit(req.actor, "USER_UPDATED", "User", user.id, { username: user.username }, req);
    return ok(res, userDto(updated[0]), "User updated");
  } catch (e) {
    next(e);
  }
});

app.patch("/api/v1/users/:id/activate", requireRoles("ERP_OWNER", "SCHOOL_ADMIN"), async (req, res, next) => {
  try {
    const user = await loadUser(req.params.id);
    if (!user || user.role === "ERP_OWNER") return fail(res, 404, "RESOURCE_NOT_FOUND", `User not found: ${req.params.id}`);
    const tenantId = requireTenantId(req.actor, req.query.tenantId);
    if (user.tenant_id !== tenantId) return fail(res, 404, "RESOURCE_NOT_FOUND", `User not found: ${req.params.id}`);
    const updated = await query(
      "update users set status = 'ACTIVE', locked_until = null, failed_login_attempts = 0, updated_by = $1 where id = $2 returning *",
      [req.actor.id, user.id]
    );
    await audit(req.actor, "USER_ACTIVATED", "User", user.id, { username: user.username }, req);
    return ok(res, userDto(updated[0]), "User activated");
  } catch (e) {
    next(e);
  }
});

app.patch("/api/v1/users/:id/deactivate", requireRoles("ERP_OWNER", "SCHOOL_ADMIN"), async (req, res, next) => {
  try {
    const user = await loadUser(req.params.id);
    if (!user || user.role === "ERP_OWNER") return fail(res, 404, "RESOURCE_NOT_FOUND", `User not found: ${req.params.id}`);
    const tenantId = requireTenantId(req.actor, req.query.tenantId);
    if (user.tenant_id !== tenantId) return fail(res, 404, "RESOURCE_NOT_FOUND", `User not found: ${req.params.id}`);
    if (user.id === req.actor.id) return fail(res, 400, "BUSINESS_RULE", "Cannot deactivate your own account");
    const updated = await query(
      "update users set status = 'INACTIVE', updated_by = $1 where id = $2 returning *",
      [req.actor.id, user.id]
    );
    await audit(req.actor, "USER_DEACTIVATED", "User", user.id, { username: user.username }, req);
    return ok(res, userDto(updated[0]), "User deactivated");
  } catch (e) {
    next(e);
  }
});

app.post("/api/v1/users/:id/reset-password", requireRoles("ERP_OWNER", "SCHOOL_ADMIN"), async (req, res, next) => {
  try {
    const user = await loadUser(req.params.id);
    if (!user) return fail(res, 404, "RESOURCE_NOT_FOUND", `User not found: ${req.params.id}`);
    if (user.role === "ERP_OWNER" && req.actor.role !== "ERP_OWNER") {
      return fail(res, 403, "FORBIDDEN", "Cannot reset ERP owner password");
    }
    if (user.role !== "ERP_OWNER") {
      const tenantId = requireTenantId(req.actor, req.query.tenantId);
      if (user.tenant_id !== tenantId) return fail(res, 404, "RESOURCE_NOT_FOUND", `User not found: ${req.params.id}`);
    }
    const temporaryPassword = tempPassword();
    const updated = await query(
      `update users set password_hash = $1, must_change_password = true, password_changed_at = now(),
              failed_login_attempts = 0, locked_until = null, updated_by = $2
       where id = $3 returning *`,
      [bcrypt.hashSync(temporaryPassword, 10), req.actor.id, user.id]
    );
    await audit(req.actor, "PASSWORD_RESET", "User", user.id, { username: user.username }, req);
    return ok(res, {
      user: userDto(updated[0]),
      temporaryPassword,
      message: "Password reset. Share the temporary password securely.",
    }, "Password reset");
  } catch (e) {
    next(e);
  }
});

app.get("/api/v1/audit-logs", requireRoles("ERP_OWNER", "SCHOOL_ADMIN"), async (req, res, next) => {
  try {
    const tenantId = req.actor.role === "ERP_OWNER" ? (req.query.tenantId || null) : requireTenantId(req.actor, req.query.tenantId);
    const page = Number(req.query.page || 0);
    const size = Math.min(Number(req.query.size || 20), 100);
    const action = req.query.action || null;
    const entityType = req.query.entityType || null;
    const rows = await query(
      `select * from audit_logs
       where ($1::uuid is null or tenant_id = $1)
         and ($2::text is null or action = $2)
         and ($3::text is null or entity_type = $3)
       order by created_at desc
       offset $4 limit $5`,
      [tenantId, action, entityType, page * size, size]
    );
    const total = await query(
      `select count(*)::int as n from audit_logs
       where ($1::uuid is null or tenant_id = $1)
         and ($2::text is null or action = $2)
         and ($3::text is null or entity_type = $3)`,
      [tenantId, action, entityType]
    );
    const mapped = rows.map((log) => ({
      id: log.id,
      tenantId: log.tenant_id,
      userId: log.user_id,
      username: log.username,
      role: log.role,
      action: log.action,
      entityType: log.entity_type,
      entityId: log.entity_id,
      details: log.details,
      ipAddress: log.ip_address,
      createdAt: log.created_at,
    }));
    return ok(res, pageOf(mapped, total[0].n, page, size));
  } catch (e) {
    next(e);
  }
});

app.use((err, req, res, next) => {
  if (err.status) return fail(res, err.status, err.code || "BUSINESS_RULE", err.message);
  console.error(err);
  return fail(res, 500, "INTERNAL_ERROR", "An unexpected error occurred");
});

async function main() {
  try {
    await tryNeon();
  } catch (e) {
    console.warn("Neon connection failed:", e.message);
    await usePglite();
  }
  await flywayMigrate();
  await seed();
  const tables = await query(
    "select table_name from information_schema.tables where table_schema='public' order by 1"
  );
  console.log("Tables:", tables.map((t) => t.table_name).join(", "));
  app.listen(PORT, HOST, () => {
    console.log(`ERP School Phase 1 API listening on http://${HOST}:${PORT} (${dbKind})`);
  });
}

main().catch((e) => {
  console.error(e);
  process.exit(1);
});
