# WALK_ROUTE_B_R3

## Basic Info

- App: OIPASFT
- Date: 02-06-2026
- Start time: 23:00
- End time: 23:01
- Operator: Michail Samaritakis
- Scanner phone: Ulefone Armor 22
- Tracking mode: Fusion
- Adaptive tuning: Off
- Session ID: 20260602_230016_778
- Raw log file: TestLogs/20260602_230016_778_user_position_history.json
- Status: rejected / insufficient BLE support

## Purpose

Route B checks whether the fused tracker follows a multi-leg walking path across the room. The route starts near P4, crosses through the center point P3, moves toward P2, and finishes at P5.

This route is qualitative path validation, not RMSE evidence, unless exact waypoint timestamps are annotated later.

## Route Definition

- Start point: P4, `(0.50, 2.00)` meters
- Midpoint: P3, `(1.25, 1.25)` meters
- Midpoint: P2, `(2.00, 0.50)` meters
- End point: P5, `(2.00, 2.00)` meters
- Manual anchor point: row=3, col=1, approx `(0.625, 1.875)` meters
- Planned route: P4 -> P3 -> P2 -> P5
- Duration: approx 43 seconds logged

## Physical Positioning

Stand at P4 before recording. P4 is 50 cm from the left/origin-side wall and 200 cm from the origin-side wall, assuming `go6G` is at `(0.00, 0.00)`.

Tap the manual anchor at row 3, col 1. This is the nearest grid anchor to P4, but the physical standing point remains P4 `(0.50, 2.00)`.

Walk slowly from P4 to P3, then from P3 to P2, then from P2 to P5. Pause briefly at P3, P2 and P5 so the path has visible waypoint behavior in the logs.

## Beacon Interaction Expected

- Near start: interaction should include the upper-left side of the beacon layout.
- Near center: route passes close to `Red10`.
- Near P2/P5 side: route should approach the right-side beacons, including the `SamA15` side of the room.

Expected useful behavior:

- `filteredCount` should usually be at least 3.
- `hasBleFix` should appear regularly.
- Rendered path should generally move from upper-left toward center, then toward lower-right, then upper-right.
- Short jumps are acceptable, but the path should not remain stuck at the manual anchor.

## Procedure

1. Open OIPASFT.
2. Set tracking mode to Fusion.
3. Keep adaptive tuning Off.
4. Stand at P4 `(0.50, 2.00)`.
5. Tap manual anchor row 3, col 1.
6. Wait until the dashboard shows at least 3 filtered beacons.
7. Start recording.
8. Walk slowly from P4 to P3.
9. Pause at P3 for about 10 seconds.
10. Walk slowly from P3 to P2.
11. Pause at P2 for about 10 seconds.
12. Walk slowly from P2 to P5.
13. Pause at P5 for about 10 seconds.
14. Stop recording before moving away from P5.
15. Pull logs and record the session ID.

## Notes

- Matching pulled log is `20260602_230016_778_user_position_history.json`.
- Pulled log contains 11 samples.
- App trial label is `WALK`.
- Adaptive tuning was off.
- No `ANCHOR` / `MAP_TAP` sample appears in the generated sample report for this run.
- Sample 7 is `FUSION`, `OK`, `hasBleFix=true`, and `filteredCount=3`.
- Samples 0, 1, 2, 3, 4, 6, 8, and 9 are `DEAD_RECKONING`.
- Samples 5 and 10 show `LESS_THAN_3_BEACONS`.
- BLE fix ratio is `0.0909`, so only 1 of 11 samples had an accepted BLE fix.
- This run should not be used as walking validation evidence.

## Problems

- BLE support was too sparse for Route B validation.
- The expected manual anchor sample is missing from the report.
- The path reached map boundaries near `(2.50, 0.00)` and later near `(0.00, 2.50)`, indicating boundary clamping during dead reckoning.
- Because only one BLE fix was accepted, this repeat does not validate BLE-assisted walking behavior.

## Path Behavior Summary

- This run is rejected for Route B validation because it contains only one accepted BLE fix and no reported manual anchor sample. It can be mentioned as a failed/diagnostic walking attempt showing intermittent beacon correction and dead-reckoning boundary drift, but it should not be used as successful Route B validation evidence.
