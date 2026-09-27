import {afterEach,expect,test,vi} from 'vitest';
import {cleanup,fireEvent,render,screen,waitFor} from '@testing-library/react';
import '@testing-library/jest-dom/vitest';
import SupportApp from './SupportApp';
import {rememberKey,rememberedKey,trackingLink} from './api';

const ticket={id:'11111111-1111-1111-1111-111111111111',name:'Person',email:'a@example.com',subject:'Help with setup',category:'GENERAL',status:'OPEN',createdAt:'2026-09-26T12:00:00Z',updatedAt:'2026-09-26T12:00:00Z',version:0};
const detail={ticket,messages:[]};
const response=(data:unknown,ok=true)=>Promise.resolve({ok,status:ok?200:409,json:()=>Promise.resolve(data)} as Response);
afterEach(()=>{cleanup();vi.restoreAllMocks();vi.unstubAllGlobals();history.replaceState(null,'','/');sessionStorage.clear();});
test('guest submission displays a private link and sends bounded form fields',async()=>{
  const fetch=vi.fn().mockImplementationOnce(()=>response({authenticated:false,administrator:false})).mockImplementationOnce(()=>response({detail,trackingKey:'a'.repeat(64)}));vi.stubGlobal('fetch',fetch);
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
  vi.stubGlobal('fetch',vi.fn().mockImplementation((url:string,options:RequestInit)=>url.endsWith('/session')?response({authenticated:false,administrator:false}):options.method==='POST'?response({error:'Ticket changed. Refresh before sending.'},false):response(detail)));
  render(<SupportApp/>);const input=await screen.findByLabelText('Reply');
  fireEvent.change(input,{target:{value:'Keep this draft'}});fireEvent.click(screen.getByRole('button',{name:'Send reply'}));
  await screen.findByRole('alert');expect(input).toHaveValue('Keep this draft');
  expect(screen.queryByLabelText('Private internal note')).not.toBeInTheDocument();
  expect(screen.queryByLabelText('Ticket status')).not.toBeInTheDocument();
});
test('administrator sees status and private-note controls',async()=>{
  history.replaceState(null,'',`/support#ticket=${ticket.id}&admin=1`);
  vi.stubGlobal('fetch',vi.fn().mockImplementation((url:string)=>response(url.endsWith('/session')?{authenticated:true,administrator:true}:detail)));
  render(<SupportApp/>);await screen.findByLabelText('Ticket status');
  expect(screen.getByLabelText('Private internal note')).toBeInTheDocument();
  expect(screen.getByRole('link',{name:'Admin inbox'})).toBeInTheDocument();
});
test('capabilities remain usable when storage is unavailable',()=>{
  vi.spyOn(Storage.prototype,'setItem').mockImplementation(()=>{throw new Error('unavailable');});
  expect(rememberKey(ticket.id,'a'.repeat(64))).toBe(false);expect(rememberedKey(ticket.id)).toBe('');
});
