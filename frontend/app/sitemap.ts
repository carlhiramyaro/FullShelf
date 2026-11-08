import type { MetadataRoute } from "next";
import { SITE_URL } from "@/lib/site";

// One public page. Sign-in is left out on purpose: it has nothing to index.
export default function sitemap(): MetadataRoute.Sitemap {
  return [{ url: SITE_URL, changeFrequency: "monthly", priority: 1 }];
}
