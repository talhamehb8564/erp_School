import { useEffect, useMemo, useState } from "react";
import { Link } from "react-router-dom";
import { setupApi } from "../api/services";
import { useBranding } from "../lib/branding";
import { Button, Field, Form } from "../ui/kit";
import { useToast } from "../lib/toast";

export default function Welcome() {
  const { school, campuses, campus, setCampusId, loading, missing, error, reload } = useBranding();
  const [needsSetup, setNeedsSetup] = useState(false);
  const [schoolName, setSchoolName] = useState("");
  const [b1, setB1] = useState("Main Campus");
  const [b2, setB2] = useState("Senior Campus");
  const [b3, setB3] = useState("Junior Campus");
  const [created, setCreated] = useState<{ username: string; password?: string; message?: string } | null>(null);
  const [slide, setSlide] = useState(0);
  const toast = useToast();
  const heroes = campus?.hero?.filter((h) => h.imageUrl) || [];

  useEffect(() => {
    if (!missing) return;
    void setupApi.status().then((s) => setNeedsSetup(Boolean(s?.needsSetup))).catch(() => undefined);
  }, [missing]);

  useEffect(() => {
    if (heroes.length < 2) return;
    const id = window.setInterval(() => setSlide((n) => (n + 1) % heroes.length), 5600);
    return () => window.clearInterval(id);
  }, [heroes.length]);

  useEffect(() => { setSlide(0); }, [campus?.id]);

  const contact = useMemo(() => ({
    email: campus?.email || school?.email,
    phone: campus?.phone || school?.phone,
    address: campus?.address || school?.addressLine,
    city: campus?.city || school?.city,
    facebook: campus?.facebookUrl || school?.facebookUrl,
    instagram: campus?.instagramUrl || school?.instagramUrl,
    youtube: campus?.youtubeUrl || school?.youtubeUrl,
    twitter: campus?.twitterUrl || school?.twitterUrl,
  }), [campus, school]);

  if (loading && !school) {
    return <div className="site-boot"><p className="hint">Loading school…</p></div>;
  }

  if (needsSetup && !created) {
    return (
      <div className="site-boot">
        <div className="card" style={{ maxWidth: 480 }}>
          <p className="kicker">First-time setup</p>
          <h1 className="serif">Create your school</h1>
          <p className="hint">Name the school and three branches. Branding will appear on this website immediately after.</p>
          <Form busyLabel="Creating school…" onSubmit={async () => {
            const res = await setupApi.complete({ schoolName, branches: [b1, b2, b3] });
            setCreated({ username: res.admin.username, password: res.admin.temporaryPassword, message: res.message });
            setNeedsSetup(false);
            toast("ok", res.message || "School created");
            await reload();
          }}>
            <Field label="School name"><input value={schoolName} onChange={(e) => setSchoolName(e.target.value)} required /></Field>
            <Field label="Branch 1"><input value={b1} onChange={(e) => setB1(e.target.value)} required /></Field>
            <Field label="Branch 2"><input value={b2} onChange={(e) => setB2(e.target.value)} required /></Field>
            <Field label="Branch 3"><input value={b3} onChange={(e) => setB3(e.target.value)} required /></Field>
            <Button type="submit" kind="brass" loadingText="Creating school…">Create school and 3 branches</Button>
          </Form>
        </div>
      </div>
    );
  }

  if (error && !school) {
    return (
      <div className="site-boot">
        <div className="error-box"><strong>Unable to load the school website</strong><p>{error}</p></div>
      </div>
    );
  }

  const name = school?.name || "School";
  const letter = name.trim().charAt(0).toUpperCase() || "S";

  return (
    <div className="site">
      <header className="site-nav">
        <div className="site-brand">
          {school?.logoUrl ? <img src={school.logoUrl} alt="" /> : <div className="mark">{letter}</div>}
          <div>
            <strong>{name}</strong>
            {school?.tagline ? <div className="hint">{school.tagline}</div> : null}
          </div>
        </div>
        <nav className="site-links">
          <a href="#about">About</a>
          <a href="#academics">Academics</a>
          <a href="#campuses">Campuses</a>
          <a href="#gallery">Gallery</a>
          <a href="#contact">Contact</a>
        </nav>
        <div className="site-nav-right">
          {campuses.length ? (
            <select className="site-branch" value={campus?.id || ""} onChange={(e) => setCampusId(e.target.value)} aria-label="Select campus">
              {campuses.map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}
            </select>
          ) : null}
          <Link to="/portals" className="btn brass">School Portal</Link>
        </div>
      </header>

      <section className="site-hero">
        {heroes.length ? heroes.map((h, i) => (
          <div key={h.imageUrl + i} className={`site-slide ${i === slide ? "on" : ""}`} style={{ backgroundImage: `url(${h.imageUrl})` }}>
            <div className="site-hero-copy">
              <p className="kicker">{campus?.name}</p>
              <h1>{h.title || name}</h1>
              {h.caption || school?.tagline ? <p>{h.caption || school?.tagline}</p> : null}
            </div>
          </div>
        )) : (
          <div className="site-slide on site-slide-plain">
            <div className="site-hero-copy">
              <p className="kicker">{campus?.name || "Welcome"}</p>
              <h1>{name}</h1>
              {school?.tagline ? <p>{school.tagline}</p> : null}
            </div>
          </div>
        )}
        {heroes.length > 1 ? (
          <div className="site-dots">
            {heroes.map((_, i) => (
              <button key={i} type="button" className={i === slide ? "on" : ""} onClick={() => setSlide(i)} aria-label={`Slide ${i + 1}`} />
            ))}
          </div>
        ) : null}
      </section>

      {created ? <div className="notice site-notice">{created.message} Sign in as <span className="pwd">{created.username}</span>{created.password ? <> / <span className="pwd">{created.password}</span></> : null}</div> : null}

      <section id="about" className="site-section">
        <p className="kicker">About</p>
        <h2>{name}</h2>
        {school?.aboutText ? <p>{school.aboutText}</p> : <p className="hint">About content is published from school website settings.</p>}
      </section>

      <section id="academics" className="site-section alt">
        <p className="kicker">Academics</p>
        <h2>Programmes</h2>
        {school?.academicsText ? <p>{school.academicsText}</p> : <p className="hint">Academic programmes are published from school website settings.</p>}
      </section>

      <section className="site-section">
        <p className="kicker">Achievements</p>
        <h2>Highlights</h2>
        {school?.achievementsText ? <p>{school.achievementsText}</p> : <p className="hint">Achievements are published from school website settings.</p>}
      </section>

      <section id="campuses" className="site-section alt">
        <p className="kicker">Campuses</p>
        <h2>Our branches</h2>
        <div className="site-campus-grid">
          {campuses.map((c) => (
            <button type="button" key={c.id} className={`site-campus-card ${campus?.id === c.id ? "on" : ""}`} onClick={() => setCampusId(c.id)}>
              <strong>{c.name}</strong>
              <small>{[c.city, c.address].filter(Boolean).join(" · ") || "Campus"}</small>
            </button>
          ))}
        </div>
      </section>

      <section id="gallery" className="site-section">
        <p className="kicker">Gallery</p>
        <h2>Life at {name}</h2>
        <div className="site-gallery">
          {(school?.gallery || []).filter((g) => g.imageUrl).map((g, i) => (
            <figure key={g.imageUrl + i}><img src={g.imageUrl} alt={g.caption || ""} loading="lazy" /><figcaption>{g.caption}</figcaption></figure>
          ))}
          {!(school?.gallery || []).length ? <p className="hint">Gallery photos are managed in school website settings.</p> : null}
        </div>
      </section>

      <section id="contact" className="site-section alt">
        <p className="kicker">Contact</p>
        <h2>{campus?.name || name}</h2>
        <p>{contact.address}{contact.city ? ` · ${contact.city}` : ""}</p>
        <p>{contact.phone}{contact.email ? ` · ${contact.email}` : ""}</p>
        <div className="site-social">
          {contact.facebook ? <a href={contact.facebook} target="_blank" rel="noreferrer">Facebook</a> : null}
          {contact.instagram ? <a href={contact.instagram} target="_blank" rel="noreferrer">Instagram</a> : null}
          {contact.youtube ? <a href={contact.youtube} target="_blank" rel="noreferrer">YouTube</a> : null}
          {contact.twitter ? <a href={contact.twitter} target="_blank" rel="noreferrer">X</a> : null}
          {school?.linkedinUrl ? <a href={school.linkedinUrl} target="_blank" rel="noreferrer">LinkedIn</a> : null}
        </div>
      </section>

      <footer className="site-foot">
        <div className="site-brand">
          {school?.logoUrl ? <img src={school.logoUrl} alt="" /> : <div className="mark">{letter}</div>}
          <div>
            <strong>{name}</strong>
            <div className="hint">{campus?.name}</div>
          </div>
        </div>
        <Link to="/portals" className="btn ghost">Login</Link>
      </footer>
    </div>
  );
}
