# SXC Experimental Protocol

## Rule 1 — Observation mode
Never edit simulation logic.

Allowed:
- Run tests
- Add temporary instrumentation
- Read logs
- Produce reports

Forbidden:
- Modify equations
- Modify thresholds
- Modify update order
- Modify gameplay logic

## Rule 2 — Diagnosis mode
Never edit code.

Allowed:
- Explain bottlenecks
- Locate subsystem
- Suggest interventions

Forbidden:
- Any code modification

## Rule 3 — Intervention mode
Exactly one hypothesis.
Exactly one code change.
Exactly one commit.
Exactly one rerun.
Stop afterwards.

Every intervention must answer:
1. Files modified
2. Why
3. Hypothesis
4. Expected observable
5. Revert criteria
