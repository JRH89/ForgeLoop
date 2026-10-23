import { test } from 'node:test';
import assert from 'node:assert/strict';
import { createServer } from 'node:http';
import { configuration, runLoadTest } from './load-test.mjs';

async function withServer(handler, work) {
  const server = createServer(handler);
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  try { await work(`http://127.0.0.1:${server.address().port}/graphql`); }
  finally { server.closeAllConnections(); await new Promise(resolve => server.close(resolve)); }
}
const options = endpoint => ({ endpoint, durationMs: 80, concurrency: 2, requestTimeoutMs: 100, scenario: 'history' });

test('rejects unbounded input and write/unknown scenarios before traffic', () => {
  for (const env of [
    { FORGELOOP_LOAD_CONCURRENCY: '201' }, { FORGELOOP_LOAD_DURATION_MS: 'NaN' },
    { FORGELOOP_LOAD_REQUEST_TIMEOUT_MS: '0' }, { FORGELOOP_LOAD_SCENARIO: 'claim' },
    { FORGELOOP_LOAD_SCENARIO: 'detail' }, { FORGELOOP_LOAD_URL: 'file:///tmp/data' },
    { FORGELOOP_LOAD_URL: 'https://user:secret@example.com/graphql' },
  ]) assert.throws(() => configuration(env));
});

test('healthy bounded history traffic meets the regression gate', async () => {
  await withServer((request, response) => {
    let body = '';
    request.on('data', chunk => body += chunk);
    request.on('end', () => {
      assert.match(JSON.parse(body).query, /size:50/);
      response.end(JSON.stringify({ data: { featureRuns: [] } }));
    });
  }, async endpoint => {
    const result = await runLoadTest(options(endpoint));
    assert.ok(result.requests > 0);
    assert.equal(result.failed, 0);
    assert.equal(result.passed, true);
  });
});

test('GraphQL errors and missing data fail even with HTTP 200', async () => {
  for (const body of [{ errors: [{ message: 'Denied' }] }, { data: null }, {}]) {
    await withServer((_, response) => response.end(JSON.stringify(body)), async endpoint => {
      const result = await runLoadTest(options(endpoint));
      assert.equal(result.failed, result.requests);
      assert.equal(result.passed, false);
    });
  }
});

test('a stalled response body is aborted and counted without hanging', async () => {
  await withServer((_, response) => { response.writeHead(200); response.write('{'); }, async endpoint => {
    const started = performance.now();
    const result = await runLoadTest(options(endpoint));
    assert.equal(result.timedOut, result.requests);
    assert.equal(result.passed, false);
    assert.ok(performance.now() - started < 2_000);
  });
});

test('mixed scenario sends only read queries and pins the selected detail run', async () => {
  const seen = new Set();
  await withServer((request, response) => {
    let body = '';
    request.on('data', chunk => body += chunk);
    request.on('end', () => {
      const input = JSON.parse(body);
      assert.ok(input.query.startsWith('query'));
      const field = input.query.includes('featureRuns(') ? 'featureRuns' : input.query.includes('runAnalytics') ? 'runAnalytics' : 'featureRun';
      if (field === 'featureRun') assert.equal(input.variables.id, 'selected-run');
      seen.add(field);
      response.end(JSON.stringify({ data: { [field]: field === 'featureRuns' ? [] : {} } }));
    });
  }, async endpoint => {
    const result = await runLoadTest({ ...options(endpoint), durationMs: 150, scenario: 'mixed', runId: 'selected-run' });
    assert.equal(result.failed, 0);
    assert.equal(seen.size, 3);
  });
});
