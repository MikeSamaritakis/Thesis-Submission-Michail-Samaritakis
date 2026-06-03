# STATIC_P2_R1

## Basic Info

- App: OIPASFT
- Date: 02-06-2026
- Start time: 18:52
- End time: 18:53
- Operator: Michail Samaritakis
- Scanner phone: Ulefone Armor 22
- Tracking mode: Fusion
- Session ID: 20260602_185250_796
- Raw log file: TestLogs/20260602_185250_796_user_position_history.json
- Status: valid

## Target

- Type: Static accuracy
- Point: P2
- Ground truth: (2.00, 0.50) meters
- Manual anchor: row=1, col=3, approx (1.875, 0.625) meters
- Duration: approx 79 seconds logged


## Annotation Row

| sessionId | sampleIndex | groundTruthXMeters | groundTruthYMeters | truthSource | evaluationEligible | note |
|---|---|---:|---:|---|---|---|
| 20260602_185250_796 | 2 | 2.00 | 0.50 | STATIC_POINT | true | STATIC_P2_R1 |

## Notes

- Matching pulled log is `20260602_185250_796_user_position_history.json`.
- Pulled log contains 3 samples.
- App trial label is `STATIC`, which is acceptable because OIPASFT uses built-in trial labels; this README file identifies the specific external run as `STATIC_P2_R1`.
- Sample 2 is `FUSION`, `OK`, `hasBleFix=true`, and `filteredCount=3`.
- Adaptive tuning was off for this run.
- Sample 2 rendered position is approximately `(2.0673, 1.2859)` meters.
- Ground truth is `(2.00, 0.50)` meters.

## Problems

- Only one `STATIC` evaluation sample was recorded for this run.





