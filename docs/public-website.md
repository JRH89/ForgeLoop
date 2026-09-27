# Public website and SEO

The public site is generated from React at build time. Search and social crawlers
receive complete HTML without executing JavaScript. The authenticated console
remains a separate client-loaded route and is marked noindex. No tenant GraphQL
requests are made by public pages.

The landing hero uses compact 24px top padding at widths up to 1000px (matching the mobile navigation breakpoint), while
desktop retains its 110px top padding. Browser regression tests measure the
header-to-eyebrow gap at 320, 390, 430, 760, 768, and 1000px to prevent excess mobile whitespace.
On phones the footer ends with 20px padding and no trailing paragraph margin;
browser checks verify that closing gap as well.

## Content and routes

- The landing page features four field notes in a two-column desktop grid
  (one column on phones); the blog index continues to show all articles.

- Landing capability cards use decorative Lucide icons (repository branch,
  delivery loop, verification shield, runner server), with headings providing
  their accessible meaning instead of numbered step labels.

- Existing landing-page headlines, paragraphs, capability descriptions, and CTAs
  are retained. Additional explanatory sections and FAQs follow the original copy.
- `/about`, `/features`, `/how-it-works`, `/security`, `/docs`, `/contact`, `/blog`.
- Ten original engineering articles live in `frontend/src/public/articles.ts`.
  Publication dates reflect the actual publication date, not invented history.
  No fabricated customer stories, traffic claims, benchmarks, reviews, or ratings.
- Shared header/footer links cover desktop and mobile. Every public page and the
  404 page has a decorative image-backed hero. Informational text remains HTML.
- Each page and article now has unique artwork, responsive derivatives, and a
  matching social preview. See [hero prompts and asset map](public-hero-prompts.md).
- All hero templates (including 404) apply a dark navy overlay below 1440px,
  strongest behind copy and strengthened again on phones. At 1440px and above
  the overlay is transparent. Browser tests cover both breakpoint edges and
  mobile/tablet widths without changing copy or artwork.
- Article cards are whole-card links, including the visible read action; browser
  tests open every article through that action. The decorative pipeline status
  strip has been removed. Customer-facing pages and the guide never name internal
  demonstration repositories; generated public HTML is checked for regressions.
- Header/footer span the viewport; desktop navigation uses equal outer columns
  to stay centered. Mobile navigation matches the console's full-width dropdown,
  icon tiles, outlined sign-in button and 200ms vertical slide/fade. The header
  toggle, Escape, outside clicks and leaving with Tab close the non-modal panel;
  closed links are inert. Escape restores focus. Reduced motion removes animation.
  No-JavaScript visitors retain ordinary links. Desktop resizing closes the panel.
- Public docs distinguish desktop previews from signed distribution and paid
  model execution from free setup checks. The console still contains the full guide.

## Metadata contract

`frontend/src/public/seo.ts` is the shared metadata registry. The production build
creates unique titles (15–60 characters) and descriptions (90–160 characters),
canonical HTTPS URLs, Open Graph and Twitter large-image tags, and JSON-LD:
Organization, WebSite, WebPage/collection/about/contact, BreadcrumbList,
SoftwareApplication on the homepage, and BlogPosting on article pages. Unsupported
price/review claims are deliberately omitted; SoftwareApplication markup alone
does not guarantee eligibility for Google's software-app rich result.

Google truncates based on display width and can rewrite titles/snippets; character
budgets are editorial checks, not a guarantee of exact search appearance or rank.
References: [titles](https://developers.google.com/search/docs/appearance/title-link),
[snippets](https://developers.google.com/search/docs/appearance/snippet),
[article markup](https://developers.google.com/search/docs/appearance/structured-data/article).

`sitemap.xml` contains public canonical pages only. Article dates are stable;
rebuilding does not invent a new modification date. `robots.txt` excludes API,
authentication, and download paths. App and error HTML use noindex; app access
still requires the existing authorization checks. Unknown public URLs return an
actual HTTP 404; trailing slashes redirect to the canonical path.

## Build and verification

Run `npm ci` then `npm run check` in `frontend`. The build creates responsive image
derivatives, client bundles, an SSR build in ignored `.prerender`, static HTML,
sitemap/robots, then validates all page metadata, schema JSON, links, heading
counts, content presence, ten-article count, and image budget. Public visitors do
not download the operator console chunk until entering `/app`.

Run `PLAYWRIGHT_BASE_URL=<nginx deployment> npx playwright test e2e/public-site.spec.ts`
with the environment variable syntax for your shell. These tests require the
production Nginx route behavior, not Vite's development fallback. They check all
pages with JavaScript disabled, navigation/hydration, mobile overflow, real 404s,
assets and redirects, plus automated WCAG A/AA accessibility checks. Existing
dashboard/pairing tests remain part of full CI. Public production pages use native
HTML navigation/disclosures and a tiny bootstrap rather than hydrating React;
React runs during static generation and loads on demand for the operator console.
Inter is self-hosted with its license, avoiding third-party font requests.

After deploying, validate canonical URLs and social images through the public
tunnel. Search Console ownership, sitemap submission, indexing coverage, real
field Core Web Vitals, and external share-preview refreshes require owner/provider
access or actual traffic. Never claim these are completed merely because a local
audit passes. Do not invent a verification token or add analytics without consent.

## Verification snapshot (2026-09-26)

- Frontend lint, type checking, 21 unit tests, production build and 18-page SEO
  contract checks pass.
- Eight public-site browser tests pass against the production Nginx image, including
  JavaScript-disabled content, mobile navigation, real 404/redirect behavior and
  automated WCAG A/AA checks on representative templates.
- Lighthouse 13.5.0 mobile simulation against the local production image scored
  100 performance, 100 accessibility, 100 best practices and 100 SEO; LCP was 1.4s.
  These are local lab results, not live field metrics or a search-ranking promise.
  Report: `evidence/public-site/lighthouse-home.json` (local evidence, not committed).

## Hero artwork provenance

The homepage now uses the [reference-inspired refined hero](landing-hero-refinement.md),
with versioned URLs and higher-quality encoding. The original source below is
retained for history; all other page illustrations remain unchanged.

Built-in image generation was used, not the paid provider key configured on the
ForgeLoop runner. Original: `frontend/src/assets/delivery-hero-generated.png`.
Web assets: `frontend/public/images/delivery-hero.webp`,
`delivery-hero-small.webp`, and `social-preview.jpg`. Encoding/resizing and favicon
derivatives are reproducible with `scripts/prepare-images.mjs` (Sharp). Existing
reference `landing_page_hero_example.png` remains unchanged. ResearchOS was viewed
as a layout reference only; no artwork or copy was copied from that website.

Final generation prompt:

> Use case: ads-marketing. Generate a premium software website hero BACKGROUND,
> not a full website mockup. Very wide landscape 2400x1200 composition. ForgeLoop
> autonomous software delivery visual language: deep midnight navy #071019,
> slate-blue translucent panels, restrained electric-blue and mint highlights.
> The LEFT 58 percent must be almost empty dark navy with a subtle atmospheric
> gradient, absolutely no objects, so real HTML headings can sit there. Concentrate
> the artwork on the RIGHT 42 percent: an elegant layered stack of three
> translucent glass workflow cards connected by thin blue light paths, suggesting
> specification, parallel coding agents, and a verified pull request. Small
> abstract lines, status dots, a simple check mark, no readable text or numbers.
> Slight three-dimensional perspective, sophisticated soft lighting, crisp edges,
> understated depth, no noisy particles. Cards fully visible inside safe margins.
> Inspired by the supplied visual concept but produce original artwork. No
> headline, no buttons, no logo, no watermark, no text anywhere. A background image
> suitable behind accessible HTML text.
