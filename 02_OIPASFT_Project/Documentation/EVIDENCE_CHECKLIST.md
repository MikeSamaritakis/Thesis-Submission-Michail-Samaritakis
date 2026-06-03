# Evidence Collection Checklist

Use this checklist before generating final thesis results.

## Before Each Run

- Confirm `current_test.json` is the active calibration.
- Confirm the physical room dimensions match the JSON room dimensions: `2.50 m x 2.50 m`.
- Confirm beacon labels and positions match the active JSON:
  - `go6G`: row 0, col 0, `x=0.00`, `y=0.00`
  - `yiBb`: row 4, col 0, `x=0.00`, `y=2.50`
  - `SamA15`: row 4, col 4, `x=2.50`, `y=2.50`
  - `Red10`: row 2, col 2, `x=1.25`, `y=1.25`
- Confirm Ulefone Armor 22 is the scanner/detector.
- Set the closest available dashboard trial label. Detailed repeat IDs such as `STATIC_P1_R1` are recorded in the `TestRuns/` documentation.

## Static Point Trial

- Mark the true physical point.
- Set the app trial label to `STATIC`, then record the detailed repeat ID, for example `STATIC_P3_R2`, in the matching `TestRuns/` file.
- Use anchor taps carefully; anchors reset the tracker and are not accuracy samples.
- Stand still until several samples are recorded.
- Watch Logcat for:
  - `STATIC_ANCHOR_HOLD` when BLE tries to pull away while stationary
  - `BLE_DISTANCE` for active beacons
  - `BLE_RESIDUAL` for accepted/rejected BLE solves

## Walking Trace Trial

- Set the app trial label to `WALK`, then record the detailed route/repeat ID, for example `WALK_ROUTE_A_R1`, in the matching `TestRuns/` file.
- Anchor at the start point.
- Walk the route naturally.
- Do not count unknown movement path samples as ground truth.

## After Collection

Run:

```powershell
.\scripts\pull-test-logs.ps1
powershell.exe -ExecutionPolicy Bypass -File .\scripts\generate-thesis-report.ps1
powershell.exe -ExecutionPolicy Bypass -File .\scripts\check-delivery-readiness.ps1
```

If truth is annotated after collection, copy
`TestLogs/evaluation_annotations_template.csv` to
`TestLogs/evaluation_annotations.csv`, fill one row per independently labeled
sample, and rerun `generate-thesis-report.ps1`.

Final accuracy acceptance requires at least one row in `thesis_sessions_summary.csv` with `evaluationSamples > 0`, produced by static or externally annotated ground-truth samples rather than anchors.
