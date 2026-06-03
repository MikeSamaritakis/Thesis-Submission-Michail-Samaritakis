# OIPASFT Main Thesis Project

OIPASFT is the main Android prototype developed for the thesis. It estimates indoor position from Bluetooth Low Energy beacon observations and phone sensor data, then stores position-history logs for later evaluation.

## What To Review

1. `Report.md`
   - Main technical report for the implemented prototype, evaluation method, and results.

2. `DEFENSE_BUNDLE.md`
   - Short defense-oriented summary of key claims, limitations, and evidence files.

3. `EVIDENCE_CHECKLIST.md`
   - Checklist describing how evidence was collected and interpreted.

4. `figures/`
   - Thesis figures in SVG and PNG form.

5. `../SUBMISSION_MANIFEST.md`
   - Current file manifest for the OIPASFT project package.

## Source Code

The Android Studio project is under:

```text
02_OIPASFT_Project/src/
```

Important implementation areas:

- `app/src/main/java/com/example/connectiontest/MainActivity.java`: Android lifecycle, permissions, BLE callbacks, sensor flow, logging, and dashboard updates.
- `app/src/main/java/com/example/connectiontest/Positioning/PositioningEngine.java`: BLE positioning logic, filtering, distance gates, trilateration, bounds checks, and jump rejection.
- `app/src/main/java/com/example/connectiontest/Positioning/KalmanPositionFilter.java`: position smoothing.
- `app/src/main/java/com/example/connectiontest/SensorManager/SensorManager.java`: phone sensor and step-detection handling.
- `app/src/main/assets/CalibrationTests/current_test.json`: active room, beacon, and model configuration used by the prototype.

## Build Artifact

The installable APK is provided under:

```text
02_OIPASFT_Project/APK/app-release.apk
```

## Experimental Evidence

The evidence used for thesis evaluation is kept in:

```text
04_Experiment_Evidence/
```

Key evidence files:

- `TestLogs/evaluation_annotations.csv`: externally supplied ground-truth annotations.
- `TestLogs/Reports/thesis_sessions_summary.csv`: session-level evaluation summary.
- `TestLogs/Reports/thesis_samples.csv`: sample-level evaluation table.
- `TestLogs/Reports/*_path.svg`: generated path plots.
- `TestLogs/Reports/*_error.svg`: generated error plots for sessions with evaluation-eligible samples.
- `TestRuns/*.md`: per-run notes for static and walking trials.

## Evaluation Notes

- Manual map taps are initialization or reset anchors, not accuracy samples.
- Walking traces are qualitative path evidence unless independent ground truth exists for each sample.
- Quantitative accuracy is based on static samples marked `evaluationEligible=true`.
- Adaptive tuning exists in the prototype but was disabled for the controlled evidence set.

## Reported Accuracy Summary

- Fused mean error: 0.7245 m
- Fused RMSE: 0.8543 m
- Fused max error: 1.9315 m
- Raw BLE mean error: 0.7554 m
- Raw BLE RMSE: 0.8911 m
- Raw BLE max error: 1.9353 m
