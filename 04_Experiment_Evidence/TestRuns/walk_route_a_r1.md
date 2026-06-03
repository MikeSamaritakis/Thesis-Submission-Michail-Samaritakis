# WALK_ROUTE_A_R1

## Basic Info

- App: OIPASFT
- Date: 02-06-2026
- Start time: 21:07
- End time: 21:08
- Operator: Michail Samaritakis
- Scanner phone: Ulefone Armor 22
- Tracking mode: Fusion
- Adaptive tuning: Off
- Session ID: 20260602_210705_398
- Raw log file: TestLogs/20260602_210705_398_user_position_history.json
- Status: valid qualitative / limited BLE coverage

## Purpose

Route A checks whether the fused tracker follows a simple diagonal movement path across the room. The route starts near the lower-left/origin-side interior point, passes through the center beacon area, and ends near the upper-right interior point.

This route is qualitative path validation, not RMSE evidence, unless exact waypoint timestamps are annotated later.

## Route Definition

- Start point: P1, `(0.50, 0.50)` meters
- Midpoint: P3, `(1.25, 1.25)` meters
- End point: P5, `(2.00, 2.00)` meters
- Manual anchor point: row=1, col=1, approx `(0.625, 0.625)` meters
- Planned route: P1 -> P3 -> P5
- Duration: approx 62 seconds logged

## Physical Positioning

Stand at P1 before recording. P1 is 50 cm from the origin-side wall and 50 cm from the left/origin-side wall, assuming `go6G` is at `(0.00, 0.00)`.

Tap the manual anchor at row 1, col 1. This is the nearest grid anchor to P1, but the physical standing point remains P1 `(0.50, 0.50)`.

Walk slowly from P1 to P3, then from P3 to P5. Pause briefly at P3 and P5 so the path has visible waypoint behavior in the logs.

## Beacon Interaction Expected

- Near start: strongest interaction should be around `go6G`.
- Near midpoint: route passes close to `Red10`.
- Near end: route approaches the `SamA15` side of the room.

Expected useful behavior:

- `filteredCount` should usually be at least 3.
- `hasBleFix` should appear regularly.
- Rendered path should generally move from lower-left toward upper-right.
- Short jumps are acceptable, but the path should not remain stuck at the manual anchor.

## Procedure

1. Open OIPASFT.
2. Set tracking mode to Fusion.
3. Keep adaptive tuning Off.
4. Stand at P1 `(0.50, 0.50)`.
5. Tap manual anchor row 1, col 1.
6. Wait until the dashboard shows at least 3 filtered beacons.
7. Start recording.
8. Walk slowly from P1 to P3.
9. Pause at P3 for about 10 seconds.
10. Walk slowly from P3 to P5.
11. Pause at P5 for about 10 seconds.
12. Stop recording before moving away from P5.
13. Pull logs and record the session ID.

## Notes

- Matching pulled log is `20260602_210705_398_user_position_history.json`.
- Pulled log contains 10 samples.
- App trial label is `WALK`.
- Adaptive tuning was off.
- Samples 0 and 1 are `FUSION`, `OK`, `hasBleFix=true`, and `filteredCount=3`.
- Samples 2-6 and 9 are `DEAD_RECKONING`.
- Samples 7-8 show `LESS_THAN_3_BEACONS`.
- BLE fix ratio is `0.2000`, so only 2 of 10 samples had accepted BLE fixes.
- This run should be used as qualitative path evidence only, not RMSE accuracy evidence.

## Problems

- BLE coverage was limited after the first two samples.
- The rendered path reached boundary-clamped positions near `x=2.50`, which may indicate PDR drift or unstable heading after BLE fixes stopped.

## Path Behavior Summary

- The run captured the walking route as a path trace, but the path is limited as validation evidence because BLE fixes were sparse. The first accepted BLE positions were around `(2.21, 0.80)` and `(1.24, 1.94)`, followed mainly by dead reckoning and beacon-loss periods. Use this run to discuss path continuity, BLE loss, and PDR drift rather than numerical accuracy.
