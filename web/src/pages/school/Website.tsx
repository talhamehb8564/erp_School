import { useEffect, useState } from "react";
import { campusApi, fileApi, websiteApi } from "../../api/services";
import { useBranding } from "../../lib/branding";
import { useSession } from "../../lib/session";
import { useToast } from "../../lib/toast";
import { Button, Field, Form, QueryState, useAsync } from "../../ui/kit";
import type { Campus } from "../../lib/types";

type Hero = { imageUrl: string; title?: string; caption?: string };

export default function WebsitePage() {
  const toast = useToast();
  const { reload: reloadBrand } = useBranding();
  const { reload: reloadSession } = useSession();
  const site = useAsync(() => websiteApi.get());
  const campuses = useAsync(() => campusApi.list());
  const [form, setForm] = useState<Record<string, string>>({});
  const [gallery, setGallery] = useState<Hero[]>([]);
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
      primaryColor: String(loaded.primaryColor || "#1c2740"),
      accentColor: String(loaded.accentColor || "#c08956"),
      secondaryColor: String(loaded.secondaryColor || "#1f4d43"),
      logoUrl: String(loaded.logoUrl || ""),
    });
    setGallery(Array.isArray(loaded.gallery) ? loaded.gallery as Hero[] : []);
  }, [loaded]);

  return (
    <>
      <div className="page-title">
        <div><h1>School website</h1><p>Global name, logo and colours apply to every campus. Heroes and contact are per branch.</p></div>
      </div>
      <QueryState status={site} label="website">
        <div className="card" style={{ marginBottom: 16 }}>
          <h3>Global branding</h3>
          <Form busyLabel="Saving…" onSubmit={async () => {
            await websiteApi.save({ ...form, gallery });
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
              <input type="file" accept="image/*" onChange={async (e) => {
                const f = e.target.files?.[0];
                if (!f) return;
                const up = await fileApi.upload(f);
                setForm((cur) => ({ ...cur, logoUrl: String(up.url || "") }));
              }} />
            </Field>
            {form.logoUrl ? <img src={form.logoUrl} alt="" style={{ height: 48 }} /> : null}
            <Field label="About"><textarea value={form.aboutText || ""} onChange={(e) => setForm({ ...form, aboutText: e.target.value })} rows={4} /></Field>
            <Field label="Academics"><textarea value={form.academicsText || ""} onChange={(e) => setForm({ ...form, academicsText: e.target.value })} rows={3} /></Field>
            <Field label="Achievements"><textarea value={form.achievementsText || ""} onChange={(e) => setForm({ ...form, achievementsText: e.target.value })} rows={3} /></Field>
            <Field label="Public email"><input value={form.email || ""} onChange={(e) => setForm({ ...form, email: e.target.value })} /></Field>
            <Field label="Public phone"><input value={form.phone || ""} onChange={(e) => setForm({ ...form, phone: e.target.value })} /></Field>
            <Field label="Address"><input value={form.addressLine || ""} onChange={(e) => setForm({ ...form, addressLine: e.target.value })} /></Field>
            <Field label="City"><input value={form.city || ""} onChange={(e) => setForm({ ...form, city: e.target.value })} /></Field>
            <Field label="Facebook"><input value={form.facebookUrl || ""} onChange={(e) => setForm({ ...form, facebookUrl: e.target.value })} /></Field>
            <Field label="Instagram"><input value={form.instagramUrl || ""} onChange={(e) => setForm({ ...form, instagramUrl: e.target.value })} /></Field>
            <Field label="YouTube"><input value={form.youtubeUrl || ""} onChange={(e) => setForm({ ...form, youtubeUrl: e.target.value })} /></Field>
            <div className="row">
              <Button type="button" kind="ghost" onClick={async () => {
                const input = document.createElement("input");
                input.type = "file"; input.accept = "image/*";
                input.onchange = async () => {
                  const f = input.files?.[0];
                  if (!f) return;
                  const up = await fileApi.upload(f);
                  setGallery((g) => [...g, { imageUrl: String(up.url || ""), caption: "" }]);
                };
                input.click();
              }}>Add gallery photo</Button>
            </div>
            <div className="site-gallery" style={{ marginTop: 8 }}>
              {gallery.map((g, i) => (
                <figure key={g.imageUrl + i}>
                  <img src={g.imageUrl} alt="" />
                  <input placeholder="Caption" value={g.caption || ""} onChange={(e) => setGallery(gallery.map((x, n) => n === i ? { ...x, caption: e.target.value } : x))} />
                </figure>
              ))}
            </div>
            <Button type="submit" kind="brass" loadingText="Saving…">Save global website</Button>
          </Form>
        </div>
      </QueryState>
      <QueryState status={campuses} label="campuses">
        {(campuses.data || []).map((c) => <CampusWebsite key={c.id} campus={c} onSaved={() => { void campuses.reload(); void reloadBrand(); }} />)}
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
  const [hero, setHero] = useState<Hero[]>(() => {
    try {
      return campus.heroJson ? JSON.parse(campus.heroJson) as Hero[] : [];
    } catch {
      return [];
    }
  });
  return (
    <div className="card" style={{ marginBottom: 12 }}>
      <h3>{campus.name}</h3>
      <p className="hint">Branch contact and hero slider. Logo and colours stay global.</p>
      <Form busyLabel="Saving campus…" onSubmit={async () => {
        await campusApi.update(campus.id, { name: campus.name, code: campus.code, email, phone, address, city, facebookUrl, instagramUrl, youtubeUrl, twitterUrl, hero });
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
        <Button type="button" kind="ghost" onClick={async () => {
          const input = document.createElement("input");
          input.type = "file"; input.accept = "image/*";
          input.onchange = async () => {
            const f = input.files?.[0];
            if (!f) return;
            const up = await fileApi.upload(f);
            setHero((h) => [...h, { imageUrl: String(up.url || ""), title: campus.name, caption: "" }]);
          };
          input.click();
        }}>Add hero image</Button>
        <div className="site-gallery" style={{ marginTop: 8 }}>
          {hero.map((h, i) => (
            <figure key={h.imageUrl + i}>
              <img src={h.imageUrl} alt="" />
              <input placeholder="Title" value={h.title || ""} onChange={(e) => setHero(hero.map((x, n) => n === i ? { ...x, title: e.target.value } : x))} />
              <input placeholder="Caption" value={h.caption || ""} onChange={(e) => setHero(hero.map((x, n) => n === i ? { ...x, caption: e.target.value } : x))} />
            </figure>
          ))}
        </div>
        <Button type="submit" kind="brass" loadingText="Saving campus…">Save branch website</Button>
      </Form>
    </div>
  );
}
