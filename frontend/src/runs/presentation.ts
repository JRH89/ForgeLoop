import type { FeatureRun } from '../api';

export const terminal = new Set(["COMPLETE", "CANCELLED", "FAILED", "REJECTED"]);
export const retryable = new Set(["FAILED", "HELD", "RETRYABLE_FAILURE"]);
export const money = (micros: number) => `$${(micros / 1_000_000).toFixed(micros > 0 && micros < 10000 ? 6 : 2)}`;
export const costSummary = (run:FeatureRun, compact = false) => {
  const attempts = run.tasks.flatMap(task=>task.providerAttempts??[]);
  const unknown = attempts.filter(attempt=>!attempt.costKnown).length;
  // Keep queue cells short while retaining the full explanation in their tooltip.
  if (compact && (attempts.length===0 || unknown===attempts.length)) return 'N/A';
  return attempts.length===0?'No usage recorded':unknown===attempts.length?'Cost unavailable — no model rate':`${money(run.spentCostMicros)} estimated${unknown?` + ${unknown} unpriced requests`:''}`;
};
export const stamp = (value: string) =>
  new Intl.DateTimeFormat(undefined, {
    dateStyle: "medium",
    timeStyle: "short",
  }).format(new Date(value));
