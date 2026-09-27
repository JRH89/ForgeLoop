import type { CSSProperties } from 'react';

/** Stable route-specific assets are shared by HTML, social metadata and preloads. */
export function heroFor(path: string) {
  const key = path === '/' ? 'delivery-v2' : path.split('/').filter(Boolean).pop() || 'not-found';
  return {
    image: `/images/${key}-hero.webp`,
    small: `/images/${key}-hero-small.webp`,
    social: `/images/${key}-social-preview.jpg`,
  };
}

export function heroStyle(path: string): CSSProperties {
  const hero = heroFor(path);
  return { '--hero-image': `url("${hero.image}")`, '--hero-small': `url("${hero.small}")` } as CSSProperties;
}
