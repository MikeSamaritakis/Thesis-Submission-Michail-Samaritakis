# STATIC_P1_R3

## Basic Info

- App: OIPASFT
- Date: 02-06-2026
- Start time: 18:23
- End time: 18:24
- Operator: Michail Samaritakis
- Scanner phone: Ulefone Armor 22
- Tracking mode: Fusion
- Session ID: 20260602_182358_677
- Raw log file: TestLogs/20260602_182358_677_user_position_history.json
- Status: valid

## Target

- Type: Static accuracy
- Point: P1
- Ground truth: (0.50, 0.50) meters
- Manual anchor: row=1, col=1, approx (0.625, 0.625) meters
- Duration: approx 55 seconds logged


## Annotation Row

| sessionId | sampleIndex | groundTruthXMeters | groundTruthYMeters | truthSource | evaluationEligible | note |
|---|---|---:|---:|---|---|---|
| 20260602_182358_677 | 3-11 | 0.50 | 0.50 | STATIC_POINT | true | STATIC_P1_R3 |

## Notes

- Matching pulled log is `20260602_182358_677_user_position_history.json`.
- Pulled log contains 14 samples.
- App trial label is `STATIC`, which is acceptable because OIPASFT uses built-in trial labels; this README file identifies the specific external run as `STATIC_P1_R3`.
- Samples 3-11 are `FUSION`, `OK`, `hasBleFix=true`, and `filteredCount=3`.
- Samples 12-13 are manual anchor samples and must not be used for accuracy metrics.
- Candidate static evaluation samples: 3, 4, 5, 6, 7, 8, 9, 10, 11.
- Operator confirmed the phone was physically standing still at P1 during samples 3-11.
- Average rendered position over candidate samples is approximately `(1.21, 1.70)` meters, far from P1 `(0.50, 0.50)`.

## Problems

- Manual anchor was recorded near the end of the session instead of before the static samples, but the operator confirmed samples 3-11 were collected while standing at P1.
- Do not use samples 12-13 because they are manual anchor rows.





