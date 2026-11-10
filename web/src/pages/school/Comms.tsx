import { useState } from "react";
import { announcementApi, calendarApi, fileApi, notificationApi, reportApi } from "../../api/services";
import { ApiError } from "../../api/client";
import { useBranding } from "../../lib/branding";
import { useLookups } from "../../lib/lookups";
import { fmtDate, monthStart, pretty } from "../../lib/format";
import { useSession } from "../../lib/session";
import { useToast } from "../../lib/toast";
import type { NoticeAttachment } from "../../lib/types";
import { Badge, Bars, Button, Empty, Field, Form, NoticeAttachments, QueryState, Table, useAsync } from "../../ui/kit";

export function AnnouncementsPage() {
  const { user } = useSession();
  const { classes } = useLookups();
  const { reload: reloadBrand } = useBranding();
  const list = useAsync(() => announcementApi.list());
  const toast = useToast();
  const staff = user?.role === "SCHOOL_ADMIN" || user?.role === "PRINCIPAL";
  const [form, setForm] = useState({ title: "", body: "", audience: "ALL", classId: "" });
  const [attachments, setAttachments] = useState<NoticeAttachment[]>([]);
  const [uploading, setUploading] = useState(false);

  const handleUploadFiles = async (files: FileList | null) => {
    if (!files || !files.length) return;
    setUploading(true);
    try {
      const next: NoticeAttachment[] = [];
      for (const file of Array.from(files)) {
        const uploaded = await fileApi.upload(file);
        if (uploaded?.url) {
          next.push({
            id: uploaded.id,
            fileName: uploaded.fileName || file.name,
            fileUrl: uploaded.url,
            contentType: uploaded.contentType || file.type,
          });
        }
      }
      if (next.length) {
        setAttachments((prev) => [...prev, ...next]);
        toast("ok", next.length === 1 ? "Attachment uploaded" : `${next.length} attachments uploaded`);
      }
    } catch (e) {
      const msg = e instanceof ApiError ? e.message : e instanceof Error ? e.message : "Upload failed";
      toast("err", msg);
    } finally {
      setUploading(false);
    }
  };

  return (
    <>
      <div className="page-title"><div><h1>Announcements</h1><p>Visible according to audience and class</p></div></div>
      {staff ? (
        <div className="card" style={{ marginBottom: 16 }}>
          <Form
            busyLabel="Publishing…"
            onSubmit={async () => {
              await announcementApi.create({
                title: form.title,
                body: form.body,
                audience: form.audience,
                ...(form.audience === "CLASS" && form.classId ? { classId: form.classId } : {}),
                ...(attachments[0]?.fileUrl ? { attachmentUrl: attachments[0].fileUrl } : {}),
                attachments,
              });
              setForm({ title: "", body: "", audience: "ALL", classId: "" });
              setAttachments([]);
              toast("ok", "Published");
              void list.reload();
              void reloadBrand();
            }}
          >
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
            <Field label="Attachments (images, videos, PDF, or Word)">
              <input
                type="file"
                multiple
                disabled={uploading}
                accept="image/jpeg,image/png,image/webp,image/gif,video/mp4,video/webm,video/quicktime,application/pdf,.pdf,.doc,.docx"
                onChange={(e) => {
                  const files = e.target.files;
                  void handleUploadFiles(files);
                  e.target.value = "";
                }}
              />
            </Field>
            {attachments.length > 0 ? (
              <NoticeAttachments
                attachments={attachments}
                compact
                onRemove={(fileUrl) => setAttachments((prev) => prev.filter((a) => a.fileUrl !== fileUrl))}
              />
            ) : null}
            <Button type="submit" kind="brass" disabled={uploading} loadingText="Publishing…">Publish</Button>
          </Form>
        </div>
      ) : null}
      <QueryState status={list} label="announcements">
      {!(list.data || []).length ? <Empty title="No announcements" /> : (
        <div className="grid">
          {(list.data || []).map((a) => (
            <article className="card" key={a.id}>
              <div className="kicker">{pretty(a.audience)} · {fmtDate(a.publishDate)}</div>
              <h3>{a.title}</h3>
              <p>{a.body}</p>
              <NoticeAttachments attachments={a.attachments} attachmentUrl={a.attachmentUrl} />
            </article>
          ))}
        </div>
      )}
      </QueryState>
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
        <Form busyLabel="Saving…" onSubmit={async () => { await calendarApi.create(form); toast("ok", "Event saved"); void list.reload(); }}>
          <div className="row">
            <input className="search" placeholder="Title" value={form.title} onChange={(e) => setForm({ ...form, title: e.target.value })} required />
            <select className="search" value={form.eventType} onChange={(e) => setForm({ ...form, eventType: e.target.value })}>
              {["HOLIDAY", "EVENT", "PTM", "EXAM", "ACTIVITY"].map((t) => <option key={t}>{t}</option>)}
            </select>
            <input className="search" type="date" value={form.startDate} onChange={(e) => setForm({ ...form, startDate: e.target.value })} />
            <Button type="submit" loadingText="Saving…">Add</Button>
          </div>
        </Form>
      ) : null}
      <QueryState status={list} label="calendar">
        <Table headers={["When", "Type", "Title"]} rows={(list.data || []).map((e) => [fmtDate(e.startDate), pretty(e.eventType), e.title])} />
      </QueryState>
    </>
  );
}

export function NotificationsPage() {
  const list = useAsync(() => notificationApi.list());
  const toast = useToast();
  return (
    <>
      <div className="page-title"><div><h1>Notifications</h1><p>In-app messages from homework, fees and results</p></div></div>
      <QueryState status={list} label="notifications">
        <Table
          headers={["When", "Title", ""]}
          rows={(list.data?.content || []).map((n) => [
            fmtDate(n.createdAt), n.title,
            n.readAt ? "Read" : <Button key={n.id} kind="ghost" loadingText="Updating…" onClick={async () => { await notificationApi.read(n.id); toast("ok", "Marked read"); void list.reload(); }}>Mark read</Button>,
          ])}
        />
      </QueryState>
    </>
  );
}

export function ReportsPage() {
  const dash = useAsync(() => reportApi.schoolDashboard());
  const [month, setMonth] = useState(monthStart());
  const fees = useAsync(() => reportApi.feeCollection(month), [month]);
  const m = dash.data || {};
  const billed = Number(fees.data?.billed || 0);
  const collected = Number(fees.data?.collected || 0);
  return (
    <>
      <div className="page-title"><div><h1>Reports</h1><p>Collection and school census</p></div></div>
      <div className="grid two">
        <div className="card">
          <h3>Census</h3>
          <QueryState status={dash} label="census">
            <Bars items={[
              { label: "Students", value: Number(m.students || 0) },
              { label: "Teachers", value: Number(m.teachers || 0) },
              { label: "Parents", value: Number(m.parents || 0) },
              { label: "Unpaid", value: Number(m.unpaidChallans || 0) },
            ]} />
          </QueryState>
        </div>
        <div className="card">
          <h3>Fee collection</h3>
          <Field label="Month"><input type="month" value={month.slice(0, 7)} onChange={(e) => setMonth(`${e.target.value}-01`)} /></Field>
          <QueryState status={fees} label="fee collection">
            <Bars items={[
              { label: "Billed", value: billed, max: Math.max(billed, collected, 1) },
              { label: "Collected", value: collected, max: Math.max(billed, collected, 1) },
            ]} />
          </QueryState>
        </div>
      </div>
    </>
  );
}
