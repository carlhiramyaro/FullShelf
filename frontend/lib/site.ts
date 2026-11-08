// The public address, for absolute URLs in metadata, robots.txt and the
// sitemap. Baked in at build time like the other NEXT_PUBLIC_ values.
export const SITE_URL = process.env.NEXT_PUBLIC_SITE_URL ?? "https://fullshelf.app";

// Where a shop owner reaches me: one number, for calls and WhatsApp.
export const CONTACT_PHONE_DISPLAY = "+233 24 720 7555";
export const CONTACT_TEL_URL = "tel:+233247207555";
export const CONTACT_WHATSAPP_URL =
  "https://wa.me/233247207555?text=" +
  encodeURIComponent("Hi, I'd like to set up FullShelf for my shop.");
