# STATIC_P1_R2

## Basic Info

- App: OIPASFT
- Date: 02-06-2026
- Start time: 18:12
- End time: 18:13
- Operator: Michail Samaritakis
- Scanner phone: Ulefone Armor 22
- Tracking mode: Fusion
- Session ID: 20260602_181209_489
- Raw log file: TestLogs/20260602_181209_489_user_position_history.json
- Status: rejected

## Target

- Type: Static accuracy
- Point: P1
- Ground truth: (0.50, 0.50) meters
- Manual anchor: row=1, col=1, approx (0.625, 0.625) meters
- Duration: approx 45 seconds logged


## Annotation Row

| sessionId | sampleIndex | groundTruthXMeters | groundTruthYMeters | truthSource | evaluationEligible | note |
|---|---|---:|---:|---|---|---|
| 20260602_181209_489 | N/A | 0.50 | 0.50 | STATIC_POINT | false | STATIC_P1_R2_REJECTED_NO_VALID_SAMPLE |

## Notes

- Matching pulled log is `20260602_181209_489_user_position_history.json`.
- Pulled log contains 2 samples.
- App trial label is `STATIC`, which is acceptable because OIPASFT uses built-in trial labels; this README file identifies the specific external run as `STATIC_P1_R2`.
- Sample 0 is a manual anchor at approximately `(0.6143, 0.6437)`, close to the planned P1 anchor `(0.625, 0.625)`.
- Sample 1 is `FUSION` but shows `LESS_THAN_3_BEACONS`.
- No accepted BLE fix was recorded in this session.

## Problems

- This run should not be used for P1 accuracy metrics.
- No valid static evaluation sample was selected because the session has no BLE fixes and the rendered position stayed at the manual anchor.
- Do not add this annotation row to `evaluation_annotations.csv`.




