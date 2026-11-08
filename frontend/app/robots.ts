import type { MetadataRoute } from "next";
import { SITE_URL } from "@/lib/site";

// Everything behind a login or a device token has nothing for a crawler.
export default function robots(): MetadataRoute.Robots {
  return {
    rules: {
      userAgent: "*",
      allow: "/",
      disallow: ["/owner", "/admin", "/shop", "/no-shop", "/api"],
    },
    sitemap: `${SITE_URL}/sitemap.xml`,
  };
}
