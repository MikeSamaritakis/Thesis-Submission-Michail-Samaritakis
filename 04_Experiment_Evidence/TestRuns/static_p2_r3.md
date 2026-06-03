# STATIC_P2_R3

## Basic Info

- App: OIPASFT
- Date: 02-06-2026
- Start time: 18:56
- End time: 18:58
- Operator: Michail Samaritakis
- Scanner phone: Ulefone Armor 22
- Tracking mode: Fusion
- Session ID: 20260602_185639_824
- Raw log file: TestLogs/20260602_185639_824_user_position_history.json
- Status: rejected

## Target

- Type: Static accuracy
- Point: P2
- Ground truth: (2.00, 0.50) meters
- Manual anchor: row=1, col=3, approx (1.875, 0.625) meters
- Duration: approx 120 seconds logged


## Annotation Row

| sessionId | sampleIndex | groundTruthXMeters | groundTruthYMeters | truthSource | evaluationEligible | note |
|---|---|---:|---:|---|---|---|
| 20260602_185639_824 | N/A | 2.00 | 0.50 | STATIC_POINT | false | STATIC_P2_R3_REJECTED_NO_VALID_SAMPLE |

## Notes

- Matching pulled log is `20260602_185639_824_user_position_history.json`.
- Pulled log contains 22 samples.
- App trial label is `STATIC`, which is acceptable because OIPASFT uses built-in trial labels; this README file identifies the specific external run as `STATIC_P2_R3`.
- No accepted BLE fix was recorded in this session.
- Most samples show `LESS_THAN_3_BEACONS`.
- Filtered beacon count is usually `1`, so the app did not have enough accepted beacons for trilateration.
- Samples 8-9 are `DEAD_RECKONING`, not independent static accuracy samples.
- Sample 18 is a manual anchor and must not be used for accuracy metrics.

## Problems

- This run should not be used for P2 accuracy metrics.
- No valid static evaluation sample was selected.
- Do not add this annotation row to `evaluation_annotations.csv`.





