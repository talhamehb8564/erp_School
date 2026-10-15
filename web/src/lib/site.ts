import type { PublicCampus, PublicMedia, PublicSchool } from "./branding";

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

export const DEFAULT_PRINCIPAL_MESSAGE =
  "Welcome. This is a sample Principal’s message so the public website looks complete before your school publishes its own words.\n\nReplace the portrait and this text from the Principal portal. Until then, visitors will see this clearly marked sample — it is not an official statement from the school.";

export const DEFAULT_ABOUT =
  "This is sample About copy. School Admin can publish the school’s real story, values and history from Website settings. Until then this paragraph is a placeholder, not verified school information.";

export const DEFAULT_ACADEMICS =
  "This is sample Academics copy. Describe your programmes, year groups and teaching approach in Website settings. This placeholder is not an official prospectus.";

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
