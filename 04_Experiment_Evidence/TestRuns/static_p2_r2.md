# STATIC_P2_R2

## Basic Info

- App: OIPASFT
- Date: 02-06-2026
- Start time: 18:55
- End time: 18:56
- Operator: Michail Samaritakis
- Scanner phone: Ulefone Armor 22
- Tracking mode: Fusion
- Session ID: 20260602_185503_790
- Raw log file: TestLogs/20260602_185503_790_user_position_history.json
- Status: valid

## Target

- Type: Static accuracy
- Point: P2
- Ground truth: (2.00, 0.50) meters
- Manual anchor: row=1, col=3, approx (1.875, 0.625) meters
- Duration: approx 43 seconds logged


## Annotation Row

| sessionId | sampleIndex | groundTruthXMeters | groundTruthYMeters | truthSource | evaluationEligible | note |
|---|---|---:|---:|---|---|---|
| 20260602_185503_790 | 2, 4 | 2.00 | 0.50 | STATIC_POINT | true | STATIC_P2_R2 |

## Notes

- Matching pulled log is `20260602_185503_790_user_position_history.json`.
- Pulled log contains 5 samples.
- App trial label is `STATIC`, which is acceptable because OIPASFT uses built-in trial labels; this README file identifies the specific external run as `STATIC_P2_R2`.
- Samples 2 and 4 are `FUSION`, `OK`, `hasBleFix=true`, and `filteredCount=3`.
- Sample 3 is excluded because it has `LESS_THAN_3_BEACONS`.
- Adaptive tuning was off for this run.

## Problems

- Only two `STATIC` evaluation samples were recorded for this run.





