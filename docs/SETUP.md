# Setup and API

`python3 scripts/dev.py` builds the Kotlin application, installs the locked frontend dependencies, and starts Temporal, the IT fixture, the API/worker, and the dashboard. It generates role tokens unless they are supplied in the environment. It always removes Portkey variables from child processes to keep the demo free.

For manual setup, copy `.env.example` to `.env`, replace the example tokens, and load it in each terminal with `set -a; . ./.env; set +a`.

1. `temporal server start-dev --ip 127.0.0.1 --db-filename temporal.db`
2. `python3 demo/it_api.py`
3. `./gradlew run`
4. `cd web && pnpm install --frozen-lockfile --ignore-scripts && pnpm dev`

| API | Credential | Body/result |
|---|---|---|
| `GET /api/health` | Operator | API readiness and version |
| `POST /api/incidents` | Operator | `{ "service": "vpn", "description": "VPN unavailable" }`; returns incident ID |
| `GET /api/incidents/{id}` | Operator | Status, plan with ID, evidence, timeline, failure category |
| `POST /api/incidents/{id}/decision` | Approver | `{ "approved": true, "planId": "ID from snapshot" }` |

A decision response means the signal was submitted, not that execution completed. Poll the snapshot. Only the first decision for the current plan while awaiting approval is accepted. An empty service escalates for clarification; unsupported service names return 400.

Optional environment settings: `PORT` (8080), `IT_PORT` (8091), `IT_API_URL`, and `TEMPORAL_ADDRESS`. Set `DESKPILOT_API_URL` for Vite if using a nondefault API port.

## Live planning

Use manual setup and set `PORTKEY_API_KEY`, `PORTKEY_VIRTUAL_KEY`, and `PORTKEY_MODEL`. Calls incur provider charges. The gateway receives only the request and operational evidence; credentials remain in the adapter. Model output cannot bypass workflow approval, service scope, or evidence checks. A four-case local planner probe is bundled; it is a boundary demonstration, not a model-quality benchmark.

For direct local inference, run Ollama with `qwen3:4b`, set `LLM_PROVIDER=ollama` and `OLLAMA_MODEL=qwen3:4b`, then use manual setup. No Portkey key is needed. `./gradlew probeLocalModel` records four synthetic planner cases in `examples/local-planner-probe.json` without executing actions. Missing models, invalid output and provider errors fail explicitly rather than falling back to demo output. `scripts/dev.py` always forces the deterministic demo provider.

## Integration fixture controls

FailureLab's isolated runner sets `FIXTURE_CONTROL_TOKEN` only for its local test processes. This enables authenticated `/__test__/configure` and `/__test__/stats` routes on the fixture API. They inject faults and inspect the simulated action ledger. Never add those controls to a real IT adapter. The normal demo does not enable them.

## Compatibility

Version 2 changes the workflow signal contract to include a plan ID. Use a fresh local development database when upgrading from the initial prototype. Production workflow evolution would need explicit Temporal versioning or separate workflow types; this demo does not claim in-place migration support.
