import { test, expect } from '@playwright/test';
import { createHash, randomBytes } from 'node:crypto';

test('browser approval enrolls only the matching single-use desktop proof', async ({ page, request }) => {
  // The CI control plane is disposable/development-only. No task is created or claimed.
  const verifier=randomBytes(32).toString('hex');
  const challenge=createHash('sha256').update(verifier,'ascii').digest('hex');
  const name=`Browser pairing ${challenge.slice(0,8)}`;
  await page.goto(`/app/runner-connect#challenge=${challenge}&name=${encodeURIComponent(name)}`);
  await expect(page.getByText(challenge.slice(0,12),{exact:true})).toBeVisible();
  await expect(page.getByRole('button',{name:'Approve runner'})).toBeDisabled();
  await page.getByRole('checkbox').check();
  await page.getByRole('button',{name:'Approve runner'}).click();
  await expect(page.getByRole('status')).toContainText('Approved');
  const exchange='mutation($verifier:String!){exchangeRunnerPairing(verifier:$verifier){runner{id name organizationId} credential}}';
  const wrong=await request.post('/graphql',{data:{query:exchange,variables:{verifier:randomBytes(32).toString('hex')}}});
  expect((await wrong.json()).data.exchangeRunnerPairing).toBeNull();
  const response=await request.post('/graphql',{data:{query:exchange,variables:{verifier}}});
  const body=await response.json();
  expect(body.errors).toBeUndefined();
  const enrollment=body.data.exchangeRunnerPairing;
  expect(enrollment.runner.name).toBe(name);
  expect(enrollment.runner.organizationId).toBe('local-development');
  const heartbeat=await request.post('/graphql',{data:{query:'mutation($runnerId:ID!,$credential:String!){runnerHeartbeat(runnerId:$runnerId,credential:$credential){id}}',variables:{runnerId:enrollment.runner.id,credential:enrollment.credential}}});
  expect((await heartbeat.json()).data.runnerHeartbeat.id).toBe(enrollment.runner.id);
  const replay=await request.post('/graphql',{data:{query:exchange,variables:{verifier}}});
  expect((await replay.json()).errors).toBeTruthy();
});
