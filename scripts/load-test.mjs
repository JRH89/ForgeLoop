import { pathToFileURL } from 'node:url';

const queries = {
  history: 'query { featureRuns(page:0,size:50,archived:false) { id state } }',
  analytics: 'query { runAnalytics { totalRuns activeRuns deliveredRuns } }',
  detail: 'query($id:ID!) { featureRun(id:$id) { id state tasks { id state } gates { name state } } }',
};

/** Validate all operator input before sending traffic; no write scenarios are implicit. */
export function configuration(env = process.env) {
  const config = {
    endpoint: env.FORGELOOP_LOAD_URL ?? 'http://localhost:8090/graphql',
    durationMs: Number(env.FORGELOOP_LOAD_DURATION_MS ?? 30_000),
    concurrency: Number(env.FORGELOOP_LOAD_CONCURRENCY ?? 10),
    requestTimeoutMs: Number(env.FORGELOOP_LOAD_REQUEST_TIMEOUT_MS ?? 5_000),
    scenario: env.FORGELOOP_LOAD_SCENARIO ?? 'history',
    runId: env.FORGELOOP_LOAD_RUN_ID,
    token: env.FORGELOOP_LOAD_TOKEN,
  };
  const url = new URL(config.endpoint);
  if (!['http:', 'https:'].includes(url.protocol) || url.username || url.password)
    throw new Error('Load URL must use HTTP(S) without embedded credentials');
  if (!Number.isInteger(config.concurrency) || config.concurrency < 1 || config.concurrency > 200)
    throw new Error('Concurrency must be 1..200');
  if (!Number.isInteger(config.durationMs) || config.durationMs < 1_000 || config.durationMs > 900_000)
    throw new Error('Duration must be 1000..900000 ms');
  if (!Number.isInteger(config.requestTimeoutMs) || config.requestTimeoutMs < 100 || config.requestTimeoutMs > 60_000)
    throw new Error('Request timeout must be 100..60000 ms');
  if (![...Object.keys(queries), 'mixed'].includes(config.scenario)) throw new Error('Unknown load scenario');
  if (['detail', 'mixed'].includes(config.scenario) && !config.runId?.trim())
    throw new Error('Detail and mixed scenarios require FORGELOOP_LOAD_RUN_ID');
  return config;
}

/** Exercise the real HTTP boundary with bounded requests and a bounded latency sample. */
export async function runLoadTest(config) {
  const started = performance.now();
  const latencies = [];
  const sampleLimit = 100_000;
  let requests = 0, failed = 0, timedOut = 0, issued = 0;
  const headers = { 'content-type': 'application/json' };
  if (config.token) headers.authorization = `Bearer ${config.token}`;
  const scenarios = config.scenario === 'mixed' ? Object.keys(queries) : [config.scenario];

  async function worker() {
    while (performance.now() - started < config.durationMs) {
      const scenario = scenarios[issued++ % scenarios.length];
      const requestStarted = performance.now();
      // This covers connection establishment, headers, and the complete JSON response body.
      const signal = AbortSignal.timeout(config.requestTimeoutMs);
      try {
        const response = await fetch(config.endpoint, {
          method: 'POST', headers, signal, redirect: 'error',
          body: JSON.stringify({ query: queries[scenario], variables: scenario === 'detail' ? { id: config.runId } : {} }),
        });
        const result = await response.json();
        const field = scenario === 'history' ? 'featureRuns' : scenario === 'analytics' ? 'runAnalytics' : 'featureRun';
        if (!response.ok || result.errors?.length || result.data?.[field] == null) failed++;
      } catch {
        failed++;
        if (signal.aborted) timedOut++;
      } finally {
        requests++;
        const latency = performance.now() - requestStarted;
        // Reservoir sampling avoids unbounded memory while keeping a representative sample.
        if (latencies.length < sampleLimit) latencies.push(latency);
        else {
          const index = Math.floor(Math.random() * requests);
          if (index < sampleLimit) latencies[index] = latency;
        }
      }
    }
  }

  await Promise.all(Array.from({ length: config.concurrency }, worker));
  latencies.sort((a, b) => a - b);
  const percentile = value => latencies[Math.max(0, Math.ceil(latencies.length * value) - 1)] ?? 0;
  const errorRate = requests ? failed / requests : 1;
  const summary = {
    scenario: config.scenario, requests, failed, timedOut,
    errorRate: Number(errorRate.toFixed(4)),
    requestsPerSecond: Number((requests / ((performance.now() - started) / 1000)).toFixed(2)),
    latencySamples: latencies.length,
    p50Ms: Number(percentile(0.5).toFixed(2)),
    p95Ms: Number(percentile(0.95).toFixed(2)),
    p99Ms: Number(percentile(0.99).toFixed(2)),
    passed: errorRate <= 0.01 && percentile(0.95) <= 500,
  };
  return summary;
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  try {
    const summary = await runLoadTest(configuration());
    console.log(JSON.stringify(summary));
    if (!summary.passed) process.exitCode = 1;
  } catch {
    // Do not echo endpoint URLs or credentials supplied through the environment.
    console.error('Load test could not run. Check URL, scenario, duration, concurrency, timeout, and run ID configuration.');
    process.exitCode = 1;
  }
}
