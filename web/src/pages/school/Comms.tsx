import { useState } from "react";
import { announcementApi, calendarApi, notificationApi, reportApi } from "../../api/services";
import { useLookups } from "../../lib/lookups";
import { fmtDate, monthStart, pretty } from "../../lib/format";
import { useSession } from "../../lib/session";
import { useToast } from "../../lib/toast";
import { Badge, Bars, Button, Empty, ErrorBox, Field, Form, Loading, Table, useAsync } from "../../ui/kit";

export function AnnouncementsPage() {
  const { user } = useSession();
  const { classes } = useLookups();
  const list = useAsync(() => announcementApi.list());
  const toast = useToast();
  const staff = user?.role === "SCHOOL_ADMIN" || user?.role === "PRINCIPAL";
  const [form, setForm] = useState({ title: "", body: "", audience: "ALL", classId: "" });
  return (
    <>
      <div className="page-title"><div><h1>Announcements</h1><p>Visible according to audience and class</p></div></div>
      {staff ? (
        <div className="card" style={{ marginBottom: 16 }}>
          <Form onSubmit={async () => { await announcementApi.create(form); toast("ok", "Published"); void list.reload(); }}>
            <Field label="Title"><input value={form.title} onChange={(e) => setForm({ ...form, title: e.target.value })} required /></Field>
            <Field label="Body"><textarea value={form.body} onChange={(e) => setForm({ ...form, body: e.target.value })} required /></Field>
            <div className="row">
              <Field label="Audience">
                <select value={form.audience} onChange={(e) => setForm({ ...form, audience: e.target.value })}>
                  {["ALL", "TEACHERS", "PARENTS", "STUDENTS", "CLASS"].map((a) => <option key={a}>{a}</option>)}
                </select>
              </Field>
              {form.audience === "CLASS" ? (
                <Field label="Class">
                  <select value={form.classId} onChange={(e) => setForm({ ...form, classId: e.target.value })}>
                    <option value="">Select</option>
                    {classes.map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}
                  </select>
                </Field>
              ) : null}
            </div>
            <Button type="submit" kind="brass">Publish</Button>
          </Form>
        </div>
      ) : null}
      {list.loading ? <Loading /> : list.error ? <ErrorBox error={list.error} /> : !(list.data || []).length ? <Empty title="No announcements" /> : (
        <div className="grid">
          {(list.data || []).map((a) => (
            <article className="card" key={a.id}>
              <div className="kicker">{pretty(a.audience)} · {fmtDate(a.publishDate)}</div>
              <h3>{a.title}</h3>
              <p>{a.body}</p>
            </article>
          ))}
        </div>
      )}
    </>
  );
}

export function CalendarPage() {
  const { user } = useSession();
  const list = useAsync(() => calendarApi.list());
  const toast = useToast();
  const staff = user?.role === "SCHOOL_ADMIN" || user?.role === "PRINCIPAL";
  const [form, setForm] = useState({ title: "", eventType: "EVENT", startDate: new Date().toISOString().slice(0, 10), audience: "ALL" });
  return (
    <>
      <div className="page-title"><div><h1>Calendar</h1><p>Holidays, PTM and school events</p></div></div>
      {staff ? (
        <Form onSubmit={async () => { await calendarApi.create(form); toast("ok", "Event saved"); void list.reload(); }}>
          <div className="row">
            <input className="search" placeholder="Title" value={form.title} onChange={(e) => setForm({ ...form, title: e.target.value })} required />
            <select className="search" value={form.eventType} onChange={(e) => setForm({ ...form, eventType: e.target.value })}>
              {["HOLIDAY", "EVENT", "PTM", "EXAM", "ACTIVITY"].map((t) => <option key={t}>{t}</option>)}
            </select>
            <input className="search" type="date" value={form.startDate} onChange={(e) => setForm({ ...form, startDate: e.target.value })} />
            <Button type="submit">Add</Button>
          </div>
        </Form>
      ) : null}
      {list.loading ? <Loading /> : <Table headers={["When", "Type", "Title"]} rows={(list.data || []).map((e) => [fmtDate(e.startDate), pretty(e.eventType), e.title])} />}
    </>
  );
}

export function NotificationsPage() {
  const list = useAsync(() => notificationApi.list());
  const toast = useToast();
  return (
    <>
      <div className="page-title"><div><h1>Notifications</h1><p>In-app messages from homework, fees and results</p></div></div>
      {list.loading ? <Loading /> : list.error ? <ErrorBox error={list.error} /> : (
        <Table
          headers={["When", "Title", ""]}
          rows={(list.data?.content || []).map((n) => [
            fmtDate(n.createdAt), n.title,
            n.readAt ? "Read" : <Button key={n.id} kind="ghost" onClick={async () => { await notificationApi.read(n.id); toast("ok", "Marked read"); void list.reload(); }}>Mark read</Button>,
          ])}
        />
      )}
    </>
  );
}

export function ReportsPage() {
  const dash = useAsync(() => reportApi.schoolDashboard());
  const [month, setMonth] = useState(monthStart());
  const fees = useAsync(() => reportApi.feeCollection(month), [month]);
  if (dash.loading) return <Loading />;
  const m = dash.data || {};
  const billed = Number(fees.data?.billed || 0);
  const collected = Number(fees.data?.collected || 0);
  return (
    <>
      <div className="page-title"><div><h1>Reports</h1><p>Collection and school census</p></div></div>
      <div className="grid two">
        <div className="card">
          <h3>Census</h3>
          <Bars items={[
            { label: "Students", value: Number(m.students || 0) },
            { label: "Teachers", value: Number(m.teachers || 0) },
            { label: "Parents", value: Number(m.parents || 0) },
            { label: "Unpaid", value: Number(m.unpaidChallans || 0) },
          ]} />
        </div>
        <div className="card">
          <h3>Fee collection</h3>
          <Field label="Month"><input type="month" value={month.slice(0, 7)} onChange={(e) => setMonth(`${e.target.value}-01`)} /></Field>
          {fees.loading ? <Loading /> : (
            <Bars items={[
              { label: "Billed", value: billed, max: Math.max(billed, collected, 1) },
              { label: "Collected", value: collected, max: Math.max(billed, collected, 1) },
            ]} />
          )}
        </div>
      </div>
    </>
  );
}
