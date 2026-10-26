import { availableParallelism, loadavg } from 'node:os';

// Host load for e2e timeouts and timing budgets. Agents share a 20-CPU box and the 1-minute loadavg can sit at
// 50-100; SwiftShader WebGL then runs several times slower. Budgets are set on a quiet machine (load at or under
// the CPU count, factor 1) and stretch in proportion above it, so they stay tight when quiet and still catch a
// hang under load. The factor is printed next to every timing number.

/** 1-minute loadavg per CPU, at least 1. */
export function loadFactor(): number {
  return Math.max(1, loadavg()[0]! / availableParallelism());
}

/** loadFactor() capped at 4: for test timeouts (30 s becomes at most 120 s). */
export function timeoutFactor(): number {
  return Math.min(4, loadFactor());
}

/** A timing budget in ms scaled by the worse of the load factor now and `atStart` (taken when timing began). */
export function budget(quietMs: number, atStart: number): number {
  return Math.round(quietMs * Math.max(atStart, loadFactor()));
}
