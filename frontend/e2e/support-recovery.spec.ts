import {expect,test} from '@playwright/test';

for(const width of [390,1440])test(`guest recovery verifies and rotates the private link at ${width}px`,async({page})=>{
  await page.setViewportSize({width,height:900});
  const id='11111111-1111-1111-1111-111111111111',token='c'.repeat(64),replacement='b'.repeat(64);
  const detail={ticket:{id,name:'Guest',email:'guest@example.com',subject:'Lost my support link',category:'GENERAL',status:'OPEN',emailVerifiedAt:'2026-09-27T12:00:00Z',createdAt:'2026-09-27T11:00:00Z',updatedAt:'2026-09-27T12:00:00Z',version:0},messages:[]};
  const calls:{path:string;body?:unknown;key?:string|null}[]=[];
  await page.route('**/api/support/**',async route=>{
    const request=route.request(),url=new URL(request.url()),path=url.pathname.replace('/api/support','');
    const body=request.method()==='POST'?request.postDataJSON() as unknown:undefined;calls.push({path,body,key:request.headers()['x-support-key']});
    const data=path==='/session'?{authenticated:false,administrator:false,recoveryAvailable:true}
      :path==='/tickets/recovery'?{message:'If a matching guest ticket can be recovered, instructions will be sent to its email address.'}
      :path==='/tickets/recovery/confirm'?{detail,trackingKey:replacement}
      :path===`/tickets/${id}`?detail:{error:'Unexpected support request'};
    await route.fulfill({status:path==='/tickets/recovery'?202:200,json:data});
  });

  await page.goto('/support#mine');
  await page.getByLabel('Ticket reference').fill(id);
  await page.getByLabel('Email address').fill('guest@example.com');
  await page.getByRole('button',{name:'Email a recovery link'}).click();
  await expect(page.getByRole('status')).toContainText('If a matching guest ticket can be recovered');
  expect(calls.at(-1)).toMatchObject({path:'/tickets/recovery',body:{ticketId:id,email:'guest@example.com'}});

  await page.goto(`/support#recover=${token}`);
  await expect(page.getByRole('heading',{name:'Recover your support ticket'})).toBeVisible();
  expect(calls.some(call=>call.path==='/tickets/recovery/confirm')).toBe(false);
  await page.getByRole('button',{name:'Verify email and replace private link'}).click();
  await expect(page.getByRole('heading',{name:'Lost my support link'})).toBeVisible();
  const confirmation=calls.find(call=>call.path==='/tickets/recovery/confirm');
  expect(confirmation).toMatchObject({body:{token}});
  expect(confirmation?.key).toBeUndefined();
  expect(calls.find(call=>call.path===`/tickets/${id}`)?.key).toBe(replacement);
  expect(page.url()).not.toContain('recover=');
  await expect(page.getByLabel('Replacement private tracking link')).toHaveValue(new RegExp(`/support#ticket=${id}&key=${replacement}$`));
  expect(await page.evaluate(()=>document.documentElement.scrollWidth<=window.innerWidth)).toBe(true);
});
