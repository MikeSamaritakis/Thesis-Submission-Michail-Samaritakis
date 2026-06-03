# STATIC_P1_R1

## Basic Info

- App: OIPASFT
- Date: 02-06-2026
- Start time: 18:04
- End time: 18:06
- Operator: Michail Samaritakis
- Scanner phone: Ulefone Armor 22
- Tracking mode: Fusion
- Session ID: 20260602_180444_557
- Raw log file: TestLogs/20260602_180444_557_user_position_history.json
- Status: rejected

## Target

- Type: Static accuracy
- Point: P1
- Ground truth: (0.50, 0.50) meters
- Manual anchor: row=1, col=1, approx (0.625, 0.625) meters
- Duration: approx 42 seconds logged


## Annotation Row

| sessionId | sampleIndex | groundTruthXMeters | groundTruthYMeters | truthSource | evaluationEligible | note |
|---|---|---:|---:|---|---|---|
| 20260602_180444_557 | N/A | 0.50 | 0.50 | STATIC_POINT | false | STATIC_P1_R1_REJECTED_NO_VALID_SAMPLE |

## Notes

- Matching pulled log is `20260602_180444_557_user_position_history.json`.
- Pulled log contains 4 samples.
- App trial label is `STATIC`, which is acceptable because OIPASFT uses built-in trial labels; this README file identifies the specific external run as `STATIC_P1_R1`.
- Sample 0 is a manual anchor at approximately `(0.6357, 0.6197)`, close to the planned P1 anchor `(0.625, 0.625)`.
- Samples 1-3 are `FUSION` but show `LESS_THAN_3_BEACONS`.
- No accepted BLE fix was recorded in this session.

## Problems

- This run should not be used for P1 accuracy metrics.
- No valid static evaluation sample was selected because the session has no BLE fixes and the rendered position stayed at the manual anchor.
- Do not add this annotation row to `evaluation_annotations.csv`.

