import {afterEach,expect,test,vi} from 'vitest';
import {cleanup,fireEvent,render,screen,waitFor} from '@testing-library/react';
import '@testing-library/jest-dom/vitest';
import SupportApp from './SupportApp';
import {rememberKey,rememberedKey,trackingLink} from './api';

const ticket={id:'11111111-1111-1111-1111-111111111111',name:'Person',email:'a@example.com',subject:'Help with setup',category:'GENERAL',status:'OPEN',emailVerifiedAt:null,createdAt:'2026-09-26T12:00:00Z',updatedAt:'2026-09-26T12:00:00Z',version:0};
const detail={ticket,messages:[]};
const response=(data:unknown,ok=true)=>Promise.resolve({ok,status:ok?200:409,json:()=>Promise.resolve(data)} as Response);
afterEach(()=>{cleanup();vi.restoreAllMocks();vi.unstubAllGlobals();history.replaceState(null,'','/');sessionStorage.clear();});
test('guest submission displays a private link and sends bounded form fields',async()=>{
  const fetch=vi.fn().mockImplementationOnce(()=>response({authenticated:false,administrator:false,recoveryAvailable:true})).mockImplementationOnce(()=>response({detail,trackingKey:'a'.repeat(64)}));vi.stubGlobal('fetch',fetch);
  render(<SupportApp contact/>);
  await waitFor(()=>expect(screen.getByRole('button',{name:'Submit ticket'})).toBeEnabled());
  fireEvent.change(screen.getByLabelText('Your name'),{target:{value:'Person'}});
  fireEvent.change(screen.getByLabelText('Email address'),{target:{value:'a@example.com'}});
  fireEvent.change(screen.getByLabelText('Subject'),{target:{value:'Help with setup'}});
  fireEvent.change(screen.getByLabelText('How can we help?'),{target:{value:'Question'}});
  fireEvent.click(screen.getByRole('button',{name:'Submit ticket'}));
  await screen.findByRole('heading',{name:'Ticket submitted'});
  expect(screen.getByLabelText('Private tracking link')).toHaveValue(trackingLink(ticket.id,'a'.repeat(64)));
  expect(fetch.mock.calls[1][1].headers['X-ForgeLoop-Support']).toBe('1');
  expect(JSON.parse(fetch.mock.calls[1][1].body).message).toBe('Question');
});
test('conflicting reply preserves the customer draft and hides staff controls',async()=>{
  history.replaceState(null,'',`/support#ticket=${ticket.id}&key=${'a'.repeat(64)}`);
  vi.stubGlobal('fetch',vi.fn().mockImplementation((url:string,options:RequestInit)=>url.endsWith('/session')?response({authenticated:false,administrator:false,recoveryAvailable:false}):options.method==='POST'?response({error:'Ticket changed. Refresh before sending.'},false):response(detail)));
  render(<SupportApp/>);const input=await screen.findByLabelText('Reply');
  fireEvent.change(input,{target:{value:'Keep this draft'}});fireEvent.click(screen.getByRole('button',{name:'Send reply'}));
  await screen.findByRole('alert');expect(input).toHaveValue('Keep this draft');
  expect(screen.queryByLabelText('Private internal note')).not.toBeInTheDocument();
  expect(screen.queryByLabelText('Ticket status')).not.toBeInTheDocument();
});
test('administrator sees status and private-note controls',async()=>{
  history.replaceState(null,'',`/support#ticket=${ticket.id}&admin=1`);
  vi.stubGlobal('fetch',vi.fn().mockImplementation((url:string)=>response(url.endsWith('/session')?{authenticated:true,administrator:true,recoveryAvailable:true}:detail)));
  render(<SupportApp/>);await screen.findByLabelText('Ticket status');
  expect(screen.getByLabelText('Private internal note')).toBeInTheDocument();
  expect(screen.getByRole('link',{name:'Admin inbox'})).toBeInTheDocument();
});
test('account mode renders the signed-in ticket list without duplicate support navigation',async()=>{
  history.replaceState(null,'','/app#account=1');
  vi.stubGlobal('fetch',vi.fn().mockImplementation((url:string)=>url.endsWith('/session')
    ?response({authenticated:true,administrator:false,recoveryAvailable:false})
    :response({tickets:[ticket],hasMore:false,page:0})));
  render(<SupportApp accountMode/>);
  const link=await screen.findByRole('link',{name:/Help with setup/});
  expect(link).toHaveAttribute('href',`/app#ticket=${ticket.id}&account=1`);
  expect(screen.getByRole('heading',{name:'My tickets'})).toBeInTheDocument();
  expect(screen.queryByRole('navigation',{name:'Support navigation'})).not.toBeInTheDocument();
});
test('guest recovery request gets the same non-enumerating confirmation regardless of the supplied reference',async()=>{
  const fetch=vi.fn().mockImplementation((url:string)=>url.endsWith('/session')?response({authenticated:false,administrator:false,recoveryAvailable:true}):response({message:'If a matching guest ticket can be recovered, instructions will be sent to its email address.'}));
  vi.stubGlobal('fetch',fetch);render(<SupportApp/>);
  await screen.findByRole('heading',{name:'Recover a guest ticket'});
  fireEvent.change(screen.getByLabelText('Ticket reference'),{target:{value:ticket.id}});
  fireEvent.change(screen.getByLabelText('Email address'),{target:{value:'a@example.com'}});
  fireEvent.click(screen.getByRole('button',{name:'Email a recovery link'}));
  expect(await screen.findByRole('status')).toHaveTextContent('If a matching guest ticket can be recovered');
  const [url,options]=fetch.mock.calls[1];expect(url).toBe('/api/support/tickets/recovery');
  expect(JSON.parse(String(options.body))).toEqual({ticketId:ticket.id,email:'a@example.com'});
});
test('email recovery fragment is redeemed in a request body and the replaced capability opens the ticket',async()=>{
  const newKey='b'.repeat(64),token='c'.repeat(64);
  history.replaceState(null,'',`/support#recover=${token}`);
  const fetch=vi.fn().mockImplementation((url:string)=>{
    if(url.endsWith('/session'))return response({authenticated:false,administrator:false,recoveryAvailable:true});
    if(url.endsWith('/tickets/recovery/confirm'))return response({detail,trackingKey:newKey});
    if(url.endsWith(`/tickets/${ticket.id}`))return response(detail);
    return response({error:'Unexpected request'},false);
  });vi.stubGlobal('fetch',fetch);render(<SupportApp/>);
  fireEvent.click(await screen.findByRole('button',{name:'Verify email and replace private link'}));
  expect(await screen.findByRole('heading',{name:'Help with setup'})).toBeInTheDocument();
  const confirm=fetch.mock.calls.find(([url])=>url.endsWith('/tickets/recovery/confirm'))!;
  expect(confirm[0]).toBe('/api/support/tickets/recovery/confirm');
  expect(JSON.parse(String(confirm[1].body))).toEqual({token});
  expect(new Headers(confirm[1].headers).get('X-Support-Key')).toBeNull();
  expect(screen.getByRole('heading',{name:'Help with setup'})).toBeInTheDocument();
  expect(screen.getByLabelText('Replacement private tracking link')).toHaveValue(trackingLink(ticket.id,newKey));
  expect(rememberedKey(ticket.id)).toBe(newKey);
  expect(history.state).toBeNull();expect(location.hash).toBe(`#ticket=${ticket.id}`);
});
test('capabilities remain usable when storage is unavailable',()=>{
  vi.spyOn(Storage.prototype,'setItem').mockImplementation(()=>{throw new Error('unavailable');});
  expect(rememberKey(ticket.id,'a'.repeat(64))).toBe(false);expect(rememberedKey(ticket.id)).toBe('');
});
