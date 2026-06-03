# STATIC_P3_R2

## Basic Info

- App: OIPASFT
- Date: 02-06-2026
- Start time: 19:03
- End time: 19:05
- Operator: Michail Samaritakis
- Scanner phone: Ulefone Armor 22
- Tracking mode: Fusion
- Session ID: 20260602_190332_653
- Raw log file: TestLogs/20260602_190332_653_user_position_history.json
- Status: rejected

## Target

- Type: Static accuracy
- Point: P3
- Ground truth: (1.25, 1.25) meters
- Manual anchor: row=2, col=2, approx (1.25, 1.25) meters
- Duration: approx 95 seconds logged


## Annotation Row

| sessionId | sampleIndex | groundTruthXMeters | groundTruthYMeters | truthSource | evaluationEligible | note |
|---|---|---:|---:|---|---|---|
| 20260602_190332_653 | N/A | 1.25 | 1.25 | STATIC_POINT | false | STATIC_P3_R2_REJECTED_NO_VALID_SAMPLE |

## Notes

- Matching pulled log is `20260602_190332_653_user_position_history.json`.
- Pulled log contains 15 samples.
- App trial label is `STATIC`, which is acceptable because OIPASFT uses built-in trial labels; this README file identifies the specific external run as `STATIC_P3_R2`.
- No accepted BLE fix was recorded in this session.
- Most samples show `LESS_THAN_3_BEACONS`.
- Filtered beacon count is usually `1`, so the app did not have enough accepted beacons for trilateration.
- Sample 14 is `DEAD_RECKONING`, not an independent static accuracy sample.

## Problems

- This run should not be used for P3 accuracy metrics.
- No valid static evaluation sample was selected.
- Do not add this annotation row to `evaluation_annotations.csv`.





