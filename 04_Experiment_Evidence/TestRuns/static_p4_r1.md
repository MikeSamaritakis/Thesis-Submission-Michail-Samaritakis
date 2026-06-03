# STATIC_P4_R1

## Basic Info

- App: OIPASFT
- Date: 02-06-2026
- Start time: 19:08
- End time: 19:09
- Operator: Michail Samaritakis
- Scanner phone: Ulefone Armor 22
- Tracking mode: Fusion
- Session ID: 20260602_190839_014
- Raw log file: TestLogs/20260602_190839_014_user_position_history.json
- Status: valid

## Target

- Type: Static accuracy
- Point: P4
- Ground truth: (0.50, 2.00) meters
- Manual anchor: row=3, col=1, approx (0.625, 1.875) meters
- Duration: approx 36 seconds logged


## Annotation Row

| sessionId | sampleIndex | groundTruthXMeters | groundTruthYMeters | truthSource | evaluationEligible | note |
|---|---|---:|---:|---|---|---|
| 20260602_190839_014 | 0, 1 | 0.50 | 2.00 | STATIC_POINT | true | STATIC_P4_R1 |

## Notes

- Matching pulled log is `20260602_190839_014_user_position_history.json`.
- Pulled log contains 2 samples.
- App trial label is `STATIC`, which is acceptable because OIPASFT uses built-in trial labels; this README file identifies the specific external run as `STATIC_P4_R1`.
- Samples 0 and 1 are `FUSION`, `OK`, `hasBleFix=true`, and `filteredCount=3`.
- Adaptive tuning was off for this run.

## Problems

- Only two `STATIC` evaluation samples were recorded for this run.





