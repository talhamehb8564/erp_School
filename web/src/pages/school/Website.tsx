import { useEffect, useMemo, useState } from "react";
import { campusApi, fileApi, websiteApi } from "../../api/services";
import { ApiError } from "../../api/client";
import { useBranding } from "../../lib/branding";
import { useSession } from "../../lib/session";
import { useToast } from "../../lib/toast";
import { DEFAULT_HEROES } from "../../lib/site";
import { Button, Field, Form, QueryState, useAsync } from "../../ui/kit";
import type { Campus } from "../../lib/types";

type Hero = { imageUrl: string; title?: string; caption?: string };

async function uploadFile(file: File, toast: (k: "ok" | "err" | "info", m: string) => void) {
  try {
    const up = await fileApi.upload(file);
    const url = String(up.url || "");
    if (!url) throw new Error("Upload did not return a file URL");
    return url;
  } catch (e) {
    const msg = e instanceof ApiError ? e.message : e instanceof Error ? e.message : "Upload failed";
    toast("err", msg);
    throw e;
  }
}

export default function WebsitePage() {
  const { user } = useSession();
  if (user?.role === "PRINCIPAL") return <PrincipalWebsite />;
  return <AdminWebsite />;
}

type CampusPrincipal = {
  id: string;
  name: string;
  principalName?: string;
  principalTitle?: string;
  principalPhotoUrl?: string;
  principalPhotos?: Hero[];
  principalMessage?: string;
  principalPublished?: boolean;
};

function PrincipalWebsite() {
  const toast = useToast();
  const { user } = useSession();
  const { reload: reloadBrand } = useBranding();
  const site = useAsync(() => websiteApi.get());
  const loaded = site.data;
  const campuses = useMemo(
    () => (Array.isArray(loaded?.campuses) ? loaded.campuses : []) as CampusPrincipal[],
    [loaded],
  );
  const pinned = user?.campusId || "";
  const [campusId, setCampusId] = useState(pinned);
  const [form, setForm] = useState({
    principalName: "",
    principalTitle: "Principal",
    principalPhotoUrl: "",
    principalMessage: "",
    principalPublished: true,
  });
  const [photos, setPhotos] = useState<Hero[]>([]);
  const [busyFile, setBusyFile] = useState(false);

  useEffect(() => {
    if (!campuses.length) return;
    setCampusId((cur) => {
      if (pinned && campuses.some((c) => c.id === pinned)) return pinned;
      if (cur && campuses.some((c) => c.id === cur)) return cur;
      return campuses[0].id;
    });
  }, [campuses, pinned]);

  useEffect(() => {
    const row = campuses.find((c) => c.id === campusId);
    if (!row) return;
    setForm({
      principalName: String(row.principalName || user?.fullName || ""),
      principalTitle: String(row.principalTitle || "Principal"),
      principalPhotoUrl: String(row.principalPhotoUrl || ""),
      principalMessage: String(row.principalMessage || ""),
      principalPublished: row.principalPublished !== false,
    });
    const list = Array.isArray(row.principalPhotos) ? row.principalPhotos : [];
    setPhotos(list.length ? list : (row.principalPhotoUrl ? [{ imageUrl: String(row.principalPhotoUrl) }] : []));
  }, [campusId, campuses, user?.fullName]);

  const lockedCampus = Boolean(pinned);

  return (
    <>
      <div className="page-title">
        <div>
          <h1>Principal’s message</h1>
          <p>Each branch has its own Principal. School Admin cannot overwrite this section. Photographs are stored as uploaded files.</p>
        </div>
        <a className="btn ghost" href="/" target="_blank" rel="noreferrer">Preview website</a>
      </div>
      <QueryState status={site} label="principal profile">
        <div className="grid two">
          <div className="card">
            <Form busyLabel="Publishing…" onSubmit={async () => {
              const photoUrl = form.principalPhotoUrl || photos[0]?.imageUrl || "";
              await websiteApi.savePrincipal({
                ...form,
                principalPhotoUrl: photoUrl,
                principalPhotos: photos,
                campusId,
              });
              toast("ok", "Principal profile saved for this branch");
              await site.reload();
              await reloadBrand();
            }}>
              <Field label="Campus">
                <select value={campusId} disabled={lockedCampus} onChange={(e) => setCampusId(e.target.value)} required>
                  {campuses.map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}
                </select>
              </Field>
              {lockedCampus ? <p className="hint">Your account is assigned to this campus. Other branches are edited by that campus Principal.</p> : null}
              <Field label="Display name"><input value={form.principalName} onChange={(e) => setForm({ ...form, principalName: e.target.value })} /></Field>
              <Field label="Designation"><input value={form.principalTitle} onChange={(e) => setForm({ ...form, principalTitle: e.target.value })} /></Field>
              <Field label="Photographs">
                <input type="file" accept="image/jpeg,image/png,image/webp,image/gif" disabled={busyFile} onChange={async (e) => {
                  const f = e.target.files?.[0];
                  e.target.value = "";
                  if (!f) return;
                  setBusyFile(true);
                  try {
                    const url = await uploadFile(f, toast);
                    setPhotos((cur) => [...cur, { imageUrl: url, caption: "" }]);
                    setForm((cur) => cur.principalPhotoUrl ? cur : { ...cur, principalPhotoUrl: url });
                    toast("ok", "Photograph uploaded");
                  } catch { /* toasted */ } finally {
                    setBusyFile(false);
                  }
                }} />
              </Field>
              <p className="hint">Add a portrait plus photographs at school, at events, or with students. The first image is the profile portrait.</p>
              <div className="site-gallery" style={{ marginTop: 8 }}>
                {photos.map((p, i) => (
                  <figure key={p.imageUrl + i}>
                    <img src={p.imageUrl} alt="" />
                    <input placeholder="Caption" value={p.caption || ""} onChange={(e) => setPhotos(photos.map((x, n) => n === i ? { ...x, caption: e.target.value } : x))} />
                    <div className="row">
                      <Button kind="ghost" onClick={() => {
                        if (i === 0) return;
                        const next = photos.slice();
                        const [row] = next.splice(i, 1);
                        next.splice(i - 1, 0, row);
                        setPhotos(next);
                      }}>Up</Button>
                      <Button kind="ghost" onClick={() => setPhotos(photos.filter((_, n) => n !== i))}>Remove</Button>
                    </div>
                  </figure>
                ))}
              </div>
              <Field label="Message"><textarea rows={10} value={form.principalMessage} onChange={(e) => setForm({ ...form, principalMessage: e.target.value })} /></Field>
              <label className="chip">
                <input type="checkbox" checked={form.principalPublished} onChange={(e) => setForm({ ...form, principalPublished: e.target.checked })} />
                Publish on the public website for this branch
              </label>
              <Button type="submit" kind="brass" loadingText="Publishing…">Save and publish</Button>
            </Form>
          </div>
          <div className="card">
            <p className="kicker">Preview · {campuses.find((c) => c.id === campusId)?.name}</p>
            <h3>Principal’s Message</h3>
            <div className="site-principal" style={{ display: "grid", gap: 16 }}>
              {(photos[0]?.imageUrl || form.principalPhotoUrl) ? <img src={photos[0]?.imageUrl || form.principalPhotoUrl} alt="" style={{ width: 140, height: 170, objectFit: "cover", borderRadius: 16 }} /> : null}
              <div>
                <strong>{form.principalName || "Principal"}</strong>
                <p className="hint">{form.principalTitle}</p>
                <p style={{ whiteSpace: "pre-wrap" }}>{form.principalMessage || "Write a message to appear here."}</p>
              </div>
            </div>
          </div>
        </div>
      </QueryState>
    </>
  );
}

function AdminWebsite() {
  const toast = useToast();
  const { reload: reloadBrand } = useBranding();
  const { reload: reloadSession } = useSession();
  const site = useAsync(() => websiteApi.get());
  const campuses = useAsync(() => campusApi.list());
  const [form, setForm] = useState<Record<string, string>>({});
  const [gallery, setGallery] = useState<Hero[]>([]);
  const [noticePublished, setNoticePublished] = useState(false);
  const [busyFile, setBusyFile] = useState(false);
  const loaded = site.data;

  useEffect(() => {
    if (!loaded) return;
    setForm({
      name: String(loaded.name || ""),
      tagline: String(loaded.tagline || ""),
      aboutText: String(loaded.aboutText || ""),
      academicsText: String(loaded.academicsText || ""),
      achievementsText: String(loaded.achievementsText || ""),
      email: String(loaded.email || ""),
      phone: String(loaded.phone || ""),
      addressLine: String(loaded.addressLine || ""),
      city: String(loaded.city || ""),
      facebookUrl: String(loaded.facebookUrl || ""),
      instagramUrl: String(loaded.instagramUrl || ""),
      youtubeUrl: String(loaded.youtubeUrl || ""),
      twitterUrl: String(loaded.twitterUrl || ""),
      linkedinUrl: String(loaded.linkedinUrl || ""),
      whatsappUrl: String(loaded.whatsappUrl || ""),
      primaryColor: String(loaded.primaryColor || "#1c2740"),
      accentColor: String(loaded.accentColor || "#c08956"),
      secondaryColor: String(loaded.secondaryColor || "#1f4d43"),
      logoUrl: String(loaded.logoUrl || ""),
      mapLat: loaded.mapLat == null ? "" : String(loaded.mapLat),
      mapLng: loaded.mapLng == null ? "" : String(loaded.mapLng),
      mapPlace: String(loaded.mapPlace || ""),
      noticeTitle: String(loaded.noticeTitle || ""),
      noticeBody: String(loaded.noticeBody || ""),
      noticeVideoUrl: String(loaded.noticeVideoUrl || ""),
      noticePosterUrl: String(loaded.noticePosterUrl || ""),
    });
    setNoticePublished(Boolean(loaded.noticePublished));
    setGallery(Array.isArray(loaded.gallery) ? loaded.gallery as Hero[] : []);
  }, [loaded]);

  const pick = async (accept: string, onUrl: (url: string) => void) => {
    const input = document.createElement("input");
    input.type = "file";
    input.accept = accept;
    input.onchange = async () => {
      const f = input.files?.[0];
      if (!f) return;
      setBusyFile(true);
      try {
        onUrl(await uploadFile(f, toast));
      } catch { /* toasted */ } finally {
        setBusyFile(false);
      }
    };
    input.click();
  };

  return (
    <>
      <div className="page-title">
        <div>
          <h1>School website</h1>
          <p>Global name, logo and colours apply to every campus. Heroes and contact are per branch. The Principal owns their message separately.</p>
        </div>
        <a className="btn ghost" href="/" target="_blank" rel="noreferrer">Preview website</a>
      </div>
      <QueryState status={site} label="website">
        <div className="card" style={{ marginBottom: 16 }}>
          <h3>Global branding</h3>
          <Form busyLabel="Saving…" onSubmit={async () => {
            await websiteApi.save({
              ...form,
              gallery,
              noticePublished,
              mapLat: form.mapLat || null,
              mapLng: form.mapLng || null,
            });
            toast("ok", "Website saved");
            await site.reload();
            await reloadBrand();
            await reloadSession();
          }}>
            <Field label="School name"><input value={form.name || ""} onChange={(e) => setForm({ ...form, name: e.target.value })} required /></Field>
            <Field label="Tagline"><input value={form.tagline || ""} onChange={(e) => setForm({ ...form, tagline: e.target.value })} /></Field>
            <div className="row">
              <Field label="Primary"><input type="color" value={form.primaryColor || "#1c2740"} onChange={(e) => setForm({ ...form, primaryColor: e.target.value })} /></Field>
              <Field label="Accent"><input type="color" value={form.accentColor || "#c08956"} onChange={(e) => setForm({ ...form, accentColor: e.target.value })} /></Field>
              <Field label="Secondary"><input type="color" value={form.secondaryColor || "#1f4d43"} onChange={(e) => setForm({ ...form, secondaryColor: e.target.value })} /></Field>
            </div>
            <Field label="Logo">
              <input type="file" accept="image/jpeg,image/png,image/webp,image/gif" disabled={busyFile} onChange={async (e) => {
                const f = e.target.files?.[0];
                e.target.value = "";
                if (!f) return;
                setBusyFile(true);
                try {
                  const url = await uploadFile(f, toast);
                  setForm((cur) => ({ ...cur, logoUrl: url }));
                  toast("ok", "Logo uploaded");
                } catch { /* toasted */ } finally {
                  setBusyFile(false);
                }
              }} />
            </Field>
            {form.logoUrl ? <img src={form.logoUrl} alt="" style={{ height: 48 }} /> : null}
            <Field label="About"><textarea value={form.aboutText || ""} onChange={(e) => setForm({ ...form, aboutText: e.target.value })} rows={4} /></Field>
            <Field label="Academics / programmes"><textarea value={form.academicsText || ""} onChange={(e) => setForm({ ...form, academicsText: e.target.value })} rows={6} /></Field>
            <p className="hint">Separate programmes with a blank line. The first line of each block is the heading shown on the public site.</p>
            <Field label="Achievements"><textarea value={form.achievementsText || ""} onChange={(e) => setForm({ ...form, achievementsText: e.target.value })} rows={3} /></Field>
            <div className="row">
              <Field label="Public email"><input value={form.email || ""} onChange={(e) => setForm({ ...form, email: e.target.value })} /></Field>
              <Field label="Public phone"><input value={form.phone || ""} onChange={(e) => setForm({ ...form, phone: e.target.value })} /></Field>
            </div>
            <Field label="Address"><input value={form.addressLine || ""} onChange={(e) => setForm({ ...form, addressLine: e.target.value })} /></Field>
            <Field label="City"><input value={form.city || ""} onChange={(e) => setForm({ ...form, city: e.target.value })} /></Field>
            <div className="row">
              <Field label="Map latitude"><input value={form.mapLat || ""} onChange={(e) => setForm({ ...form, mapLat: e.target.value })} placeholder="e.g. 31.5204" /></Field>
              <Field label="Map longitude"><input value={form.mapLng || ""} onChange={(e) => setForm({ ...form, mapLng: e.target.value })} placeholder="e.g. 74.3587" /></Field>
            </div>
            <Field label="Map place name (optional)"><input value={form.mapPlace || ""} onChange={(e) => setForm({ ...form, mapPlace: e.target.value })} /></Field>
            <p className="hint">Leave coordinates empty until you have the school’s real location. Do not invent a pin.</p>
            <Field label="Facebook"><input value={form.facebookUrl || ""} onChange={(e) => setForm({ ...form, facebookUrl: e.target.value })} /></Field>
            <Field label="Instagram"><input value={form.instagramUrl || ""} onChange={(e) => setForm({ ...form, instagramUrl: e.target.value })} /></Field>
            <Field label="YouTube"><input value={form.youtubeUrl || ""} onChange={(e) => setForm({ ...form, youtubeUrl: e.target.value })} /></Field>
            <Field label="X / Twitter"><input value={form.twitterUrl || ""} onChange={(e) => setForm({ ...form, twitterUrl: e.target.value })} /></Field>
            <Field label="LinkedIn"><input value={form.linkedinUrl || ""} onChange={(e) => setForm({ ...form, linkedinUrl: e.target.value })} /></Field>
            <Field label="WhatsApp"><input value={form.whatsappUrl || ""} onChange={(e) => setForm({ ...form, whatsappUrl: e.target.value })} placeholder="https://wa.me/…" /></Field>

            <h3>Public notice &amp; video</h3>
            <p className="hint">Shown on the public website when published. Video is optional — YouTube URL or an uploaded MP4.</p>
            <Field label="Notice title"><input value={form.noticeTitle || ""} onChange={(e) => setForm({ ...form, noticeTitle: e.target.value })} /></Field>
            <Field label="Notice body"><textarea rows={3} value={form.noticeBody || ""} onChange={(e) => setForm({ ...form, noticeBody: e.target.value })} /></Field>
            <Field label="Video URL (YouTube or file)"><input value={form.noticeVideoUrl || ""} onChange={(e) => setForm({ ...form, noticeVideoUrl: e.target.value })} /></Field>
            <div className="row">
              <Button type="button" kind="ghost" disabled={busyFile} onClick={() => void pick("video/mp4,video/webm,video/quicktime", (url) => {
                setForm((cur) => ({ ...cur, noticeVideoUrl: url }));
                toast("ok", "Video uploaded");
              })}>Upload video</Button>
              <Button type="button" kind="ghost" disabled={busyFile} onClick={() => void pick("image/*", (url) => {
                setForm((cur) => ({ ...cur, noticePosterUrl: url }));
                toast("ok", "Poster uploaded");
              })}>Upload poster</Button>
            </div>
            <label className="chip">
              <input type="checkbox" checked={noticePublished} onChange={(e) => setNoticePublished(e.target.checked)} />
              Publish notice on the public website
            </label>

            <h3>Gallery</h3>
            <div className="row" style={{ marginBottom: 8 }}>
              <Button type="button" kind="ghost" disabled={busyFile} onClick={() => void pick("image/jpeg,image/png,image/webp,image/gif", (url) => {
                setGallery((g) => [...g, { imageUrl: url, caption: "" }]);
                toast("ok", "Photo added");
              })}>Add gallery photo</Button>
            </div>
            <div className="site-gallery" style={{ marginTop: 8 }}>
              {gallery.map((g, i) => (
                <figure key={g.imageUrl + i}>
                  <img src={g.imageUrl} alt="" />
                  <input placeholder="Caption" value={g.caption || ""} onChange={(e) => setGallery(gallery.map((x, n) => n === i ? { ...x, caption: e.target.value } : x))} />
                  <Button kind="ghost" onClick={() => setGallery(gallery.filter((_, n) => n !== i))}>Remove</Button>
                </figure>
              ))}
            </div>
            <p className="hint">Each branch Principal publishes their own name, photographs and message. This form never overwrites those fields.</p>
            <Button type="submit" kind="brass" loadingText="Saving…">Save global website</Button>
          </Form>
        </div>
      </QueryState>
      <QueryState status={campuses} label="campuses">
        {(campuses.data || []).map((c) => (
          <CampusWebsite key={c.id} campus={c} onSaved={() => { void campuses.reload(); void reloadBrand(); }} />
        ))}
      </QueryState>
    </>
  );
}

function CampusWebsite({ campus, onSaved }: { campus: Campus; onSaved: () => void }) {
  const toast = useToast();
  const [email, setEmail] = useState(campus.email || "");
  const [phone, setPhone] = useState(campus.phone || "");
  const [address, setAddress] = useState(campus.address || "");
  const [city, setCity] = useState(campus.city || "");
  const [facebookUrl, setFacebookUrl] = useState(campus.facebookUrl || "");
  const [instagramUrl, setInstagramUrl] = useState(campus.instagramUrl || "");
  const [youtubeUrl, setYoutubeUrl] = useState(campus.youtubeUrl || "");
  const [twitterUrl, setTwitterUrl] = useState(campus.twitterUrl || "");
  const [whatsappUrl, setWhatsappUrl] = useState(campus.whatsappUrl || "");
  const [mapLat, setMapLat] = useState(campus.mapLat == null ? "" : String(campus.mapLat));
  const [mapLng, setMapLng] = useState(campus.mapLng == null ? "" : String(campus.mapLng));
  const [mapPlace, setMapPlace] = useState(campus.mapPlace || "");
  const [hero, setHero] = useState<Hero[]>(() => {
    try {
      return campus.heroJson ? JSON.parse(campus.heroJson) as Hero[] : [];
    } catch {
      return [];
    }
  });
  const [busyFile, setBusyFile] = useState(false);
  const move = (i: number, dir: number) => {
    const j = i + dir;
    if (j < 0 || j >= hero.length) return;
    const next = hero.slice();
    const [row] = next.splice(i, 1);
    next.splice(j, 0, row);
    setHero(next);
  };
  return (
    <div className="card" style={{ marginBottom: 12 }}>
      <h3>{campus.name}</h3>
      <p className="hint">Branch contact and hero slider. Logo and colours stay global.</p>
      <Form busyLabel="Saving campus…" onSubmit={async () => {
        await campusApi.update(campus.id, { name: campus.name, code: campus.code, email, phone, address, city, facebookUrl, instagramUrl, youtubeUrl, twitterUrl, whatsappUrl, hero, mapLat, mapLng, mapPlace });
        toast("ok", `${campus.name} website updated`);
        onSaved();
      }}>
        <Field label="Email"><input value={email} onChange={(e) => setEmail(e.target.value)} /></Field>
        <Field label="Phone"><input value={phone} onChange={(e) => setPhone(e.target.value)} /></Field>
        <Field label="Address"><input value={address} onChange={(e) => setAddress(e.target.value)} /></Field>
        <Field label="City"><input value={city} onChange={(e) => setCity(e.target.value)} /></Field>
        <Field label="Facebook"><input value={facebookUrl} onChange={(e) => setFacebookUrl(e.target.value)} /></Field>
        <Field label="Instagram"><input value={instagramUrl} onChange={(e) => setInstagramUrl(e.target.value)} /></Field>
        <Field label="YouTube"><input value={youtubeUrl} onChange={(e) => setYoutubeUrl(e.target.value)} /></Field>
        <Field label="X / Twitter"><input value={twitterUrl} onChange={(e) => setTwitterUrl(e.target.value)} /></Field>
        <Field label="WhatsApp"><input value={whatsappUrl} onChange={(e) => setWhatsappUrl(e.target.value)} placeholder="https://wa.me/…" /></Field>
        <div className="row">
          <Field label="Map latitude"><input value={mapLat} onChange={(e) => setMapLat(e.target.value)} placeholder="Leave empty until known" /></Field>
          <Field label="Map longitude"><input value={mapLng} onChange={(e) => setMapLng(e.target.value)} /></Field>
        </div>
        <Field label="Map place name (optional)"><input value={mapPlace} onChange={(e) => setMapPlace(e.target.value)} /></Field>
        <p className="hint">Branch map uses these coordinates when set. Do not invent a pin. Multiple hero images power this campus slider.</p>
        <div className="row">
          <Button type="button" kind="ghost" disabled={busyFile} onClick={async () => {
            const input = document.createElement("input");
            input.type = "file";
            input.accept = "image/jpeg,image/png,image/webp,image/gif";
            input.onchange = async () => {
              const f = input.files?.[0];
              if (!f) return;
              setBusyFile(true);
              try {
                const url = await uploadFile(f, toast);
                setHero((h) => [...h, { imageUrl: url, title: campus.name, caption: "" }]);
                toast("ok", "Hero image uploaded");
              } catch { /* toasted */ } finally {
                setBusyFile(false);
              }
            };
            input.click();
          }}>Add hero image</Button>
          <Button type="button" kind="ghost" onClick={() => {
            setHero(DEFAULT_HEROES.map((h) => ({ ...h, title: campus.name })));
            toast("info", "Starter slides added — save the campus to publish them");
          }}>Use starter slides</Button>
        </div>
        <div className="site-gallery" style={{ marginTop: 8 }}>
          {hero.map((h, i) => (
            <figure key={h.imageUrl + i}>
              <img src={h.imageUrl} alt="" />
              <input placeholder="Title" value={h.title || ""} onChange={(e) => setHero(hero.map((x, n) => n === i ? { ...x, title: e.target.value } : x))} />
              <input placeholder="Caption" value={h.caption || ""} onChange={(e) => setHero(hero.map((x, n) => n === i ? { ...x, caption: e.target.value } : x))} />
              <div className="row">
                <Button kind="ghost" onClick={() => move(i, -1)}>Up</Button>
                <Button kind="ghost" onClick={() => move(i, 1)}>Down</Button>
                <Button kind="ghost" onClick={() => setHero(hero.filter((_, n) => n !== i))}>Remove</Button>
              </div>
            </figure>
          ))}
        </div>
        <Button type="submit" kind="brass" loadingText="Saving campus…">Save branch website</Button>
      </Form>
    </div>
  );
}
