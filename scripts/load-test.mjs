const endpoint = process.env.FORGELOOP_LOAD_URL ?? "http://localhost:8090/graphql";
const durationMs = Number(process.env.FORGELOOP_LOAD_DURATION_MS ?? 30_000);
const concurrency = Number(process.env.FORGELOOP_LOAD_CONCURRENCY ?? 10);
const token = process.env.FORGELOOP_LOAD_TOKEN;

if (!Number.isInteger(concurrency) || concurrency < 1 || concurrency > 200) throw new Error("Concurrency must be 1..200");
if (!Number.isFinite(durationMs) || durationMs < 1_000 || durationMs > 900_000) throw new Error("Duration must be 1000..900000 ms");

const started = performance.now();
const latencies = [];
let failed = 0;
const body = JSON.stringify({ query: "{ featureRuns { id state } }" });
const headers = { "content-type": "application/json" };
if (token) headers.authorization = `Bearer ${token}`;

async function worker() {
  while (performance.now() - started < durationMs) {
    const requestStarted = performance.now();
    try {
      const response = await fetch(endpoint, { method: "POST", headers, body });
      const result = await response.json();
      if (!response.ok || result.errors) failed++;
    } catch {
      failed++;
    } finally {
      latencies.push(performance.now() - requestStarted);
    }
  }
}

await Promise.all(Array.from({ length: concurrency }, worker));
latencies.sort((left, right) => left - right);
const percentile = (value) => latencies[Math.min(latencies.length - 1, Math.floor(latencies.length * value))] ?? 0;
const errorRate = latencies.length ? failed / latencies.length : 1;
const summary = {
  requests: latencies.length,
  failed,
  errorRate: Number(errorRate.toFixed(4)),
  requestsPerSecond: Number((latencies.length / ((performance.now() - started) / 1000)).toFixed(2)),
  p50Ms: Number(percentile(0.5).toFixed(2)),
  p95Ms: Number(percentile(0.95).toFixed(2)),
  p99Ms: Number(percentile(0.99).toFixed(2)),
};
console.log(JSON.stringify(summary));
if (errorRate > 0.01 || summary.p95Ms > 500) process.exitCode = 1;
