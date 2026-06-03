# Thesis Defense Bundle

This folder contains the fixed, reusable thesis delivery material for the OIPASFT indoor-positioning prototype. Keep the claims conservative: final accuracy numbers must come only from evaluation-eligible samples generated after controlled data collection.

## Required Defense Artifacts

- System architecture figure: `Documentation/figures/svg/system_architecture.svg`
- Room and beacon layout figure: `Documentation/figures/svg/current_room_beacon_layout.svg`
- Session summary table: `TestLogs/Reports/thesis_sessions_summary.csv`
- Sample-level evidence table: `TestLogs/Reports/thesis_samples.csv`
- Optional external truth annotations: `TestLogs/evaluation_annotations.csv`
- Path trace figures: `TestLogs/Reports/*_path.svg`
- Error figures: `TestLogs/Reports/*_error.svg`, generated only when a session has evaluation-eligible samples
- BLE Simulator appendix text: `app/Report.md`, section 14

## Evidence Rules

- Manual anchors are setup/control events. Do not count them as positioning accuracy samples.
- Explicitly recorded static truth samples are the accuracy evidence.
- External annotations are allowed only when each annotated sample has an independently
  measured truth coordinate and `evaluationEligible=true`.
- Unknown samples between known truth points can show path continuity, but they are not RMSE evidence.
- Use `meanErrorMeters`, `rmseMeters`, and `maxErrorMeters` only when `evaluationSamples > 0`.
- Use `bleRawMeanErrorMeters` and `bleRawRmseMeters` as the accepted raw BLE baseline when available.

## Final Collection Set

- Static dataset: 5 marked locations, 3 repeated trials per location, documented as `STATIC_P1_R1` through `STATIC_P5_R3`.
- Walking dataset: Route A and Route B, 3 repeated attempts each. Route A produced qualitative path evidence; Route B attempts were rejected or diagnostic because BLE support was insufficient.
- App trial labels are limited to built-in labels such as `STATIC`, `WALK`, and `FUSION`; the detailed repeat IDs are recorded in `TestRuns/*.md`.
- Use the Ulefone Armor 22 as scanner/detector.
- Use Samsung Galaxy A15 and Xiaomi Redmi Note 10 as simulated advertisers only for mixed-beacon trials.

## Final Claim Template

Use wording like this for the final generated report:

> The prototype implements BLE-only positioning, PDR, and Kalman-based fusion, and records reproducible experiment logs. The final generated report contains 46 evaluation-eligible static samples across 9 valid static sessions. Manual anchors and walking traces are excluded from RMSE accuracy claims because anchors reset the tracker and walking samples do not have continuous independent ground truth.
