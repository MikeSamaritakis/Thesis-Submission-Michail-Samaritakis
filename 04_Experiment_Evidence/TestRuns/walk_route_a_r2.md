# WALK_ROUTE_A_R2

## Basic Info

- App: OIPASFT
- Date: 02-06-2026
- Start time: 22:51
- End time: 22:52
- Operator: Michail Samaritakis
- Scanner phone: Ulefone Armor 22
- Tracking mode: Fusion
- Adaptive tuning: Off
- Session ID: 20260602_225152_115
- Raw log file: TestLogs/20260602_225152_115_user_position_history.json
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
- Duration: approx 55 seconds logged

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

- Matching pulled log is `20260602_225152_115_user_position_history.json`.
- Pulled log contains 16 samples.
- App trial label is `WALK`.
- Adaptive tuning was off.
- Sample 0 is `UNLABELED`, before the route label was applied.
- Sample 1 is the manual `ANCHOR` / `MAP_TAP` at approximately `(0.6891, 0.6410)`.
- Samples 4, 7, 9, and 15 are `FUSION`, `OK`, `hasBleFix=true`, and `filteredCount=3`.
- Samples 2, 3, 5, 6, 8, 10, 13, and 14 are `DEAD_RECKONING`.
- Samples 11 and 12 show `LESS_THAN_3_BEACONS`.
- BLE fix ratio is `0.3125`, so 5 of 16 samples had accepted BLE fixes including the initial unlabeled sample.
- This run should be used as qualitative path evidence only, not RMSE accuracy evidence.

## Problems

- BLE support was intermittent, especially after the midpoint/end section.
- The rendered path repeatedly reached the upper map boundary near `y=2.50`, which may indicate PDR drift or heading instability between BLE fixes.
- One initial sample was logged as `UNLABELED`; use the `WALK` samples after the anchor for route interpretation.

## Path Behavior Summary

- The run captured a clearer Route A trace than `WALK_ROUTE_A_R1`, with more accepted BLE fixes and a visible manual anchor. However, the path still shows intermittent BLE loss and boundary-clamped dead reckoning. Use this run to discuss route continuity and drift correction behavior, but do not use it for numerical accuracy unless exact walking waypoint timestamps are annotated later.
