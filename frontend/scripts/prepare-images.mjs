import sharp from 'sharp';
import { mkdir, copyFile } from 'node:fs/promises';
await mkdir('public/images',{recursive:true});
await mkdir('public/icons',{recursive:true});
await mkdir('public/fonts',{recursive:true});
await copyFile('node_modules/@fontsource-variable/inter/files/inter-latin-wght-normal.woff2','public/fonts/inter-latin-variable.woff2');
await copyFile('node_modules/@fontsource-variable/inter/LICENSE','public/fonts/INTER-LICENSE.txt');
const hero='src/assets/delivery-hero-generated.png';
// Encoding/size derivatives of the approved generated asset, not substitute artwork.
await sharp(hero).resize({width:1774,withoutEnlargement:true}).webp({quality:82}).toFile('public/images/delivery-hero.webp');
await sharp(hero).resize({width:900,withoutEnlargement:true}).webp({quality:78}).toFile('public/images/delivery-hero-small.webp');
await sharp(hero).resize(1200,630,{fit:'cover'}).jpeg({quality:86}).toFile('public/images/social-preview.jpg');
for(const size of [32,180,192])await sharp('src/assets/favicon.png').resize(size,size).png().toFile(`public/icons/icon-${size}.png`);
