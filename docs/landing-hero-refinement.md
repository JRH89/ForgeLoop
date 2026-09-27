## Landing hero refinement

The current homepage uses `frontend/src/assets/delivery-hero-v2.png`, generated
with the built-in image tool using `landing_page_hero_example.png` as a style and
composition reference. The original artwork remains available. Versioned
`delivery-v2-hero.webp`, `delivery-v2-hero-small.webp`, and
`delivery-v2-social-preview.jpg` avoid stale image caches. The desktop derivative
preserves the generated source resolution with quality-92 WebP encoding. Page
copy, layout, and responsive overlays are unchanged. The cards are decorative
workflow illustrations, not live run statuses.

The landing hero has 20px additional top and bottom spacing: 110px/85px on
desktop and 75px/65px on phones. Desktop minimum height is 630px; phone height
remains content-driven rather than forcing a full-screen section.

Final prompt:

Use case: ads-marketing. Create a high-quality original website HERO BACKGROUND inspired closely by the supplied reference image (style/composition reference, NOT an edit target). Wide 2:1 landscape, ideally 3072x1536. Keep the LEFT 58% completely empty midnight navy #071019 for existing live HTML heading and buttons: DO NOT reproduce its heading, paragraph, logo, or buttons. On the RIGHT 42%, show three slim premium translucent dark-blue glass UI cards stacked vertically like the reference, slight perspective but almost front facing, subtle thin blue borders and layered ghost panels behind. Top short card: document icon and tasteful simple label 'Specification'. Middle taller card: label 'Running Agents', four clean rows with tiny mint status lights and labels 'Backend agent', 'Frontend agent', 'Test agent', 'Verification agent'. Bottom card: mint circled check and label 'Pull Request Ready', small line 'Ready for review'. These are decorative conceptual workflow cards, not a screenshot. No large robot icons, no thick tubes, no bulky 3D machines, no sci-fi clutter. Razor sharp edges and readable crisp typography, smooth deep navy gradients, restrained blue rim light, restrained mint highlights, high-end detailed rendering consistent with premium glass product illustrations. Keep all three cards fully inside canvas with generous top/bottom safe margins, occupying x=62%-94%, y=18%-82%, no cropping. Left stays calm near-solid navy. Background image only, no web-page UI outside these three cards, no watermark.
