import '@testing-library/jest-dom/vitest';
import { fireEvent, render, screen } from '@testing-library/react';
import { afterEach, expect, it, vi } from 'vitest';
import RunnerSetup from './RunnerSetup';
afterEach(()=>vi.unstubAllGlobals());
it('lets an administrator enroll without asking the browser for provider credentials',async()=>{
  vi.stubGlobal('fetch',vi.fn(async(_url,init)=>({ok:true,json:async()=>({data:JSON.parse(init.body).query.includes('mutation')?{issueRunnerRegistrationToken:'single-use-test-token'}:{runners:[]}})})));
  render(<RunnerSetup operator={{subject:'admin',organizationId:'org',role:'ADMIN'}}/>);
  fireEvent.click(screen.getByRole('button',{name:'Generate enrollment token'}));
  expect(await screen.findByLabelText('One-time enrollment token')).toHaveValue('single-use-test-token');
  expect(screen.getByRole('link',{name:'Download runner package'})).toHaveAttribute('href','/downloads/forgeloop-runner.zip');
  expect(screen.queryByLabelText('Provider API key')).not.toBeInTheDocument();
  fireEvent.click(screen.getByRole('button',{name:'Hide token'}));
  expect(screen.queryByLabelText('One-time enrollment token')).not.toBeInTheDocument();
});
it('does not offer enrollment to viewers',()=>{
  vi.stubGlobal('fetch',vi.fn(async()=>({ok:true,json:async()=>({data:{runners:[]}})})));
  render(<RunnerSetup operator={{subject:'viewer',organizationId:'org',role:'VIEWER'}}/>);
  expect(screen.queryByRole('button',{name:'Generate enrollment token'})).not.toBeInTheDocument();
});
