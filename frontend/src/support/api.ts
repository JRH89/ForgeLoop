export type Session={authenticated:boolean;administrator:boolean};
export type Ticket={id:string;name:string;email:string;subject:string;category:string;status:string;createdAt:string;updatedAt:string;version:number};
export type Message={id:string;authorKind:string;body:string;internalNote:boolean;createdAt:string};
export type Detail={ticket:Ticket;messages:Message[]};
export type TicketPage={tickets:Ticket[];hasMore:boolean;page:number};
export const statuses=['OPEN','IN_PROGRESS','WAITING_ON_CUSTOMER','RESOLVED','CLOSED'];
export const categories=['GENERAL','BUG','BILLING','ACCOUNT','FEATURE'];
export const label=(value:string)=>value.toLowerCase().replaceAll('_',' ').replace(/^./,c=>c.toUpperCase());
export async function supportRequest<T>(path:string,options:{method?:string;body?:unknown;key?:string;signal?:AbortSignal}={}):Promise<T>{
  const response=await fetch(`/api/support${path}`,{
    method:options.method??'GET',credentials:'same-origin',cache:'no-store',signal:options.signal,
    headers:{'X-ForgeLoop-Support':'1',...(options.body?{'Content-Type':'application/json'}:{}),...(options.key?{'X-Support-Key':options.key}:{})},
    body:options.body?JSON.stringify(options.body):undefined,
  });
  const data=await response.json().catch(()=>null);
  if(!response.ok)throw new Error(data?.error??(response.status===413?'Your message is too large.':'Support could not be loaded. Please try again.'));
  return data as T;
}
/** The capability stays in the fragment, never the request URL or referrer. */
export function trackingLink(id:string,key:string){return `${window.location.origin}/support#ticket=${encodeURIComponent(id)}&key=${encodeURIComponent(key)}`;}
export function rememberKey(id:string,key:string){try{sessionStorage.setItem(`support:${id}`,key);return true;}catch{return false;/* Keep the fragment when private browsing disables storage. */}}
export function rememberedKey(id:string){try{return sessionStorage.getItem(`support:${id}`)??'';}catch{return '';}}
