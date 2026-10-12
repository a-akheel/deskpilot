# Two-minute demo walkthrough

1. Run `python3 scripts/dev.py` and open the printed URL.
2. Enter the operator token and investigate VPN. Point out the evidence ID and `awaiting_approval` state.
3. Enter the approver token and approve. The timeline includes `revalidating`, then `executing`, then `completed`.
4. Start another investigation and reject it. No action should execute.
5. Select “Not sure yet” and submit an ambiguous request. The workflow asks for a service rather than choosing one silently.
6. Open FailureLab's DeskPilot integration view. Inspect `lost-action-response`: the workflow completes and the ledger shows one action.
7. Show the worker-restart result. The before/after snapshots retain the same plan ID.

Explain the limitation: these are simulated service operations, and shared role tokens are not a substitute for production identity. A recording should show this actual flow; no voiceover or personal development story is fabricated.
