import '@testing-library/jest-dom/vitest';
import { fireEvent, render, screen } from '@testing-library/react';
import { afterEach, expect, it, vi } from 'vitest';
import RunnerPairingPage from './RunnerPairingPage';
import { approveRunnerPairing, loadOperator } from './api';
vi.mock('./api',()=>({approveRunnerPairing:vi.fn(),loadOperator:vi.fn()}));
afterEach(()=>{vi.resetAllMocks();window.history.replaceState({},'','/');});
it('requires fingerprint confirmation and admin approval without exposing a verifier',async()=>{
  window.history.replaceState({},'','/app/runner-connect#challenge='+'a'.repeat(64)+'&name=Laptop');
  vi.mocked(loadOperator).mockResolvedValue({subject:'admin',organizationId:'org',role:'ADMIN'});
  vi.mocked(approveRunnerPairing).mockResolvedValue(true);
  render(<RunnerPairingPage/>);
  expect(screen.getByRole('button',{name:'Approve runner'})).toBeDisabled();
  fireEvent.click(screen.getByRole('checkbox'));fireEvent.click(screen.getByRole('button',{name:'Approve runner'}));
  expect(await screen.findByRole('status')).toHaveTextContent('No paid work has started');
  expect(approveRunnerPairing).toHaveBeenCalledWith('a'.repeat(64),'Laptop');
});
it('rejects malformed links',()=>{render(<RunnerPairingPage/>);expect(screen.getByRole('alert')).toHaveTextContent('Invalid pairing request');});
