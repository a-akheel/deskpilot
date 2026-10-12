# Validation

Verified locally on October 11, 2026:

- 10 Temporal workflow tests: approval, rejection, expiry, wrong plan, duplicate decisions, changed revision, stale evidence, exhausted retries, response lost after commit, and history replay.
- Fixture HTTP tests for authentication, service scope, required idempotency keys, and repeated requests.
- FailureLab's 16 HTTP integration scenarios all passed.
- A separate actual JVM restart restored a pending plan and completed one action.
- React production build.

The first replacement-worker test inside Temporal's time-skipping environment hung waiting for a result. It was replaced with bounded result waits, a history replay test, and an actual process-restart integration check. This avoids treating a stalled test as evidence of recovery.

The integration fixture injects HTTP 503/504 responses; it does not reproduce every TCP timeout or partial-write failure. Integration results use the deterministic planner and simulated IT services. No paid model calls or production systems were used.
