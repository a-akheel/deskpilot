# Validation and evaluation boundaries

The local build compiles the Kotlin application and runs Temporal workflow tests for approval, rejection, expiry, and escalation. The fixture tests validate authentication, scoped service lookup, missing idempotency keys, and repeated restart requests. Both frontends are production-built with Vite.

The deterministic planner is a demonstration policy, not an LLM benchmark. Live Portkey credentials are deliberately not bundled. The companion evaluation project contains eight support scenarios including prompt injection, ambiguity, API timeout, malformed output, stale evidence, and authorization failure.

Before connecting real infrastructure, add organization identity, resource ownership checks, fresh evidence checks, signed/versioned approvals, persistent external audit export, and adapter-specific transactional idempotency tests.
