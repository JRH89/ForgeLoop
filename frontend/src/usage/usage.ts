import type { FeatureRun, ProviderAttempt } from '../api';

export type UsageBucket = { name: string; knownMicros: number; priced: number; unpriced: number; tokens: number };
export const usd = (micros: number) => new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD', minimumFractionDigits: 2, maximumFractionDigits: micros > 0 && micros < 10_000 ? 6 : 2 }).format(micros / 1_000_000);

/** Attribute spend to the telemetry recording day, never the run's creation date. */
export function usageSummary(runs: FeatureRun[], days: number, repository: string, now = Date.now()) {
  const end = new Date(now); end.setUTCHours(0, 0, 0, 0); end.setUTCDate(end.getUTCDate() + 1);
  const start = new Date(end); start.setUTCDate(start.getUTCDate() - days);
  const daily = new Map<string, UsageBucket>();
  for (let day = start.getTime(); day < end.getTime(); day += 86_400_000) {
    const name = new Date(day).toISOString().slice(0, 10);
    daily.set(name, { name, knownMicros: 0, priced: 0, unpriced: 0, tokens: 0 });
  }
  const models = new Map<string, UsageBucket>();
  const harnesses = new Map<string, UsageBucket>();
  const rows: Array<{ run: FeatureRun; usage: UsageBucket }> = [];
  const total: UsageBucket = { name: 'Total', knownMicros: 0, priced: 0, unpriced: 0, tokens: 0 };
  function add(bucket: UsageBucket, attempt: ProviderAttempt) {
    if (attempt.costKnown) { bucket.knownMicros += attempt.estimatedCostMicros; bucket.priced++; }
    else bucket.unpriced++;
    bucket.tokens += attempt.inputTokens + attempt.outputTokens;
  }
  for (const run of runs.filter(item => !repository || item.repository === repository)) {
    const usage: UsageBucket = { name: run.id, knownMicros: 0, priced: 0, unpriced: 0, tokens: 0 };
    for (const attempt of run.tasks.flatMap(task => task.providerAttempts ?? [])) {
      const at = Date.parse(attempt.recordedAt);
      if (!Number.isFinite(at) || at < start.getTime() || at >= end.getTime()) continue;
      const modelName = `${attempt.provider} / ${attempt.model}`;
      if (!models.has(modelName)) models.set(modelName, { name: modelName, knownMicros: 0, priced: 0, unpriced: 0, tokens: 0 });
      const harnessName = run.harnessProfile || 'Unspecified';
      if (!harnesses.has(harnessName)) harnesses.set(harnessName, { name: harnessName, knownMicros: 0, priced: 0, unpriced: 0, tokens: 0 });
      add(harnesses.get(harnessName)!, attempt);
      add(usage, attempt); add(total, attempt); add(models.get(modelName)!, attempt);
      add(daily.get(new Date(at).toISOString().slice(0, 10))!, attempt);
    }
    const created = Date.parse(run.createdAt);
    if (usage.priced + usage.unpriced > 0 || (created >= start.getTime() && created < end.getTime())) rows.push({ run, usage });
  }
  return { total, daily: [...daily.values()], models: [...models.values()].sort((a, b) => b.knownMicros - a.knownMicros), harnesses: [...harnesses.values()].sort((a, b) => b.knownMicros - a.knownMicros), rows: rows.sort((a, b) => b.usage.knownMicros - a.usage.knownMicros) };
}
