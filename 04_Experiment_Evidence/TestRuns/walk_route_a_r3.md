# WALK_ROUTE_A_R3

## Basic Info

- App: OIPASFT
- Date: 02-06-2026
- Start time: 22:53
- End time: 22:54
- Operator: Michail Samaritakis
- Scanner phone: Ulefone Armor 22
- Tracking mode: Fusion
- Adaptive tuning: Off
- Session ID: 20260602_225335_352
- Raw log file: TestLogs/20260602_225335_352_user_position_history.json
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
- Duration: approx 48 seconds logged

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

- Matching pulled log is `20260602_225335_352_user_position_history.json`.
- Pulled log contains 18 samples.
- App trial label is `WALK`.
- Adaptive tuning was off.
- Sample 0 is `UNLABELED`, before the route label was applied.
- Sample 1 is the manual `ANCHOR` / `MAP_TAP` at approximately `(0.6170, 0.6223)`.
- Samples 2, 3, 6, 9, and 15 are `FUSION`, `OK`, `hasBleFix=true`, and `filteredCount=3`.
- Samples 4, 5, 7, 8, 10, 13, 14, 16, and 17 are `DEAD_RECKONING`.
- Samples 11 and 12 show `LESS_THAN_3_BEACONS`.
- BLE fix ratio is `0.3333`, so 6 of 18 samples had accepted BLE fixes including the initial unlabeled sample.
- This run should be used as qualitative path evidence only, not RMSE accuracy evidence.

## Problems

- BLE support was intermittent, with beacon loss around samples 11 and 12.
- The rendered path repeatedly reached the upper map boundary near `y=2.50`, especially during dead reckoning.
- The final sample reached the map boundary at approximately `(2.50, 2.50)`, which suggests accumulated PDR drift after intermittent BLE correction.
- One initial sample was logged as `UNLABELED`; use the `WALK` samples after the anchor for route interpretation.

## Path Behavior Summary

- This run captured Route A with the clearest BLE coverage of the three repeats, but it still shows dead-reckoning drift and boundary clamping. The manual anchor was close to the planned P1-side anchor, and accepted BLE fixes occurred several times during the route. Use this run as qualitative evidence for walking behavior, BLE correction, and drift under intermittent beacon availability; do not use it for numerical route accuracy unless exact waypoint timestamps are annotated later.
