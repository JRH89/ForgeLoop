import '@testing-library/jest-dom/vitest';
import { fireEvent, render, screen } from '@testing-library/react';
import { afterEach, expect, it, vi } from 'vitest';
import IntakeSettings from './IntakeSettings';
import type {RepositoryConnection} from './api';
afterEach(()=>vi.unstubAllGlobals());
it('persists the explicit assignee and surfaces failures',async()=>{
  const fetch=vi.fn(async(url:string,init:RequestInit)=>{expect(url).toBe('/graphql');expect(init.method).toBe('POST');return {ok:false,json:async()=>({errors:[{message:'Access denied'}]})};});vi.stubGlobal('fetch',fetch);
  render(<IntakeSettings item={{repository:'org/repo'} as RepositoryConnection} editable onSaved={()=>{}}/>);
  fireEvent.change(screen.getByRole('textbox'),{target:{value:'worker'}});
  fireEvent.click(screen.getByRole('button',{name:'Save intake setting'}));
  expect(await screen.findByRole('alert')).toHaveTextContent('Access denied');
  expect(JSON.parse(String(fetch.mock.calls[0][1].body)).variables).toEqual({repository:'org/repo',requiredAssignee:'worker'});
});
