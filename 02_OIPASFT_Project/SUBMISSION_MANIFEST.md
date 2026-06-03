# OIPASFT Project Submission Manifest

This folder contains the final Android indoor-positioning prototype and its thesis evidence package.

## Folder Structure

```text
02_OIPASFT_Project/
├─ src/
│  ├─ README.md
│  ├─ LICENSE
│  ├─ settings.gradle
│  ├─ build.gradle
│  ├─ gradle.properties
│  ├─ gradlew
│  ├─ gradlew.bat
│  ├─ gradle/
│  ├─ scripts/
│  └─ app/
│     ├─ build.gradle
│     ├─ Report.md
│     └─ src/
│        ├─ main/
│        │  ├─ AndroidManifest.xml
│        │  ├─ java/
│        │  ├─ res/
│        │  └─ assets/CalibrationTests/current_test.json
│        └─ test/
│
├─ Documentation/
│  ├─ README.md
│  ├─ Report.md
│  ├─ DEFENSE_BUNDLE.md
│  ├─ EVIDENCE_CHECKLIST.md
│  └─ figures/
│     ├─ svg/
│     │  ├─ system_architecture.svg
│     │  ├─ current_room_beacon_layout.svg
│     │  ├─ dashboard_interface_overview.svg
│     │  ├─ android_permissions_overview.svg
│     │  ├─ heading_offset_calibration.svg
│     │  └─ logging_export_workflow.svg
│     └─ png/
│        ├─ current_room_beacon_layout.png
│        ├─ dashboard_interface_overview.png
│        ├─ android_permissions_overview.png
│        ├─ heading_offset_calibration.png
│        └─ logging_export_workflow.png
│
├─ Evidence/
│  ├─ scripts/
│  │  ├─ check-delivery-readiness.ps1
│  │  ├─ generate-thesis-report.ps1
│  │  ├─ pull-test-logs-after-app-exits.ps1
│  │  └─ pull-test-logs.ps1
│  └─ TestLogs/
│     ├─ evaluation_annotations.csv
│     ├─ evaluation_annotations_template.csv
│     ├─ *_user_position_history.json
│     ├─ *_user_position_history.csv
│     └─ Reports/
│        ├─ thesis_sessions_summary.csv
│        ├─ thesis_samples.csv
│        ├─ *_path.svg
│        └─ *_error.svg
│
└─ APK/
   ├─ app-release.apk
   └─ output-metadata.json
```

## Final Evidence Summary

- Raw copied JSON logs: 28
- Evaluation-eligible static samples: 46
- Evaluation sessions: 9
- Generated path SVGs: 25
- Generated error SVGs: 9
- Final readiness status: passed

## Accuracy Summary

- Fused mean error: 0.7245 m
- Fused RMSE: 0.8543 m
- Fused max error: 1.9315 m
- Raw BLE mean error: 0.7554 m
- Raw BLE RMSE: 0.8911 m
- Raw BLE max error: 1.9353 m

## Important Notes

- Quantitative accuracy comes only from static evaluation annotations and generated report CSVs.
- Manual anchors are initialization/reset events and are not accuracy samples.
- Route A is qualitative walking evidence.
- Route B attempts are retained as rejected/diagnostic evidence because BLE support was insufficient.
- Adaptive tuning exists in the prototype but was disabled and remains untested in the final controlled evidence set.

## Intentionally Excluded

The following are not included in the submission copy:

- `keystore`
- `local.properties`
- `.git/`
- `.gradle/`, `.gradle-user-home/`, `.gradle2/`
- `.idea/`, `.vscode/`
- build output caches such as `build/` and `app/build/`
