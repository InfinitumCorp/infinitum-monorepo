/**
 * Hardware evidence stub.
 * Records a telemetry event as Firmament-ready evidence.
 */
import type { HardwareTelemetry } from "./interface.ts";

export interface HardwareEvidence {
  taskId: string;
  status: string;
  hash: string;
  at: string;
}

export function toEvidence(t: HardwareTelemetry): HardwareEvidence {
  return {
    taskId: t.taskId,
    status: t.status,
    hash: `0xevidence_${t.taskId.slice(0, 8)}`,
    at: t.recordedAt,
  };
}
