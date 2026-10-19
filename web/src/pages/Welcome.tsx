import { useEffect, useMemo, useRef, useState, type CSSProperties, type ReactNode } from "react";
import { createPortal } from "react-dom";
import { Link } from "react-router-dom";
import { publicApi, setupApi } from "../api/services";
import { useBranding } from "../lib/branding";
import {
  DEFAULT_ABOUT,
  DEFAULT_NOTICE_BODY,
  DEFAULT_NOTICE_TITLE,
  DEFAULT_PRINCIPAL_MESSAGE,
  contactFor,
  galleryFor,
  heroesFor,
  mapOf,
  mapsEmbedUrl,
  mapsSearchUrl,
  osmEmbed,
  principalFor,
  principalPhotos,
  programsFor,
  highlightsFor,
  youtubeId,
} from "../lib/site";
import { Button, Field, Form } from "../ui/kit";
import { SocialLink, WhatsAppFloat } from "../ui/CampusContact";
import TypeLine from "../ui/TypeLine";
import { useToast } from "../lib/toast";
import type { PublicMedia } from "../lib/branding";

const SITE_MENU: { href: string; label: string }[] = [
  { href: "#about", label: "About" },
  { href: "#principal", label: "Principal" },
  { href: "#academics", label: "Academics" },
  { href: "#highlights", label: "Highlights" },
  { href: "#gallery", label: "Life at school" },
  { href: "#campuses", label: "Campuses" },
  { href: "#contact", label: "Contact" },
];

function Reveal({ children, className }: { children: ReactNode; className?: string }) {
  const ref = useRef<HTMLDivElement>(null);
  const [on, setOn] = useState(false);
  useEffect(() => {
    const el = ref.current;
    if (!el) return;
    if (window.matchMedia("(prefers-reduced-motion: reduce)").matches) {
      setOn(true);
      return;
    }
    const io = new IntersectionObserver((entries) => {
      if (entries.some((e) => e.isIntersecting)) setOn(true);
    }, { threshold: 0.12 });
    io.observe(el);
    return () => io.disconnect();
  }, []);
  return <div ref={ref} className={`reveal ${on ? "on" : ""} ${className || ""}`}>{children}</div>;
}

export default function Welcome() {
  const { school, campuses, campus, setCampusId, loading, missing, error, reload } = useBranding();
  const [needsSetup, setNeedsSetup] = useState(false);
  const [schoolName, setSchoolName] = useState("");
  const [b1, setB1] = useState("Main Campus");
  const [b2, setB2] = useState("Senior Campus");
  const [b3, setB3] = useState("Junior Campus");
  const [created, setCreated] = useState<{ username: string; password?: string; message?: string } | null>(null);
  const [slide, setSlide] = useState(0);
  const [menu, setMenu] = useState(false);
  const [paused, setPaused] = useState(false);
  const burgerRef = useRef<HTMLButtonElement>(null);
  const toast = useToast();

  useEffect(() => {
    if (!missing) return;
    void setupApi.status().then((s) => setNeedsSetup(Boolean(s?.needsSetup))).catch(() => undefined);
  }, [missing]);

  const heroes = useMemo(() => heroesFor(campus, school), [campus, school]);
  useEffect(() => {
    if (heroes.length < 2 || paused) return;
    const id = window.setInterval(() => setSlide((n) => (n + 1) % heroes.length), 3800);
    return () => window.clearInterval(id);
  }, [heroes.length, paused]);
  useEffect(() => { setSlide(0); }, [campus?.id]);

  const contact = useMemo(() => contactFor(campus, school), [campus, school]);

  useEffect(() => {
    document.documentElement.classList.toggle("menu-open", menu);
    document.body.classList.toggle("menu-open", menu);
    return () => {
      document.documentElement.classList.remove("menu-open");
      document.body.classList.remove("menu-open");
    };
  }, [menu]);
  useEffect(() => {
    if (!menu) return;
    burgerRef.current?.focus();
    const onKey = (e: KeyboardEvent) => {
      if (e.key === "Escape") {
        setMenu(false);
        return;
      }
      if (e.key !== "Tab") return;
      const root = document.getElementById("site-menu");
      if (!root) return;
      const nodes = [burgerRef.current, ...Array.from(root.querySelectorAll<HTMLElement>("a,button"))].filter((el): el is HTMLElement => Boolean(el));
      if (!nodes.length) return;
      const first = nodes[0];
      const last = nodes[nodes.length - 1];
      const active = document.activeElement;
      if (e.shiftKey && active === first) {
        e.preventDefault();
        last.focus();
      } else if (!e.shiftKey && active === last) {
        e.preventDefault();
        first.focus();
      }
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [menu]);

  if (loading && !school) {
    return (
      <div className="site-boot">
        <div className="card site-boot-card">
          <div className="skeleton-line" style={{ width: 120 }} />
          <div className="skeleton-line" style={{ width: 240, height: 28, marginTop: 12 }} />
          <p className="hint">Loading school website…</p>
        </div>
      </div>
    );
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
  const gallery = galleryFor(school);
  const principal = principalFor(campus, school);
  const photos = principalPhotos(principal, campus);
  const principalName = principal?.name || "Principal";
  const principalTitle = principal?.title || `Principal · ${campus?.name || "Campus"}`;
  const principalMessage = principal?.message || DEFAULT_PRINCIPAL_MESSAGE;
  const principalDefault = Boolean(principal?.isDefault) || !principal?.message;
  const showPrincipal = principal?.published !== false;
  const noticeCustom = Boolean(school?.notice?.published && school.notice.title);
  const noticeTitle = noticeCustom ? school?.notice?.title : DEFAULT_NOTICE_TITLE;
  const noticeBody = noticeCustom ? school?.notice?.body : DEFAULT_NOTICE_BODY;
  const videoUrl = noticeCustom ? school?.notice?.videoUrl : "";
  const yt = youtubeId(videoUrl);
  const geo = mapOf(campus, school);
  const lat = geo.lat != null && geo.lat !== "" ? Number(geo.lat) : NaN;
  const lng = geo.lng != null && geo.lng !== "" ? Number(geo.lng) : NaN;
  const hasCoords = Number.isFinite(lat) && Number.isFinite(lng);
  const mapQuery = geo.place || [contact.address, contact.city, name].filter(Boolean).join(", ");
  const hasContact = Boolean(contact.phone || contact.email || contact.address);
  const programs = programsFor(school);
  const highlights = highlightsFor(school);

  const closeMenu = () => setMenu(false);

  const burger = (
    <button
      ref={burgerRef}
      type="button"
      className={`burger-x ${menu ? "open" : ""}`}
      aria-label={menu ? "Close menu" : "Open menu"}
      aria-expanded={menu}
      aria-controls="site-menu"
      onClick={() => setMenu((v) => !v)}
    >
      <span aria-hidden="true" /><span aria-hidden="true" /><span aria-hidden="true" />
    </button>
  );

  const drawer = (
    <div
      className={`site-drawer ${menu ? "open" : ""}`}
      aria-hidden={!menu}
      {...(!menu ? ({ inert: "" } as Record<string, string>) : {})}
    >
      <button type="button" className="site-scrim" aria-label="Close menu" tabIndex={menu ? 0 : -1} onClick={closeMenu} />
      <div
        id="site-menu"
        className="site-drawer-panel"
        role="dialog"
        aria-modal={menu}
        aria-label="Site menu"
      >
        <div className="site-drawer-top" aria-hidden="true" />
        <nav className="site-drawer-links" aria-label="On this page">
          {SITE_MENU.map((item, i) => (
            <a key={item.href} href={item.href} style={{ "--i": i } as CSSProperties} onClick={closeMenu}>{item.label}</a>
          ))}
          {school?.admissions?.enabled ? (
            <Link to="/apply" className="btn brass" style={{ "--i": SITE_MENU.length } as CSSProperties} onClick={closeMenu}>
              {school.admissions.ctaLabel || "Apply for Admission"}
            </Link>
          ) : null}
          <Link to="/portals" className="btn ghost" style={{ "--i": SITE_MENU.length + 1 } as CSSProperties} onClick={closeMenu}>School Portal</Link>
        </nav>
      </div>
    </div>
  );

  return (
    <div className="site">
      <header className="site-nav">
        <a href="#top" className="site-brand">
          {school?.logoUrl ? <img src={school.logoUrl} alt="" /> : <div className="mark" aria-hidden="true">{letter}</div>}
          <div>
            <strong>{name}</strong>
            {school?.tagline ? <div className="hint">{school.tagline}</div> : null}
          </div>
        </a>
        <div className="site-nav-tools">
          {school?.admissions?.enabled ? (
            <Link to="/apply" className="btn brass sm site-nav-apply">{school.admissions.ctaLabel || "Apply for Admission"}</Link>
          ) : null}
          {campuses.length ? (
            <select className="site-branch" value={campus?.id || ""} onChange={(e) => setCampusId(e.target.value)} aria-label="Select campus">
              {campuses.map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}
            </select>
          ) : null}
          <Link to="/portals" className="btn ghost sm site-nav-portal">School Portal</Link>
        </div>
        <div className="burger-slot" aria-hidden="true" />
      </header>
      {createPortal(
        <>
          {burger}
          {drawer}
        </>,
        document.body,
      )}

      <section className="site-hero" id="top" onMouseEnter={() => setPaused(true)} onMouseLeave={() => setPaused(false)}>
        <div className="site-hero-media">
          {heroes.map((h, i) => (
            <div key={h.imageUrl + i} className={`site-slide ${i === slide ? "on" : ""}`}>
              <img src={h.imageUrl} alt="" loading={i === 0 ? "eager" : "lazy"} />
            </div>
          ))}
          {heroes.length > 1 ? (
            <>
              <button type="button" className="site-arrow prev" aria-label="Previous slide" onClick={() => setSlide((n) => (n - 1 + heroes.length) % heroes.length)}>‹</button>
              <button type="button" className="site-arrow next" aria-label="Next slide" onClick={() => setSlide((n) => (n + 1) % heroes.length)}>›</button>
              <div className="site-dots">
                {heroes.map((_, i) => (
                  <button key={i} type="button" className={i === slide ? "on" : ""} onClick={() => setSlide(i)} aria-label={`Slide ${i + 1}`} />
                ))}
              </div>
            </>
          ) : null}
        </div>
        <div className="site-hero-copy">
          <p className="kicker">{campus?.name || "Welcome"}</p>
          <h1>{heroes[slide]?.title || name}</h1>
          {heroes[slide]?.caption || school?.tagline ? <p>{heroes[slide]?.caption || school?.tagline}</p> : null}
          {school?.admissions?.enabled ? (
            <div className="row" style={{ marginTop: 16 }}>
              <Link to="/apply" className="btn brass">{school.admissions.ctaLabel || "Apply for Admission"}</Link>
            </div>
          ) : null}
        </div>
      </section>

      {created ? <div className="notice site-notice">{created.message} Sign in as <span className="pwd">{created.username}</span>{created.password ? <> / <span className="pwd">{created.password}</span></> : null}</div> : null}

      <div className={`site-banner ${noticeCustom ? "live" : "sample"}`}>
        <div>
          <p className="kicker">{noticeCustom ? "Announcement" : "Sample notice"}</p>
          <strong>{noticeTitle}</strong>
          <p>{noticeBody}</p>
        </div>
      </div>

      <Reveal>
        <section id="about" className="site-section">
          <p className="kicker">About</p>
          <h2>{name}</h2>
          <TypeLine text={school?.aboutText || DEFAULT_ABOUT} className={school?.aboutText ? undefined : "sample-copy"} />
        </section>
      </Reveal>

      {showPrincipal ? (
        <Reveal>
          <section id="principal" className="site-section alt site-principal branch-swap" key={campus?.id || "principal"}>
            <PrincipalGallery photos={photos.items} name={principalName} sample={photos.isDefault} branch={campus?.name} />
            <div className="principal-copy">
              <p className="kicker">{principalDefault ? `Sample · ${campus?.name || "campus"}` : campus?.name || "Campus"}</p>
              <h2>Principal’s Message</h2>
              <p className="principal-meta">{principalName} · {principalTitle}</p>
              {principalMessage.split(/\n+/).map((p, i) => (
                i === 0
                  ? <TypeLine key={`${campus?.id || "p"}-${i}`} text={p} className={principalDefault ? "sample-copy" : undefined} />
                  : <p key={i} className={principalDefault ? "sample-copy" : undefined}>{p}</p>
              ))}
            </div>
          </section>
        </Reveal>
      ) : null}

      <Reveal>
        <section id="academics" className="site-section">
          <p className="kicker">Academics</p>
          <h2>Programmes</h2>
          {programs.isDefault ? <p className="sample-copy">Sample programmes rotate here until School Admin publishes real copy. Separate programmes with a blank line in Website settings.</p> : null}
          <ProgramsBlock items={programs.items} sample={programs.isDefault} />
        </section>
      </Reveal>

      <Reveal>
        <section id="highlights" className="site-section site-holders">
          <p className="kicker">Position holders</p>
          <h2><span className="pulse-hl">Highlights</span></h2>
          {highlights.isDefault ? <p className="sample-copy">Sample honours until School Admin publishes real names from Website settings.</p> : null}
          <div className="hl-ticker" tabIndex={0} aria-label="Achievement ticker. Hover or focus to pause.">
            <div className="hl-track">
              {[...highlights.items, ...highlights.items].map((line, i) => (
                <article key={line + i} className={`holder-card ticker-item ${highlights.isDefault ? "sample" : ""}`}>
                  <span className="holder-index">{String((i % highlights.items.length) + 1).padStart(2, "0")}</span>
                  <strong>{line}</strong>
                </article>
              ))}
            </div>
          </div>
        </section>
      </Reveal>

      <Reveal>
        <section id="gallery" className="site-section alt">
          <p className="kicker">Gallery</p>
          <h2>Life at {name}</h2>
          {gallery.isDefault ? <p className="sample-copy">Starter photographs are shown until School Admin uploads the school’s own gallery.</p> : null}
          <div className="site-gallery">
            {gallery.items.map((g, i) => (
              <figure key={g.imageUrl + i}>
                <img src={g.imageUrl} alt={g.caption || "School life"} loading="lazy" />
                {g.caption ? <figcaption>{g.caption}</figcaption> : null}
              </figure>
            ))}
          </div>
        </section>
      </Reveal>

      <Reveal>
        <section id="campuses" className="site-section">
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
      </Reveal>

      {yt || (videoUrl && !yt) ? (
        <Reveal>
          <section className="site-section alt" id="media">
            <p className="kicker">Watch</p>
            <h2>{noticeTitle || "School film"}</h2>
            <div className="site-video">
              {yt ? (
                <iframe
                  title="School video"
                  src={`https://www.youtube.com/embed/${yt}`}
                  allow="accelerometer; clipboard-write; encrypted-media; gyroscope; picture-in-picture"
                  allowFullScreen
                />
              ) : (
                <video controls preload="metadata" poster={school?.notice?.posterUrl || undefined} src={videoUrl} />
              )}
            </div>
          </section>
        </Reveal>
      ) : null}

      <Reveal>
        <section id="contact" className="site-section site-contact">
          <div>
            <p className="kicker">Contact us</p>
            <h2>{campus?.name || name}</h2>
            {hasContact ? (
              <>
                {contact.address || contact.city ? <p>{[contact.address, contact.city].filter(Boolean).join(" · ")}</p> : <p className="sample-copy">Address will appear here once published in website settings.</p>}
                <p>
                  {contact.phone ? <a href={`tel:${contact.phone}`}>{contact.phone}</a> : <span className="sample-copy">Phone unpublished</span>}
                  {contact.email ? <> · <a href={`mailto:${contact.email}`}>{contact.email}</a></> : null}
                </p>
              </>
            ) : (
              <p className="sample-copy">Phone, email and address appear here after School Admin publishes them. Nothing below is an invented school contact.</p>
            )}
            <div className="site-social">
              <SocialLink href={contact.facebook} label="Facebook" network="facebook">
                <svg viewBox="0 0 24 24" width="18" height="18" aria-hidden><path fill="currentColor" d="M13.5 9H16V6h-2.5C11.6 6 11 7.1 11 8.8V9H9v3h2v6h3v-6h2.2l.3-3H14V8.9c0-.5.2-.9.5-.9z" /></svg>
              </SocialLink>
              <SocialLink href={contact.instagram} label="Instagram" network="instagram">
                <svg viewBox="0 0 24 24" width="18" height="18" aria-hidden><path fill="currentColor" d="M8 4h8a4 4 0 0 1 4 4v8a4 4 0 0 1-4 4H8a4 4 0 0 1-4-4V8a4 4 0 0 1 4-4zm8 1.8H8A2.2 2.2 0 0 0 5.8 8v8A2.2 2.2 0 0 0 8 18.2h8A2.2 2.2 0 0 0 18.2 16V8A2.2 2.2 0 0 0 16 5.8zM12 8.5A3.5 3.5 0 1 1 8.5 12 3.5 3.5 0 0 1 12 8.5zm0 1.7A1.8 1.8 0 1 0 13.8 12 1.8 1.8 0 0 0 12 10.2zM16.7 7.4a.9.9 0 1 1-.9.9.9.9 0 0 1 .9-.9z" /></svg>
              </SocialLink>
              <SocialLink href={contact.youtube} label="YouTube" network="youtube">
                <svg viewBox="0 0 24 24" width="18" height="18" aria-hidden><path fill="currentColor" d="M22 12.2s0-3.2-.4-4.6a2.9 2.9 0 0 0-2-2C18 5.2 12 5.2 12 5.2s-6 0-7.6.4a2.9 2.9 0 0 0-2 2C2 9 2 12.2 2 12.2s0 3.2.4 4.6a2.9 2.9 0 0 0 2 2c1.6.4 7.6.4 7.6.4s6 0 7.6-.4a2.9 2.9 0 0 0 2-2c.4-1.4.4-4.6.4-4.6zM10 15.5v-6.6l5.5 3.3z" /></svg>
              </SocialLink>
              <SocialLink href={contact.twitter} label="X" network="twitter">
                <svg viewBox="0 0 24 24" width="18" height="18" aria-hidden><path fill="currentColor" d="M14.7 10.5 21 3h-2.2l-5 5.9L9.7 3H3l6.7 10.1L3 21h2.2l5.5-6.5 4.4 6.5H21l-6.3-10.5zm-1.9 2.3-.6-.9L6.4 4.5h2.2l4.7 6.9.6.9 6.2 9.2h-2.2l-5.1-7.7z" /></svg>
              </SocialLink>
              <SocialLink href={contact.linkedin} label="LinkedIn" network="linkedin">
                <svg viewBox="0 0 24 24" width="18" height="18" aria-hidden><path fill="currentColor" d="M6.5 9H4v11h2.5zm.2-3.8A1.7 1.7 0 1 0 5 6.9a1.7 1.7 0 0 0 1.7-1.7zM20 20h-2.5v-5.6c0-1.6-.6-2.2-1.6-2.2s-1.7.9-1.7 2.3V20H12V9h2.4v1.5a2.8 2.8 0 0 1 2.5-1.6c1.9 0 3.1 1.2 3.1 3.8z" /></svg>
              </SocialLink>
              <SocialLink href={contact.whatsapp} label="WhatsApp" network="whatsapp">
                <svg viewBox="0 0 24 24" width="18" height="18" aria-hidden><path fill="currentColor" d="M12.04 2a10 10 0 0 0-8.6 15.1L2 22l5.05-1.4A10 10 0 1 0 12.04 2zm5.84 14.25c-.24.68-1.4 1.26-1.94 1.3-.5.04-1.12.06-1.8-.11a16.4 16.4 0 0 1-7.2-6.3 4.3 4.3 0 0 1-.9-2.28c0-1.27.66-1.9 1.02-2.16.3-.22.78-.32 1.25-.01.16.1.36.38.47.66.12.3.24.6.26.64.12.24.02.5-.1.7l-.38.58c-.12.18-.26.3-.1.58a8.2 8.2 0 0 0 1.5 1.86 7.4 7.4 0 0 0 2.16 1.4c.28.12.44.1.6-.08l.7-1c.16-.22.4-.18.66-.1.26.08 1.64.77 1.92.91.28.14.46.22.54.34.08.14.08.78-.16 1.46z" /></svg>
              </SocialLink>
            </div>
            {hasCoords || mapQuery ? (
              <div className="site-map">
                <iframe
                  title="School map"
                  src={hasCoords ? osmEmbed(lat, lng) : mapsEmbedUrl(mapQuery)}
                  loading="lazy"
                  referrerPolicy="no-referrer-when-downgrade"
                />
                <a
                  className="btn brass sm"
                  href={hasCoords ? `https://www.openstreetmap.org/?mlat=${lat}&mlon=${lng}#map=16/${lat}/${lng}` : mapsSearchUrl(mapQuery)}
                  target="_blank"
                  rel="noopener noreferrer"
                >
                  Show on Map
                </a>
              </div>
            ) : (
              <p className="sample-copy">A map appears here after coordinates or an address are published. No location is invented.</p>
            )}
          </div>
          <div className="card site-form">
            <h3>Write to us</h3>
            <p className="hint">Messages are stored for the school. Do not send passwords or payment details.</p>
            <ContactForm />
          </div>
        </section>
      </Reveal>

      <section className="site-cta">
        <div>
          <p className="kicker glow-kicker">Portals</p>
          <h2>Sign in to {name}</h2>
          <p>Students, parents and staff use the school portal. Public pages stay on this website.</p>
        </div>
        <div className="row">
          <Link to="/portals" className="btn brass">School Portal</Link>
          {school?.admissions?.enabled ? (
            <Link to="/apply" className="btn ghost">{school.admissions.ctaLabel || "Apply for Admission"}</Link>
          ) : null}
          <a href="#contact" className="btn ghost">Contact the school</a>
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
        <nav className="site-foot-links">
          <a href="#about">About</a>
          <a href="#principal">Principal</a>
          <a href="#contact">Contact</a>
          <Link to="/portals" className="btn brass sm">Login</Link>
        </nav>
      </footer>
      <WhatsAppFloat href={contact.whatsapp} />
    </div>
  );
}

function PrincipalGallery({ photos, name, sample, branch }: { photos: PublicMedia[]; name: string; sample?: boolean; branch?: string }) {
  const [i, setI] = useState(0);
  const sig = photos.map((p) => p.imageUrl).join("|");
  useEffect(() => { setI(0); }, [sig]);
  useEffect(() => {
    if (photos.length < 2) return;
    const id = window.setInterval(() => setI((n) => (n + 1) % photos.length), 4000);
    return () => window.clearInterval(id);
  }, [sig, photos.length]);
  const current = photos[i] || photos[0];
  return (
    <div className="principal-photo">
      <div className="principal-slides">
        {photos.map((p, n) => (
          <img key={`${branch || ""}-${p.imageUrl}-${n}`} className={n === i ? "on" : ""} src={p.imageUrl} alt={p.caption || name} />
        ))}
      </div>
      {photos.length > 1 ? (
        <div className="principal-dots">
          {photos.map((_, n) => (
            <button key={n} type="button" className={n === i ? "on" : ""} aria-label={`Principal photograph ${n + 1}`} onClick={() => setI(n)} />
          ))}
        </div>
      ) : null}
      <div>
        <strong>{name}</strong>
        <span>{current?.caption || (sample ? `Sample for ${branch || "this branch"}` : branch || "Principal")}</span>
      </div>
    </div>
  );
}

function ProgramsBlock({ items, sample }: { items: { title: string; body: string }[]; sample?: boolean }) {
  const [i, setI] = useState(0);
  useEffect(() => {
    if (items.length < 2) return;
    const id = window.setInterval(() => setI((n) => (n + 1) % items.length), 4200);
    return () => window.clearInterval(id);
  }, [items.length]);
  const cur = items[i] || items[0];
  if (!cur) return null;
  return (
    <div className="site-programs">
      <div className="program-tabs">
        {items.map((p, n) => (
          <button type="button" key={p.title + n} className={n === i ? "on" : ""} onClick={() => setI(n)}>
            {p.title}
          </button>
        ))}
      </div>
      <div className="program-copy">
        <h3>{cur.title}</h3>
        <TypeLine key={`${cur.title}-${i}`} text={cur.body} className={sample ? "sample-copy" : undefined} />
      </div>
    </div>
  );
}

function ContactForm() {
  const [name, setName] = useState("");
  const [email, setEmail] = useState("");
  const [phone, setPhone] = useState("");
  const [message, setMessage] = useState("");
  const [company, setCompany] = useState("");
  const [done, setDone] = useState("");
  return (
    <Form busyLabel="Sending…" onSubmit={async () => {
      const res = await publicApi.contact({ name, email, phone, message, company });
      setDone(res.message || "Thank you. The school has received your message.");
      setName(""); setEmail(""); setPhone(""); setMessage("");
    }}>
      <label className="hp" aria-hidden>
        Company <input value={company} onChange={(e) => setCompany(e.target.value)} tabIndex={-1} autoComplete="off" />
      </label>
      <Field label="Your name"><input value={name} onChange={(e) => setName(e.target.value)} required /></Field>
      <Field label="Email"><input type="email" value={email} onChange={(e) => setEmail(e.target.value)} required /></Field>
      <Field label="Phone (optional)"><input value={phone} onChange={(e) => setPhone(e.target.value)} /></Field>
      <Field label="Message"><textarea rows={4} value={message} onChange={(e) => setMessage(e.target.value)} required /></Field>
      {done ? <p className="hint">{done}</p> : null}
      <Button type="submit" kind="brass" loadingText="Sending…">Send message</Button>
    </Form>
  );
}
