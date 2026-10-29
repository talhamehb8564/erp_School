import type { ReactNode } from "react";
import { contactFor } from "../lib/site";
import type { PublicCampus, PublicSchool } from "../lib/branding";

export function SocialLink({ href, label, network, children }: { href?: string; label: string; network: string; children: ReactNode }) {
  if (!href) return null;
  return (
    <a className={`soc ${network}`} href={href} target="_blank" rel="noopener noreferrer" aria-label={label}>
      {children}
      <span>{label}</span>
    </a>
  );
}

export function CampusContact({
  campus,
  school,
  compact,
}: {
  campus: PublicCampus | null;
  school: PublicSchool | null;
  compact?: boolean;
}) {
  const contact = contactFor(campus, school);
  const hasContact = Boolean(contact.phone || contact.email || contact.address);
  return (
    <div className={`campus-contact ${compact ? "compact" : ""}`}>
      <p className="kicker">Need help? Contact us</p>
      <h3>{contact.campusName || school?.name || "Campus"}</h3>
      {hasContact ? (
        <>
          {contact.address || contact.city ? <p>{[contact.address, contact.city].filter(Boolean).join(" · ")}</p> : null}
          <p>
            {contact.phone ? <a href={`tel:${contact.phone}`}>{contact.phone}</a> : <span className="sample-copy">Phone unpublished</span>}
            {contact.email ? <> · <a href={`mailto:${contact.email}`}>{contact.email}</a></> : null}
          </p>
        </>
      ) : (
        <p className="sample-copy">Phone, email and address appear here after this campus publishes them.</p>
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
          <WaIcon />
        </SocialLink>
      </div>
    </div>
  );
}

function WaIcon() {
  return (
    <svg viewBox="0 0 24 24" width="18" height="18" aria-hidden>
      <path fill="currentColor" d="M12.04 2a10 10 0 0 0-8.6 15.1L2 22l5.05-1.4A10 10 0 1 0 12.04 2zm5.84 14.25c-.24.68-1.4 1.26-1.94 1.3-.5.04-1.12.06-1.8-.11a16.4 16.4 0 0 1-7.2-6.3 4.3 4.3 0 0 1-.9-2.28c0-1.27.66-1.9 1.02-2.16.3-.22.78-.32 1.25-.01.16.1.36.38.47.66.12.3.24.6.26.64.12.24.02.5-.1.7l-.38.58c-.12.18-.26.3-.1.58a8.2 8.2 0 0 0 1.5 1.86 7.4 7.4 0 0 0 2.16 1.4c.28.12.44.1.6-.08l.7-1c.16-.22.4-.18.66-.1.26.08 1.64.77 1.92.91.28.14.46.22.54.34.08.14.08.78-.16 1.46z" />
    </svg>
  );
}

export function WhatsAppFloat({ href }: { href?: string }) {
  if (!href) return null;
  return (
    <a className="wa-float" href={href} target="_blank" rel="noopener noreferrer" aria-label="Chat on WhatsApp">
      <WaIcon />
    </a>
  );
}
