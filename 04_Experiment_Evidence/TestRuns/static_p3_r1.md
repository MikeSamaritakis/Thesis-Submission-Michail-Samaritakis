# STATIC_P3_R1

## Basic Info

- App: OIPASFT
- Date: 02-06-2026
- Start time: 19:01
- End time: 19:03
- Operator: Michail Samaritakis
- Scanner phone: Ulefone Armor 22
- Tracking mode: Fusion
- Session ID: 20260602_190145_516
- Raw log file: TestLogs/20260602_190145_516_user_position_history.json
- Status: valid

## Target

- Type: Static accuracy
- Point: P3
- Ground truth: (1.25, 1.25) meters
- Manual anchor: row=2, col=2, approx (1.25, 1.25) meters
- Duration: approx 91 seconds logged


## Annotation Row

| sessionId | sampleIndex | groundTruthXMeters | groundTruthYMeters | truthSource | evaluationEligible | note |
|---|---|---:|---:|---|---|---|
| 20260602_190145_516 | 5 | 1.25 | 1.25 | STATIC_POINT | true | STATIC_P3_R1 |

## Notes

- Matching pulled log is `20260602_190145_516_user_position_history.json`.
- Pulled log contains 6 samples.
- App trial label is `STATIC`, which is acceptable because OIPASFT uses built-in trial labels; this README file identifies the specific external run as `STATIC_P3_R1`.
- Sample 5 is `FUSION`, `OK`, `hasBleFix=true`, and `filteredCount=3`.
- Samples 0-1 are excluded because they show `LESS_THAN_3_BEACONS`.
- Samples 2-4 are excluded because they are `DEAD_RECKONING`, not independent BLE/Fusion accuracy samples.
- Adaptive tuning was off for this run.
- Sample 5 rendered position is approximately `(1.7981, 1.9176)` meters.
- Ground truth is `(1.25, 1.25)` meters.

## Problems

- Only one `STATIC` evaluation sample was recorded for this run.





