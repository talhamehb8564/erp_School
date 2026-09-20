import { useMemo, useState } from "react";
import { NavLink, Outlet, useNavigate, useParams } from "react-router-dom";
import { reportApi, subscriptionApi, tenantApi, userApi } from "../api/services";
import { fmtDate, money, pretty } from "../lib/format";
import { useSession } from "../lib/session";
import { useToast } from "../lib/toast";
import { applyTheme, readTheme, type Theme } from "../lib/theme";
import { Badge, Button, Empty, ErrorBox, Field, FileLink, Form, Loading, Modal, Search, Stat, Table, useAsync } from "../ui/kit";
import type { Tenant, TenantStatus } from "../lib/types";

export default function OwnerApp() {
  const { user, logout } = useSession();
  const [open, setOpen] = useState(false);
  const [theme, setTheme] = useState<Theme>(readTheme());
  const toggleTheme = () => {
    const next = theme === "light" ? "dark" : "light";
    applyTheme(next);
    setTheme(next);
  };
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
        <button className="nav-link" style={{ background: "none", border: 0, width: "100%", textAlign: "left" }} onClick={() => void logout()}>
          Sign out
        </button>
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
  const dash = useAsync(() => reportApi.platformDashboard());
  const schools = useAsync(() => tenantApi.list());
  const subs = useAsync(() => subscriptionApi.list());
  if (dash.loading) return <Loading />;
  if (dash.error) return <ErrorBox error={dash.error} />;
  const pending = (subs.data?.content || []).filter((s) => s.status === "PAYMENT_SUBMITTED");
  const nameOf = (tenantId: string) => schools.data?.content?.find((t) => t.id === tenantId)?.name || tenantId.slice(0, 8);
  return (
    <>
      <div className="page-title">
        <div>
          <h1>Platform</h1>
          <p>Live counts from PostgreSQL via /dashboard/platform</p>
        </div>
        <Button kind="ghost" onClick={() => void subscriptionApi.expireOverdue().then(() => subs.reload())}>
          Expire overdue
        </Button>
      </div>
      <div className="grid stats">
        <Stat label="Schools" value={dash.data?.schools} />
        <Stat label="Users" value={dash.data?.users} />
        <Stat label="Proofs to review" value={pending.length} />
        <Stat label="Listed tenants" value={schools.data?.totalElements} />
      </div>
      <div className="card" style={{ marginTop: 16 }}>
        <h3>Payment proofs awaiting verification</h3>
        {!pending.length ? (
          <Empty title="No submitted proofs" hint="When a school uploads a slip it appears here." />
        ) : (
          <Table
            headers={["Subscription", "School", "Amount", "Status"]}
            rows={pending.map((s) => [s.id.slice(0, 8), nameOf(s.tenantId), money(s.amount, s.currency), <Badge key={s.id} value={s.status} />])}
          />
        )}
      </div>
    </>
  );
}

export function Schools() {
  const [q, setQ] = useState("");
  const nav = useNavigate();
  const list = useAsync(() => tenantApi.list(q || undefined), [q]);
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
      <Search value={q} onChange={setQ} placeholder="Search school name or code" />
      <div style={{ height: 12 }} />
      {list.loading ? <Loading /> : list.error ? <ErrorBox error={list.error} /> : (
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
      )}
      <Modal title="Create school" open={open} onClose={() => setOpen(false)}>
        <Form
          onSubmit={async () => {
            try {
              const created = await tenantApi.create(form);
              toast("ok", `Created ${created.tenant.name}`);
              if (created.administrator?.temporaryPassword) {
                toast("info", `Admin password: ${created.administrator.temporaryPassword}`);
              }
              setOpen(false);
              void list.reload();
            } catch (e) {
              toast("err", e instanceof Error ? e.message : "Create failed");
            }
          }}
        >
          <Field label="Code"><input value={form.code} onChange={(e) => setForm({ ...form, code: e.target.value })} required /></Field>
          <Field label="Name"><input value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} required /></Field>
          <Field label="City"><input value={form.city} onChange={(e) => setForm({ ...form, city: e.target.value })} /></Field>
          <Field label="Admin first name"><input value={form.adminFirstName} onChange={(e) => setForm({ ...form, adminFirstName: e.target.value })} /></Field>
          <Field label="Admin last name"><input value={form.adminLastName} onChange={(e) => setForm({ ...form, adminLastName: e.target.value })} /></Field>
          <Field label="Admin email"><input value={form.adminEmail} onChange={(e) => setForm({ ...form, adminEmail: e.target.value })} /></Field>
          <Button type="submit" kind="brass">Create on Neon</Button>
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
    } catch {
      return null;
    }
  }, [id]);
  const users = useAsync(() => userApi.list({ tenantId: id }), [id]);
  if (school.loading) return <Loading />;
  if (school.error || !school.data) return <ErrorBox error={school.error || "Not found"} />;
  const t = school.data;
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
          <Button kind="danger" onClick={() => void setStatus("SUSPENDED")}>Turn subscription OFF</Button>
          <Button kind="ok" onClick={() => void setStatus("ACTIVE")}>Turn ON</Button>
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
        </div>
      </div>
      <div className="card" style={{ marginTop: 16 }}>
        <h3>Users in this school</h3>
        {users.loading ? <Loading /> : (
          <Table
            headers={["Name", "Username", "Role", "Status"]}
            rows={(users.data?.content || []).map((u) => [u.fullName, u.username, pretty(u.role), <Badge key={u.id} value={u.status} />])}
          />
        )}
      </div>
    </>
  );
}

function CreateSub({ tenant, onDone }: { tenant: Tenant; onDone: () => void }) {
  const toast = useToast();
  const [amount, setAmount] = useState("25000");
  return (
    <Form
      onSubmit={async () => {
        await subscriptionApi.create({ tenantId: tenant.id, amount: Number(amount), currency: "PKR" });
        toast("ok", "Subscription created");
        onDone();
      }}
    >
      <Field label="Amount PKR"><input value={amount} onChange={(e) => setAmount(e.target.value)} /></Field>
      <Button type="submit" kind="brass">Create subscription</Button>
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
  if (list.loading) return <Loading />;
  if (list.error) return <ErrorBox error={list.error} />;
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
    </>
  );
}
