import {useEffect,useState,type FormEvent} from 'react';
import {categories,label,rememberedKey,rememberKey,statuses,supportRequest,trackingLink,type Detail,type Session,type TicketPage} from './api';
import './support.css';
import './support-recovery.css';

const time=(value:string)=>new Date(value).toLocaleString();
function ErrorMessage({message}:{message:string}){return message?<p className="support-error" role="alert">{message}</p>:null;}

function TicketForm({session}:{session:Session|null}){
  const [receipt,setReceipt]=useState<{detail:Detail;trackingKey:string}|null>(null);
  const [busy,setBusy]=useState(false),[error,setError]=useState(''),[copied,setCopied]=useState(false);
  async function copyLink(url:string){
    try{await navigator.clipboard.writeText(url);setCopied(true);}
    catch{setError('Select and copy the link manually.');}
  }
  async function submit(event:FormEvent<HTMLFormElement>){
    event.preventDefault();const form=event.currentTarget;const data=Object.fromEntries(new FormData(form));
    setBusy(true);setError('');
    try{
      const result=await supportRequest<{detail:Detail;trackingKey:string}>('/tickets',{method:'POST',body:data});
      rememberKey(result.detail.ticket.id,result.trackingKey);setReceipt(result);
    }catch(e){setError((e as Error).message);}finally{setBusy(false);}
  }
  if(receipt){
    const url=trackingLink(receipt.detail.ticket.id,receipt.trackingKey);
    return <section className="support-confirmation" role="status"><h2>Ticket submitted</h2><p>Reference: <strong>{receipt.detail.ticket.id}</strong></p><p>Save this private tracking link now. Anyone with it can read and reply to your ticket. No confirmation email is sent with the ticket receipt.</p><label>Private tracking link<input readOnly value={url} onFocus={e=>e.currentTarget.select()}/></label><div className="support-actions"><a className="primary" href={url}>View ticket</a><button type="button" onClick={()=>void copyLink(url)}>{copied?'Copied':'Copy private link'}</button></div>{session?.authenticated&&<p>This ticket is also available under <a href="/support#mine">My tickets</a> when you sign in.</p>}{!session?.authenticated&&session?.recoveryAvailable&&<p>If you lose this link, use Recover a guest ticket on the support page. Email verification is required to replace it.</p>}<ErrorMessage message={error}/></section>;
  }
  return <form onSubmit={submit} className="support-form"><h2>Send a support request</h2><p>{session?.authenticated?'This request will be linked to your signed-in account.':'No account is required. Save the private link shown after submission to track your request.'}</p><p>Do not include passwords, API keys, payment details, or sensitive repository contents.</p>
    <div className="support-fields"><label>Your name<input name="name" autoComplete="name" required maxLength={100}/></label><label>Email address<input name="email" type="email" autoComplete="email" required maxLength={254}/></label></div>
    <label>Category<select name="category">{categories.map(category=><option key={category} value={category}>{label(category)}</option>)}</select></label>
    <label>Subject<input name="subject" required maxLength={160}/></label>
    <label>How can we help?<textarea name="message" required maxLength={8000} rows={7}/></label>
    <div className="support-honeypot" aria-hidden="true"><label>Leave this field empty<input name="website" tabIndex={-1} autoComplete="off"/></label></div>
    <p className="support-hint">Your name, email, and request are stored for support. Ticket updates are not emailed. If guest recovery is enabled, you can verify your email to replace a lost private link. Response times are not guaranteed.</p>
    <ErrorMessage message={error}/><button className="primary" disabled={busy||session===null}>{busy?'Submitting…':session===null?'Connecting…':'Submit ticket'}</button>
  </form>;
}

function TicketThread({id,accessKey,staff}:{id:string;accessKey:string;staff:boolean}){
  const [detail,setDetail]=useState<Detail|null>(null),[error,setError]=useState(''),[busy,setBusy]=useState(false),[draft,setDraft]=useState(''),[internal,setInternal]=useState(false),[status,setStatus]=useState('');
  const endpoint=`/${staff?'admin/':''}tickets/${encodeURIComponent(id)}`;
  useEffect(()=>{
    const abort=new AbortController();
    async function refresh(){
      try{const result=await supportRequest<Detail>(endpoint,{key:accessKey,signal:abort.signal});setDetail(result);setError('');}
      catch(e){if(!abort.signal.aborted)setError((e as Error).message);}
    }
    void refresh();const timer=window.setInterval(()=>{if(!document.hidden)void refresh();},15000);
    return()=>{abort.abort();clearInterval(timer);};
  },[endpoint,accessKey]);
  async function mutate(path:string,body:unknown,method='POST'){
    setBusy(true);setError('');
    try{setDetail(await supportRequest<Detail>(endpoint+path,{method,body,key:accessKey}));return true;}
    catch(e){setError((e as Error).message);return false;}finally{setBusy(false);}
  }
  async function reply(event:FormEvent){event.preventDefault();if(detail&&await mutate('/replies',{message:draft,internalNote:internal,version:detail.ticket.version}))setDraft('');}
  if(!detail)return <section><ErrorMessage message={error}/>{!error&&<p role="status">Loading ticket…</p>}<a href="/support">Back to support</a></section>;
  const ticket=detail.ticket;
  return <section className="support-thread"><a href={staff?'/support#admin':'/support#mine'}>← Back to tickets</a><h2>{ticket.subject}</h2><p className="support-meta"><strong>{label(ticket.status)}</strong> · {label(ticket.category)} · {ticket.id}</p><p>Opened {time(ticket.createdAt)} · Updated {time(ticket.updatedAt)}</p>{staff&&<p>From {ticket.name} · {ticket.email} <span className="support-hint">({ticket.emailVerifiedAt?`email verified ${time(ticket.emailVerifiedAt)}`:'email not verified'})</span></p>}
    <p className="support-hint">Updates refresh every 15 seconds while this tab is visible.</p><ErrorMessage message={error}/>
    {staff&&<form className="support-status" onSubmit={e=>{e.preventDefault();if(status)void mutate('',{status,version:ticket.version},'PATCH');}}><label>Ticket status<select value={status||ticket.status} onChange={e=>setStatus(e.target.value)}>{statuses.map(s=><option key={s} value={s}>{label(s)}</option>)}</select></label><button disabled={busy||!status}>Update status</button></form>}
    <ol className="support-messages">{detail.messages.map(message=><li key={message.id} className={message.internalNote?'internal-note':message.authorKind==='SYSTEM'?'system-message':''}><div><strong>{message.internalNote?'Internal staff note':message.authorKind==='SUPPORT'?'Support team':message.authorKind==='SYSTEM'?'Status update':'Requester'}</strong><time dateTime={message.createdAt}>{time(message.createdAt)}</time></div><p>{message.body}</p></li>)}</ol>
    {ticket.status==='CLOSED'?<p>This ticket is closed. <a href="/contact">Open a new request</a> if you need more help.</p>:<form onSubmit={reply} className="support-form"><label>{internal?'Internal note (staff only)':'Reply'}<textarea value={draft} onChange={e=>setDraft(e.target.value)} required maxLength={8000} rows={5}/></label>{staff&&<label className="support-checkbox"><input type="checkbox" checked={internal} onChange={e=>setInternal(e.target.checked)}/>Private internal note</label>}<button className="primary" disabled={busy||!draft.trim()}>{busy?'Saving…':internal?'Save internal note':'Send reply'}</button></form>}
  </section>;
}

function Inbox({staff}:{staff:boolean}){
  const [result,setResult]=useState<TicketPage|null>(null),[error,setError]=useState(''),[status,setStatus]=useState(''),[query,setQuery]=useState(''),[search,setSearch]=useState(''),[page,setPage]=useState(0);
  useEffect(()=>{
    const abort=new AbortController();setResult(null);setError('');
    async function load(){try{setResult(await supportRequest<TicketPage>(`/${staff?'admin/':''}tickets?${new URLSearchParams({status,q:search,page:String(page)})}`,{signal:abort.signal}));}catch(e){if(!abort.signal.aborted)setError((e as Error).message);}}
    void load();const timer=setInterval(()=>{if(!document.hidden)void load();},15000);return()=>{abort.abort();clearInterval(timer);};
  },[staff,status,search,page]);
  return <section><h2>{staff?'Support admin inbox':'My tickets'}</h2><form className="support-filters" onSubmit={e=>{e.preventDefault();setPage(0);setSearch(query);}}><label>Filter status<select value={status} onChange={e=>{setStatus(e.target.value);setPage(0);}}><option value="">All statuses</option>{statuses.map(s=><option key={s} value={s}>{label(s)}</option>)}</select></label><label>Search tickets<input value={query} onChange={e=>setQuery(e.target.value)} maxLength={160} placeholder="Subject, email, or full ticket ID"/></label><button>Search</button></form><ErrorMessage message={error}/>{!result&&!error&&<p role="status">Loading tickets…</p>}{result?.tickets.length===0&&<p>No tickets match your search.</p>}<ul className="support-ticket-list">{result?.tickets.map(ticket=><li key={ticket.id}><a href={`/support#ticket=${ticket.id}${staff?'&admin=1':''}`}><strong>{ticket.subject}</strong><span>{label(ticket.status)} · {label(ticket.category)}</span><small>{staff?`${ticket.name} · `:''}{time(ticket.updatedAt)}</small></a></li>)}</ul><div className="support-actions support-pagination"><button disabled={page===0} onClick={()=>setPage(p=>p-1)}>Previous</button><span>Page {page+1}</span><button disabled={!result?.hasMore} onClick={()=>setPage(p=>p+1)}>Next</button></div></section>;
}

export default function SupportApp({contact=false}:{contact?:boolean}){
  const [session,setSession]=useState<Session|null>(null),[sessionError,setSessionError]=useState(''),[hash,setHash]=useState(window.location.hash.slice(1)),[tracking,setTracking]=useState(''),[trackingError,setTrackingError]=useState(''),[recoveryReceipt,setRecoveryReceipt]=useState<{detail:Detail;trackingKey:string}|null>(null);
  useEffect(()=>{void supportRequest<Session>('/session').then(setSession).catch(e=>setSessionError(e.message));const change=()=>setHash(window.location.hash.slice(1));window.addEventListener('hashchange',change);return()=>window.removeEventListener('hashchange',change);},[]);
  const params=new URLSearchParams(hash),id=params.get('ticket')??'',key=params.get('key')??(id?rememberedKey(id):''),recoveryToken=params.get('recover')??'',staff=hash==='admin'||params.get('admin')==='1';
  useEffect(()=>{
    if(id&&params.get('key')&&rememberKey(id,key)){history.replaceState(null,'',`/support#ticket=${encodeURIComponent(id)}`);}
    // Only remove capabilities from the address bar after retaining them for this tab.
  },[id,key]);
  function openTracking(event:FormEvent){
    event.preventDefault();setTrackingError('');
    try{const url=new URL(tracking);const values=new URLSearchParams(url.hash.slice(1));if(url.origin!==location.origin||url.pathname!=='/support'||!values.get('ticket')||!values.get('key')?.match(/^[a-f0-9]{64}$/))throw new Error();location.hash=url.hash;}
    catch{setTrackingError('Paste the complete private tracking link you saved when submitting the ticket.');}
  }
  function acceptRecovery(receipt:{detail:Detail;trackingKey:string}){
    const {ticket}=receipt.detail;const stored=rememberKey(ticket.id,receipt.trackingKey);
    const nextHash=`ticket=${encodeURIComponent(ticket.id)}${stored?'':`&key=${encodeURIComponent(receipt.trackingKey)}`}`;
    setRecoveryReceipt(receipt);history.replaceState(null,'',`/support#${nextHash}`);setHash(nextHash);
  }
  return <div className="support-app"><nav aria-label="Support navigation"><a href="/contact#support">New ticket</a><a href="/support#mine">My tickets</a>{session?.administrator&&<a href="/support#admin">Admin inbox</a>}{session?.authenticated?<a href="/logout">Sign out</a>:<a href="/api/support/login">Sign in with GitHub</a>}</nav><ErrorMessage message={sessionError}/>{sessionError&&<button onClick={()=>window.location.reload()}>Retry connection</button>}
    {contact?<TicketForm session={session}/>:recoveryToken?<RecoveryConfirmation token={recoveryToken} onComplete={acceptRecovery}/>:id?<>{recoveryReceipt?.detail.ticket.id===id&&<RecoverySuccess receipt={recoveryReceipt}/>}<TicketThread key={`${id}:${staff}`} id={id} accessKey={key} staff={staff}/></>:staff?(session===null?<p>Checking access…</p>:session.administrator?<Inbox staff/>:<section><h2>Support administration</h2><p>A service-owner administrator account is required.</p><a className="primary" href="/api/support/login?admin=true">Sign in as administrator</a></section>):<>
      {session?.authenticated?<><Inbox staff={false}/>{session.recoveryAvailable&&<RecoveryRequestForm/>}</>:<section><h2>Track your request</h2><p>Use the private link you saved after submitting your ticket. Signed-in users can also view their account’s tickets here.</p>{session?.recoveryAvailable?<RecoveryRequestForm/>:<p className="support-hint">Lost a guest link? Email recovery is not enabled for this deployment. Contact support for help; an email address alone cannot unlock a ticket.</p>}</section>}
      <form className="support-form" onSubmit={openTracking}><label>Open a saved private tracking link<input type="url" value={tracking} onChange={e=>setTracking(e.target.value)} required autoComplete="off"/></label><ErrorMessage message={trackingError}/><button className="primary">Open ticket</button></form>
    </>}
  </div>;
}

function RecoverySuccess({receipt}:{receipt:{detail:Detail;trackingKey:string}}){
  const [copied,setCopied]=useState(false),[error,setError]=useState('');
  const url=trackingLink(receipt.detail.ticket.id,receipt.trackingKey);
  async function copy(){try{await navigator.clipboard.writeText(url);setCopied(true);setError('');}catch{setError('Select and copy the replacement link manually.');}}
  return <section className="support-confirmation support-recovery-success" role="status"><h2>Email verified; save your new private link</h2><p>Your previous ticket link has been revoked. This replacement is private: anyone who has it can read and reply to the ticket.</p><label>Replacement private tracking link<input readOnly value={url} onFocus={event=>event.currentTarget.select()}/></label><button className="primary" type="button" onClick={()=>void copy()}>{copied?'Copied':'Copy replacement link'}</button><ErrorMessage message={error}/></section>;
}

function RecoveryRequestForm(){
  const [busy,setBusy]=useState(false),[sent,setSent]=useState(false),[error,setError]=useState('');
  async function submit(event:FormEvent<HTMLFormElement>){
    event.preventDefault();const form=event.currentTarget;const data=Object.fromEntries(new FormData(form));setBusy(true);setSent(false);setError('');
    try{await supportRequest('/tickets/recovery',{method:'POST',body:data});setSent(true);form.reset();}
    catch(e){setError((e as Error).message);}finally{setBusy(false);}
  }
  return <form className="support-recovery-form" onSubmit={submit}><h3>Recover a guest ticket</h3><p>Enter the ticket reference and the email address submitted with it. If they match a guest ticket, we will email a one-time verification link. For privacy, this form always gives the same response.</p><label>Ticket reference<input name="ticketId" required maxLength={36} autoComplete="off" placeholder="UUID from the ticket receipt"/></label><label>Email address<input name="email" type="email" required maxLength={254} autoComplete="email"/></label><button disabled={busy}>{busy?'Sending request…':'Email a recovery link'}</button>{sent&&<p role="status">If a matching guest ticket can be recovered, instructions will be sent to its email address.</p>}<ErrorMessage message={error}/></form>;
}

function RecoveryConfirmation({token,onComplete}:{token:string;onComplete:(receipt:{detail:Detail;trackingKey:string})=>void}){
  const [busy,setBusy]=useState(false),[error,setError]=useState('');
  async function verify(event:FormEvent){event.preventDefault();setBusy(true);setError('');
    try{onComplete(await supportRequest<{detail:Detail;trackingKey:string}>('/tickets/recovery/confirm',{method:'POST',body:{token}}));}
    catch(reason){setError((reason as Error).message);}finally{setBusy(false);}
  }
  return <section className="support-confirmation" aria-live="polite"><h2>Recover your support ticket</h2><p>Confirm that you control this email address to create a replacement private link. The old link will stop working.</p>{error&&<><ErrorMessage message={error}/><p>This link may have expired or already been used. Request a new one from the support page.</p></>}<form onSubmit={verify}><button className="primary" disabled={busy}>{busy?'Verifying…':'Verify email and replace private link'}</button></form><a href="/support">Back to support</a></section>;
}
