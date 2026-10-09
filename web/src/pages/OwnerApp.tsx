import { useMemo, useState } from "react";
import { NavLink, Outlet, useLocation, useNavigate, useParams } from "react-router-dom";
import ChangePassword from "./ChangePassword";
import { reportApi, subscriptionApi, tenantApi, userApi } from "../api/services";
import { fmtDate, money, pretty } from "../lib/format";
import { useSession } from "../lib/session";
import { useToast } from "../lib/toast";
import { applyTheme, readTheme, type Theme } from "../lib/theme";
import { ApiError } from "../api/client";
import { Badge, Bars, Button, Donut, Empty, Field, FileLink, Form, Modal, Pager, QueryState, Search, Stat, Table, useAsync, useDebounced } from "../ui/kit";
import type { Tenant, TenantStatus } from "../lib/types";

export default function OwnerApp() {
  const { user, logout } = useSession();
  const loc = useLocation();
  const [open, setOpen] = useState(false);
  const [theme, setTheme] = useState<Theme>(readTheme());
  const toggleTheme = () => {
    const next = theme === "light" ? "dark" : "light";
    applyTheme(next);
    setTheme(next);
  };
  if (user?.mustChangePassword && loc.pathname !== "/admin/app/password") {
    return <ChangePassword />;
  }
  return (
    <div className="shell">
      {open ? <button type="button" className="scrim" aria-label="Close menu" onClick={() => setOpen(false)} /> : null}
      <aside className={`sidebar ${open ? "open" : ""}`}>
        <div className="brand-mark" style={{ marginBottom: 18 }}>
          <div className="mark">A</div>
          <div>
            Atrium Owner
            <div style={{ fontSize: 12, opacity: 0.65 }}>Platform console</div>
          </div>
        </div>
        <NavLink className="nav-link" to="/admin/app" end onClick={() => setOpen(false)}>
          Dashboard
        </NavLink>
        <NavLink className="nav-link" to="/admin/app/schools" onClick={() => setOpen(false)}>
          Schools
        </NavLink>
        <NavLink className="nav-link" to="/admin/app/subscriptions" onClick={() => setOpen(false)}>
          Subscriptions
        </NavLink>
        <div style={{ flex: 1 }} />
        <Button kind="ghost" loadingText="Signing out…" onClick={() => logout()}>
          Sign out
        </Button>
      </aside>
      <div className="main">
        <header className="topbar">
          <button className="icon-btn burger" onClick={() => setOpen((v) => !v)}>
            ☰
          </button>
          <div>
            <div className="crumbs">ERP Owner</div>
            <strong>{user?.fullName}</strong>
          </div>
          <div className="row">
            <button className="icon-btn" onClick={toggleTheme} title="Theme">
              {theme === "dark" ? "☀" : "☾"}
            </button>
          </div>
        </header>
        <div className="content">
          <Outlet />
        </div>
      </div>
    </div>
  );
}

export function OwnerHome() {
  const toast = useToast();
  const dash = useAsync(() => reportApi.platformDashboard());
  const schools = useAsync(() => tenantApi.list());
  const subs = useAsync(() => subscriptionApi.list());
  const pending = (subs.data?.content || []).filter((s) => s.status === "PAYMENT_SUBMITTED");
  const nameOf = (tenantId: string) => schools.data?.content?.find((t) => t.id === tenantId)?.name || tenantId.slice(0, 8);
  return (
    <>
      <div className="page-title">
        <div>
          <p className="kicker">SaaS console</p>
          <h1>Platform</h1>
          <p>Live counts from PostgreSQL via /dashboard/platform</p>
        </div>
        <Button kind="ghost" loadingText="Expiring…" onClick={async () => {
          await subscriptionApi.expireOverdue();
          toast("ok", "Overdue subscriptions expired");
          await subs.reload();
        }}>
          Expire overdue
        </Button>
      </div>
      <QueryState status={dash} label="platform dashboard">
      <div className="hero-strip card">
        <div>
          <p className="kicker">Network health</p>
          <h2 style={{ margin: "4px 0 6px" }}>Schools on this platform</h2>
          <p>Verify payment proofs, then turn a school subscription ON or OFF.</p>
        </div>
        <Donut value={Number(dash.data?.schools || 0)} max={Math.max(Number(dash.data?.schools || 1), 1)} label="Schools" />
      </div>
      <div className="grid stats">
        <Stat label="Schools" value={dash.data?.schools} loading={dash.loading} />
        <Stat label="Users" value={dash.data?.users} loading={dash.loading} />
        <Stat label="Proofs to review" value={pending.length} loading={subs.loading} />
        <Stat label="Listed tenants" value={schools.data?.totalElements} loading={schools.loading} />
      </div>
      <div className="card chart-card" style={{ marginTop: 16 }}>
        <h3>Network</h3>
        <Bars items={[
          { label: "Schools", value: Number(dash.data?.schools || 0) },
          { label: "Users", value: Number(dash.data?.users || 0) },
          { label: "Proofs", value: pending.length },
        ]} />
      </div>
      </QueryState>
      <div className="card" style={{ marginTop: 16 }}>
        <h3>Payment proofs awaiting verification</h3>
        <QueryState status={subs} label="payment proofs">
        {!pending.length ? (
          <Empty title="No submitted proofs" hint="When a school uploads a slip it appears here." />
        ) : (
          <Table
            headers={["Subscription", "School", "Amount", "Status"]}
            rows={pending.map((s) => [s.id.slice(0, 8), nameOf(s.tenantId), money(s.amount, s.currency), <Badge key={s.id} value={s.status} />])}
          />
        )}
        </QueryState>
      </div>
    </>
  );
}

export function Schools() {
  const [q, setQ] = useState("");
  const [page, setPage] = useState(0);
  const dq = useDebounced(q);
  const nav = useNavigate();
  const list = useAsync(() => tenantApi.list(dq || undefined, undefined, page), [dq, page]);
  const [open, setOpen] = useState(false);
  const toast = useToast();
  const [form, setForm] = useState({ code: "", name: "", city: "Lahore", adminFirstName: "", adminLastName: "", adminEmail: "" });
  return (
    <>
      <div className="page-title">
        <div>
          <h1>Schools</h1>
          <p>Create tenants and control subscription access</p>
        </div>
        <Button kind="brass" onClick={() => setOpen(true)}>
          New school
        </Button>
      </div>
      <Search value={q} onChange={(v) => { setQ(v); setPage(0); }} placeholder="Search school name or code" />
      <div style={{ height: 12 }} />
      <QueryState status={list} label="schools">
        <Table
          headers={["School", "Code", "City", "Status", ""]}
          rows={(list.data?.content || []).map((t) => [
            t.name,
            t.code,
            t.city || "—",
            <Badge key={t.id} value={t.status} />,
            <Button key={`o${t.id}`} kind="ghost" onClick={() => nav(`/admin/app/schools/${t.id}`)}>
              Open
            </Button>,
          ])}
        />
        <Pager page={list.data?.page ?? page} totalPages={list.data?.totalPages ?? 0} onChange={setPage} />
      </QueryState>
      <Modal title="Create school" open={open} onClose={() => setOpen(false)}>
        <Form
          busyLabel="Creating…"
          onSubmit={async () => {
            const created = await tenantApi.create(form);
            toast("ok", `Created ${created.tenant.name}`);
            if (created.administrator?.temporaryPassword) {
              toast("info", `Admin password: ${created.administrator.temporaryPassword}`);
            }
            setOpen(false);
            void list.reload();
          }}
        >
          <Field label="Code"><input value={form.code} onChange={(e) => setForm({ ...form, code: e.target.value })} required /></Field>
          <Field label="Name"><input value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} required /></Field>
          <Field label="City"><input value={form.city} onChange={(e) => setForm({ ...form, city: e.target.value })} /></Field>
          <Field label="Admin first name"><input value={form.adminFirstName} onChange={(e) => setForm({ ...form, adminFirstName: e.target.value })} /></Field>
          <Field label="Admin last name"><input value={form.adminLastName} onChange={(e) => setForm({ ...form, adminLastName: e.target.value })} /></Field>
          <Field label="Admin email"><input value={form.adminEmail} onChange={(e) => setForm({ ...form, adminEmail: e.target.value })} /></Field>
          <Button type="submit" kind="brass" loadingText="Creating…">Create on Neon</Button>
        </Form>
      </Modal>
    </>
  );
}

export function SchoolDetail() {
  const { id } = useParams();
  const toast = useToast();
  const school = useAsync(() => tenantApi.get(id!), [id]);
  const sub = useAsync(async () => {
    try {
      return await subscriptionApi.current(id);
    } catch (e) {
      if (e instanceof ApiError && e.status === 404) return null;
      throw e;
    }
  }, [id]);
  const users = useAsync(() => userApi.list({ tenantId: id }), [id]);
  const t = school.data;
  if (!t) {
    return (
      <QueryState status={school} label="school">
        <Empty title="School not found" />
      </QueryState>
    );
  }
  const s = sub.data;
  const proof = s?.payments?.[0];

  const setStatus = async (status: TenantStatus) => {
    try {
      await tenantApi.changeStatus(t.id, status, status === "SUSPENDED" ? "Owner turned subscription off" : "Owner restored access");
      toast("ok", `School is now ${pretty(status)}`);
      void school.reload();
    } catch (e) {
      toast("err", e instanceof Error ? e.message : "Status update failed");
    }
  };

  return (
    <>
      <div className="page-title">
        <div>
          <div className="crumbs">Schools / {t.code}</div>
          <h1>{t.name}</h1>
          <p>{t.city || t.country} · {t.email || "no email"}</p>
        </div>
        <div className="row">
          <Button kind="danger" loadingText="Updating…" onClick={() => setStatus("SUSPENDED")}>Turn subscription OFF</Button>
          <Button kind="ok" loadingText="Updating…" onClick={() => setStatus("ACTIVE")}>Turn ON</Button>
        </div>
      </div>
      <div className="grid two">
        <div className="card">
          <h3>School status</h3>
          <p><Badge value={t.status} /> · Academic {t.academicYear || "—"}</p>
          <p>When OFF, school modules return 403. The school admin can still log in to upload payment proof.</p>
        </div>
        <div className="card">
          <h3>Subscription</h3>
          <QueryState status={sub} label="subscription">
          {!s ? (
            <CreateSub tenant={t} onDone={() => void sub.reload()} />
          ) : (
            <>
              <p><Badge value={s.status} /> · {money(s.amount, s.currency)}</p>
              <p>Period {fmtDate(s.periodStart)} → {fmtDate(s.periodEnd)}</p>
              {proof?.slipUrl ? (
                <p>
                  Latest slip: <FileLink href={proof.slipUrl} label="Open slip" />
                  <br />
                  Ref {proof.transactionRef || "—"}
                </p>
              ) : <Empty title="No payment proof yet" />}
              {s.status === "PAYMENT_SUBMITTED" ? (
                <ReviewBox id={s.id} onDone={() => { void sub.reload(); void school.reload(); }} />
              ) : null}
            </>
          )}
          </QueryState>
        </div>
      </div>
      <div className="card" style={{ marginTop: 16 }}>
        <h3>Users in this school</h3>
        <QueryState status={users} label="school users">
          <Table
            headers={["Name", "Username", "Role", "Status"]}
            rows={(users.data?.content || []).map((u) => [u.fullName, u.username, pretty(u.role), <Badge key={u.id} value={u.status} />])}
          />
        </QueryState>
      </div>
    </>
  );
}

function CreateSub({ tenant, onDone }: { tenant: Tenant; onDone: () => void }) {
  const toast = useToast();
  const [amount, setAmount] = useState("25000");
  return (
    <Form
      busyLabel="Creating…"
      onSubmit={async () => {
        await subscriptionApi.create({ tenantId: tenant.id, amount: Number(amount), currency: "PKR" });
        toast("ok", "Subscription created");
        onDone();
      }}
    >
      <Field label="Amount PKR"><input value={amount} onChange={(e) => setAmount(e.target.value)} /></Field>
      <Button type="submit" kind="brass" loadingText="Creating…">Create subscription</Button>
    </Form>
  );
}

function ReviewBox({ id, onDone }: { id: string; onDone: () => void }) {
  const toast = useToast();
  const [start, setStart] = useState(new Date().toISOString().slice(0, 10));
  const [end, setEnd] = useState(`${new Date().getFullYear()}-12-31`);
  const [reason, setReason] = useState("");
  return (
    <div className="row" style={{ marginTop: 12, alignItems: "flex-end" }}>
      <Field label="Period start"><input type="date" value={start} onChange={(e) => setStart(e.target.value)} /></Field>
      <Field label="Period end"><input type="date" value={end} onChange={(e) => setEnd(e.target.value)} /></Field>
      <Button
        kind="ok"
        loadingText="Verifying…"
        onClick={async () => {
          try {
            await subscriptionApi.review(id, { approve: true, periodStart: start, periodEnd: end });
            toast("ok", "Payment verified. School is ACTIVE.");
            onDone();
          } catch (e) {
            toast("err", e instanceof Error ? e.message : "Approve failed");
          }
        }}
      >
        Verify / Approve
      </Button>
      <Button
        kind="danger"
        loadingText="Rejecting…"
        onClick={async () => {
          try {
            await subscriptionApi.review(id, { approve: false, rejectionReason: reason || "Rejected by owner" });
            toast("info", "Proof rejected. School remains locked.");
            onDone();
          } catch (e) {
            toast("err", e instanceof Error ? e.message : "Reject failed");
          }
        }}
      >
        Reject
      </Button>
      <Field label="Reject reason"><input value={reason} onChange={(e) => setReason(e.target.value)} /></Field>
    </div>
  );
}

export function Subscriptions() {
  const list = useAsync(() => subscriptionApi.list());
  const schools = useAsync(() => tenantApi.list());
  const nav = useNavigate();
  const [filter, setFilter] = useState("");
  const rows = useMemo(
    () => (list.data?.content || []).filter((s) => !filter || s.status === filter),
    [list.data, filter],
  );
  const nameOf = (tenantId: string) => schools.data?.content?.find((t) => t.id === tenantId)?.name || tenantId.slice(0, 8);
  return (
    <>
      <div className="page-title">
        <div>
          <h1>Subscriptions</h1>
          <p>Every school billing record on Neon</p>
        </div>
      </div>
      <Field label="Filter status">
        <select value={filter} onChange={(e) => setFilter(e.target.value)}>
          <option value="">All</option>
          {["PENDING", "PAYMENT_SUBMITTED", "PAID", "REJECTED", "EXPIRED", "SUSPENDED"].map((s) => (
            <option key={s}>{s}</option>
          ))}
        </select>
      </Field>
      <QueryState status={list} label="subscriptions">
      <Table
        headers={["School", "Status", "Amount", "Period", ""]}
        rows={rows.map((s) => [
          nameOf(s.tenantId),
          <Badge key={s.id} value={s.status} />,
          money(s.amount, s.currency),
          `${fmtDate(s.periodStart)} → ${fmtDate(s.periodEnd)}`,
          <Button key={`g${s.id}`} kind="ghost" onClick={() => nav(`/admin/app/schools/${s.tenantId}`)}>School</Button>,
        ])}
      />
      </QueryState>
    </>
  );
}
