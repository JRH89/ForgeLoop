import {expect,test} from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';

// Mutating tests must target a disposable database, never the public deployment.
test.skip(process.env.FORGELOOP_SUPPORT_E2E!=='1','Requires an isolated support test stack');
test('guest submits, saves a private link, replies and tracks across browsers',async({page,browser})=>{
  await page.goto('/contact');
  await page.getByLabel('Your name').fill('Support test customer');
  await page.getByLabel('Email address').fill('support-test@example.com');
  await page.getByLabel('Subject',{exact:true}).fill('Isolated runner setup question');
  await page.getByLabel('How can we help?').fill('How can I verify setup without making a paid provider call?');
  await expect(page.getByRole('button',{name:'Submit ticket'})).toBeEnabled();
  expect((await new AxeBuilder({page}).include('.support-app').analyze()).violations).toEqual([]);
  await page.getByRole('button',{name:'Submit ticket'}).click();
  await expect(page.getByRole('heading',{name:'Ticket submitted'})).toBeVisible();
  const link=await page.getByLabel('Private tracking link').inputValue();
  expect(link).toMatch(/\/support#ticket=.*&key=[a-f0-9]{64}$/);
  await page.getByRole('link',{name:'View ticket',exact:true}).click();
  await expect(page.getByRole('heading',{name:'Isolated runner setup question'})).toBeVisible();
  await page.getByLabel('Reply',{exact:true}).fill('A follow-up from the requester.');
  await page.getByRole('button',{name:'Send reply'}).click();
  await expect(page.getByText('A follow-up from the requester.',{exact:true})).toBeVisible();
  await page.reload();
  await expect(page.getByText('A follow-up from the requester.',{exact:true})).toBeVisible();
  expect(page.url()).not.toContain('&key=');
  const fresh=await browser.newContext();const other=await fresh.newPage();
  await other.goto(link);
  await expect(other.getByText('A follow-up from the requester.',{exact:true})).toBeVisible();
  await other.setViewportSize({width:390,height:844});
  expect(await other.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true);
  expect((await new AxeBuilder({page:other}).include('.support-app').analyze()).violations).toEqual([]);
  await other.screenshot({path:'../evidence/support/guest-thread-mobile.png',fullPage:true});
  await fresh.close();
  const invalid=await browser.newContext();const denied=await invalid.newPage();
  await denied.goto(link.replace(/key=[a-f0-9]+/,'key='+ '0'.repeat(64)));
  await expect(denied.getByRole('alert')).toContainText('Ticket not found');
  await invalid.close();
});

test('anonymous visitors cannot list customer or admin tickets; support is not indexed',async({page,request})=>{
  const headers={'X-ForgeLoop-Support':'1'};
  for(const path of ['/api/support/tickets','/api/support/admin/tickets'])expect((await request.get(path,{headers})).status()).toBe(401);
  const response=await page.goto('/support#admin');
  expect(response?.headers()['cache-control']).toBe('no-store');
  expect(response?.headers()['x-robots-tag']).toBe('noindex');
  await expect(page.getByRole('link',{name:'Sign in as administrator'})).toBeVisible();
  expect((await request.post('/api/support/tickets',{data:{}})).status()).toBe(403);
});

test('concurrent replies commit once and reject the stale version',async({request})=>{
  const headers={'X-ForgeLoop-Support':'1'};
  const receipt=await request.post('/api/support/tickets',{headers,data:{name:'Concurrency test',email:'test@example.com',subject:'Isolated concurrency check',category:'GENERAL',message:'Test request'}});
  expect(receipt.status()).toBe(201);
  const created=await receipt.json();const path=`/api/support/tickets/${created.detail.ticket.id}`;
  const access={...headers,'X-Support-Key':created.trackingKey};
  const results=await Promise.all(['First reply','Second reply'].map(message=>request.post(`${path}/replies`,{headers:access,data:{message,internalNote:false,version:0}})));
  expect(results.map(result=>result.status()).sort()).toEqual([200,409]);
  const persisted=await(await request.get(path,{headers:access})).json();
  expect(persisted.ticket.version).toBe(1);expect(persisted.messages).toHaveLength(2);
});
