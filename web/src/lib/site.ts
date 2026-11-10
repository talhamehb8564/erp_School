import type { PublicCampus, PublicMap, PublicMedia, PublicPrincipal, PublicSchool } from "./branding";

export const DEFAULT_HEROES: PublicMedia[] = [
  { imageUrl: "/website/hero-campus.jpg", title: "A place to belong", caption: "Sample campus photograph — replace from Website settings." },
  { imageUrl: "/website/hero-classroom.jpg", title: "Classrooms that teach well", caption: "Sample classroom photograph — replace from Website settings." },
  { imageUrl: "/website/hero-lab.jpg", title: "Science in practice", caption: "Sample laboratory photograph — replace from Website settings." },
  { imageUrl: "/website/hero-sports.jpg", title: "Sport and character", caption: "Sample sports photograph — replace from Website settings." },
  { imageUrl: "/website/hero-computers.jpg", title: "Ready for a digital world", caption: "Sample computer lab photograph — replace from Website settings." },
];

export const DEFAULT_GALLERY: PublicMedia[] = [
  { imageUrl: "/website/gallery-library.jpg", caption: "Sample library photograph" },
  { imageUrl: "/website/gallery-event.jpg", caption: "Sample school event photograph" },
  { imageUrl: "/website/hero-classroom.jpg", caption: "Sample classroom photograph" },
  { imageUrl: "/website/hero-lab.jpg", caption: "Sample science laboratory photograph" },
  { imageUrl: "/website/hero-sports.jpg", caption: "Sample sports photograph" },
  { imageUrl: "/website/hero-computers.jpg", caption: "Sample computer lab photograph" },
];

export const DEFAULT_PRINCIPAL_PHOTO = "/website/principal.jpg";

export const DEFAULT_PRINCIPAL_PHOTOS: PublicMedia[] = [
  { imageUrl: "/website/principal.jpg", caption: "Sample portrait — replace from the Principal portal for this branch." },
  { imageUrl: "/website/principal-campus.jpg", caption: "Sample campus photograph — replace with this branch’s Principal." },
  { imageUrl: "/website/principal-event.jpg", caption: "Sample event photograph — replace with this branch’s Principal." },
  { imageUrl: "/website/principal-students.jpg", caption: "Sample classroom photograph — replace with this branch’s Principal." },
];

export const DEFAULT_PRINCIPAL_MESSAGE =
  "Welcome. This is a sample Principal’s message so the public website looks complete before this branch publishes its own words.\n\nEach campus has its own Principal profile. Replace the photographs and this text from the Principal portal. Until then, visitors will see this clearly marked sample — it is not an official statement from the school.";

export const DEFAULT_ABOUT =
  "This is sample About copy. School Admin can publish the school’s real story, values and history from Website settings. Until then this paragraph is a placeholder, not verified school information.";

export const DEFAULT_ACADEMICS =
  "This is sample Academics copy. Describe your programmes, year groups and teaching approach in Website settings. This placeholder is not an official prospectus.";

export const DEFAULT_PROGRAMS: { title: string; body: string }[] = [
  { title: "Early years", body: "Sample programme: a calm start to school with play, language and the first steps in reading and number. Replace this from Website settings — it is not an official prospectus." },
  { title: "Primary", body: "Sample programme: a broad primary curriculum with literacy, mathematics, science and the arts. Publish your real year groups from Website settings." },
  { title: "Secondary", body: "Sample programme: subject specialists, laboratories and preparation for board examinations. This placeholder is not a verified offering." },
  { title: "Enrichment", body: "Sample programme: sport, music, clubs and community. Describe your real co-curricular life from Website settings." },
];

export const DEFAULT_ACHIEVEMENTS =
  "This is sample Highlights copy. Publish real results, awards and student work from Website settings. Nothing here is a verified achievement until you replace it.";

export const DEFAULT_NOTICE_TITLE = "School notice";
export const DEFAULT_NOTICE_BODY =
  "Important notices will appear here once School Admin publishes them. This banner is a sample layout only.";

export function heroesFor(campus: PublicCampus | null, school: PublicSchool | null): PublicMedia[] {
  const custom = (campus?.hero || []).filter((h) => h.imageUrl);
  if (custom.length) return custom;
  const name = school?.name || "Our school";
  return DEFAULT_HEROES.map((h, i) => ({
    ...h,
    title: i === 0 ? name : h.title,
    caption: school?.tagline || h.caption,
  }));
}

export function galleryFor(school: PublicSchool | null): { items: PublicMedia[]; isDefault: boolean } {
  const custom = (school?.gallery || []).filter((g) => g.imageUrl);
  if (custom.length) return { items: custom, isDefault: false };
  return { items: DEFAULT_GALLERY, isDefault: true };
}

export function programsFor(school: PublicSchool | null): { items: { title: string; body: string }[]; isDefault: boolean } {
  const raw = school?.academicsText?.trim();
  if (!raw) return { items: DEFAULT_PROGRAMS, isDefault: true };
  const blocks = raw.split(/\n{2,}/).map((block) => block.trim()).filter(Boolean);
  const items = blocks.map((block, i) => {
    const lines = block.split("\n");
    if (lines.length > 1 && lines[0].trim().length <= 48) {
      return { title: lines[0].trim(), body: lines.slice(1).join(" ").trim() };
    }
    return { title: blocks.length === 1 ? "Academics" : `Programme ${i + 1}`, body: block };
  }).filter((p) => p.body);
  if (!items.length) return { items: DEFAULT_PROGRAMS, isDefault: true };
  return { items, isDefault: false };
}

function samplePrincipalPhoto(campus: PublicCampus | null): PublicMedia {
  const branch = campus?.name || "this branch";
  const key = campus?.id || campus?.code || branch;
  let h = 0;
  for (let i = 0; i < key.length; i += 1) h = (Math.imul(31, h) + key.charCodeAt(i)) | 0;
  const pick = DEFAULT_PRINCIPAL_PHOTOS[Math.abs(h) % DEFAULT_PRINCIPAL_PHOTOS.length];
  return {
    imageUrl: pick.imageUrl,
    caption: `Sample portrait for ${branch} — replace from the Principal portal.`,
  };
}

function isStockPrincipalPhoto(url?: string) {
  if (!url) return true;
  return url === DEFAULT_PRINCIPAL_PHOTO || DEFAULT_PRINCIPAL_PHOTOS.some((p) => p.imageUrl === url);
}

export function principalFor(campus: PublicCampus | null, _school: PublicSchool | null): PublicPrincipal | null {
  const branch = campus?.name || "this campus";
  const campusP = campus?.principal;
  const sample = samplePrincipalPhoto(campus);
  if (campusP && campusP.published === false && !campusP.isDefault) return null;
  if (campusP && !campusP.isDefault) {
    const photos = (campusP.photos || []).filter((p) => p.imageUrl && !isStockPrincipalPhoto(p.imageUrl));
    const photoUrl = campusP.photoUrl && !isStockPrincipalPhoto(campusP.photoUrl)
      ? campusP.photoUrl
      : photos[0]?.imageUrl || sample.imageUrl;
    return {
      ...campusP,
      title: campusP.title || `Principal, ${branch}`,
      photoUrl,
      photos: photos.length ? photos : [sample],
    };
  }
  return {
    name: campusP?.name || `Principal · ${branch}`,
    title: campusP?.title || `Principal, ${branch}`,
    photoUrl: sample.imageUrl,
    photos: [sample],
    message: `This is a sample Principal’s message for ${branch} only.\n\nEach branch has its own Principal. Image, name and words on this page always follow the campus you selected. Publish ${branch}’s profile from the Principal portal — this placeholder is not an official statement.`,
    published: true,
    isDefault: true,
  };
}

export function principalPhotos(principal: PublicPrincipal | null, campus?: PublicCampus | null | string): { items: PublicMedia[]; isDefault: boolean } {
  const campusObj = typeof campus === "object" && campus ? campus : null;
  const branch = (typeof campus === "string" ? campus : campusObj?.name) || "this branch";
  const custom = [
    ...(principal?.photos || []),
    ...(principal?.photoUrl ? [{ imageUrl: principal.photoUrl, caption: principal.photos?.[0]?.caption }] : []),
  ].filter((p, i, all) => p.imageUrl && !isStockPrincipalPhoto(p.imageUrl) && all.findIndex((x) => x.imageUrl === p.imageUrl) === i);
  if (custom.length) return { items: custom, isDefault: false };
  const sample = samplePrincipalPhoto(campusObj || (typeof campus === "string" ? { name: campus } as PublicCampus : null));
  return {
    items: [{ imageUrl: sample.imageUrl, caption: sample.caption || `Sample portrait for ${branch} — not a carousel of other campuses.` }],
    isDefault: true,
  };
}

export function highlightsFor(school: PublicSchool | null): { items: string[]; isDefault: boolean } {
  const raw = school?.achievementsText?.trim();
  if (!raw) {
    return {
      items: [
        "Sample position holder — replace with a real name from Website settings.",
        "Sample achievement — publish board results or awards for this school.",
        "Sample highlight — this card is a layout placeholder, not a verified honour.",
      ],
      isDefault: true,
    };
  }
  const items = raw.split(/\n+/).map((s) => s.trim()).filter(Boolean);
  return { items: items.length ? items : [raw], isDefault: false };
}

export function contactFor(campus: PublicCampus | null, school: PublicSchool | null) {
  return {
    campusName: campus?.name,
    email: campus?.email || school?.email,
    phone: campus?.phone || school?.phone,
    address: campus?.address || school?.addressLine,
    city: campus?.city || school?.city,
    facebook: campus?.facebookUrl || school?.facebookUrl,
    instagram: campus?.instagramUrl || school?.instagramUrl,
    youtube: campus?.youtubeUrl || school?.youtubeUrl,
    twitter: campus?.twitterUrl || school?.twitterUrl,
    linkedin: school?.linkedinUrl,
    whatsapp: campus?.whatsappUrl || school?.whatsappUrl,
  };
}

export function mapOf(campus: PublicCampus | null, school: PublicSchool | null): PublicMap {
  const c = campus?.map;
  const s = school?.map;
  const lat = c?.lat ?? s?.lat;
  const lng = c?.lng ?? s?.lng;
  const place = c?.place || s?.place;
  return { lat, lng, place };
}

export function youtubeId(url?: string | null) {
  if (!url) return null;
  const m = url.match(/(?:youtu\.be\/|youtube\.com\/(?:watch\?v=|embed\/|shorts\/))([\w-]{11})/);
  return m ? m[1] : null;
}

export function mapsSearchUrl(query: string) {
  return `https://www.google.com/maps/search/?api=1&query=${encodeURIComponent(query)}`;
}

export function osmEmbed(lat: number, lng: number) {
  const d = 0.012;
  return `https://www.openstreetmap.org/export/embed.html?bbox=${lng - d}%2C${lat - d}%2C${lng + d}%2C${lat + d}&layer=mapnik&marker=${lat}%2C${lng}`;
}

export function mapsEmbedUrl(query: string) {
  return `https://maps.google.com/maps?q=${encodeURIComponent(query)}&z=16&output=embed`;
}
