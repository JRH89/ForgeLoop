import '@testing-library/jest-dom/vitest';
import { fireEvent, render, screen } from '@testing-library/react';
import { afterEach, expect, it, vi } from 'vitest';
import RepositoryScans from './RepositoryScans';

const finding={id:'finding-1',severity:'HIGH',title:'Reject invalid values',description:'Empty values pass validation.',impact:'Invalid records can be stored.',evidence:'The validator accepts an empty string.',affectedFiles:['src/Validator.java'],acceptanceCriteria:['Reject empty input.']};
const scan={id:'scan-1',repository:'owner/repo',baseBranch:'main',status:'COMPLETE',requestedBy:'admin',createdAt:'2026-01-01T00:00:00Z',commitSha:'a'.repeat(40),provider:'openai',model:'model',inputTokens:100,outputTokens:20,estimatedCostMicros:1200,costKnown:true,failureSummary:null,findings:[finding]};
afterEach(()=>vi.unstubAllGlobals());

it('requires a second explicit action before starting a potentially paid scan',async()=>{
  const fetch=vi.fn(async(_url:string,init:RequestInit)=>({ok:true,json:async()=>JSON.parse(String(init.body)).query.includes('repositoryScans')?{data:{repositoryScans:[]}}:{data:{requestRepositoryScan:{...scan,status:'PENDING',findings:[]}}}}));
  vi.stubGlobal('fetch',fetch);
  render(<RepositoryScans repository="owner/repo" isAdmin/>);
  fireEvent.click(screen.getByRole('button',{name:'Analyze repository'}));
  expect(await screen.findByText(/Provider charges may apply/)).toBeVisible();
  expect(fetch).toHaveBeenCalledTimes(1);
  fireEvent.click(screen.getByRole('button',{name:'Start manual scan'}));
  expect(await screen.findByText('PENDING')).toBeVisible();
});

it('creates an issue only after the administrator presses the finding action',async()=>{
  const fetch=vi.fn(async(_url:string,init:RequestInit)=>{
    const query=JSON.parse(String(init.body)).query as string;
    if(query.includes('repositoryScans'))return {ok:true,json:async()=>({data:{repositoryScans:[scan]}})};
    return {ok:true,json:async()=>({data:{createRepositoryScanIssue:{...finding,issueNumber:7,issueUrl:'https://github.com/owner/repo/issues/7'}}})};
  });
  vi.stubGlobal('fetch',fetch);
  render(<RepositoryScans repository="owner/repo" isAdmin/>);
  expect(await screen.findByText('Reject invalid values')).toBeVisible();
  expect(fetch).toHaveBeenCalledTimes(1);
  fireEvent.click(screen.getByRole('button',{name:'Create GitHub issue'}));
  expect(await screen.findByRole('link',{name:'GitHub issue #7'})).toHaveAttribute('href','https://github.com/owner/repo/issues/7');
});
