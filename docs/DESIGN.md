# Design decisions

## Temporal owns the wait

Approval can outlive a process. Keeping it only in an HTTP handler or in-memory map loses it on restart. Temporal records the plan, signal, activities, and timer in workflow history. The recovery experiment stops the JVM while it awaits approval, then starts a new JVM against the same server and approves the original plan.

## Approval is tied to a plan

A boolean alone cannot identify what was reviewed. Decisions now include a generated plan ID. The workflow accepts one matching decision while it is waiting; wrong IDs and later decisions are ignored. This is still shared-role authentication, not a named-person audit trail.

## Evidence is checked twice

The initial prototype could act on context retrieved an hour earlier. The workflow now rereads it after approval and rejects changed revisions, healthy services, and evidence older than five minutes. There remains a check-to-action race: a real service should atomically compare the approved revision while applying the action.

## Idempotency belongs downstream

Temporal retries activities, so an API response can fail after an action succeeds. Every retry carries the same workflow-derived key. The SQLite fixture commits its simulated effect and result together; the lost-response scenario then returns an error. The retry retrieves the stored result, leaving one action row. A real restart API must provide an equivalent guarantee itself.

## Expected failures are distinct

Authorization and malformed-context failures are not retried. Transient server failures use a three-attempt budget. Workflow state becomes `failed` with a sanitized category, while Temporal retains the full activity history. Cancellation and unexpected workflow exceptions are not caught as ordinary activity failures.

## Model and orchestration are separate

The deterministic planner makes integration tests repeatable. Portkey is an optional planner adapter. Passing orchestration tests does not establish prompt-injection resistance of a live model. That requires separate experiments with diverse adversarial inputs.
