# Thesis Information Form

## 1. Basic Information

**Thesis title:** *Optimizing Indoor Positioning Accuracy through Sensor Fusion Techniques*

**Student name:** *Michail Samaritakis*

**Department / University:** *Computer Science Department / University of Crete*

**Supervisor:** *Kostas Magoutis*

**Additional supervisor / committee members:** *Chrysostomos Zegkinis*

**Date / semester:** *Spring 2026*

**Language of thesis:** *English*

**Preferred writing style:** Formal academic / technical, especially for computer science and systems implementation topics.


## 2. Thesis Brief

**Main goal of the thesis:** The thesis aims to improve indoor positioning accuracy by combining Bluetooth Low Energy (BLE) beacon measurements with smartphone motion sensors. The practical objective is to produce a more stable and accurate estimate than BLE-only trilateration in a small indoor environment.

**Problem being solved:** BLE RSSI-based indoor positioning is noisy. Received signal strength varies because of multipath propagation, obstacles, body shadowing, antenna orientation, device differences, and short-term environmental changes. As a result, simple RSSI-to-distance conversion followed by trilateration can produce unstable position estimates, jumps, and missing fixes.

**Why this problem is important:** GPS is usually unavailable or unreliable indoors, but indoor location is useful for navigation, asset tracking, context-aware mobile applications, accessibility support, smart buildings, and emergency response. BLE beacons are attractive because they are low cost and widely supported by smartphones, but their accuracy must be improved before they can be used reliably.

**What already exists / current approaches:** Common indoor positioning approaches include Wi-Fi fingerprinting, BLE RSSI fingerprinting, BLE trilateration, inertial pedestrian dead reckoning (PDR), ultra-wideband (UWB), RFID, magnetic-field methods, and hybrid sensor-fusion systems. BLE systems commonly estimate distance from RSSI using a log-distance path-loss model, then estimate position using proximity, centroid, weighted centroid, fingerprinting, or trilateration. More advanced systems smooth RSSI values or fuse BLE with inertial sensors using Kalman filters or related filtering methods.

**Limitations of existing approaches:** BLE-only trilateration depends heavily on stable RSSI and good beacon geometry. Fingerprinting can improve accuracy but requires labor-intensive site surveys and may need recalibration when the environment changes. PDR can produce smooth short-term movement but drifts over time without an absolute correction source. Hybrid approaches reduce some of these weaknesses, but they require careful parameter tuning, reliable ground truth, and clear handling of invalid BLE fixes.

**What my system or method improves:** The system improves over a naive BLE-only baseline by averaging RSSI, rejecting physically implausible beacon distances, checking trilateration residuals, rejecting large position jumps, clamping positions to the room boundary, and using smartphone sensor/PDR information inside a Kalman-style fusion pipeline. A runtime adaptive mode can also tune sensor confidence from RSSI noise, residuals, beacon geometry, and motion stability; this is treated as online reliability estimation rather than ground-truth calibration. This adaptive mode exists in the implemented prototype, but it was kept disabled for the controlled final evaluation and remains untested as thesis evidence.

**Main contribution of my thesis:** The contribution is an Android-based experimental indoor-positioning prototype that integrates Kontakt BLE beacon scanning, calibrated room/beacon configuration, RSSI trilateration, smartphone sensor-based dead reckoning, Kalman filtering, live visualization, structured session logging, and thesis-oriented evaluation scripts.

**Short description of the final system:** The final system is an Android application. It loads a JSON room calibration file, scans BLE beacons, estimates a BLE position with trilateration, updates a fused position using phone sensors and a Kalman filter, displays BLE/PDR/fusion tracking modes on a room map, and writes CSV/JSON logs that can be converted into thesis tables and SVG charts.


## 3. Technologies Used

**Programming languages:** Java and Kotlin, mainly developed in Android Studio. PowerShell is used for log pulling and report generation scripts.

**Frameworks / libraries:** Android SDK, AndroidX Core/AppCompat/CardView/Lifecycle, Kontakt.io Android SDK 7.2.12, org.json 20210307, JUnit 4.13.2, Android Gradle Plugin 8.13.2, Kotlin plugin 2.0.0.

**Hardware used:** Ulefone Armor 22 as the scanner/detector phone, Xiaomi Redmi Note 10 and Samsung Galaxy A15 as optional simulated BLE advertisers, and physical Kontakt BLE beacons used as fixed transmitters in the calibrated room.

**Sensors used:** BLE radio plus smartphone motion/orientation sensors: step detector, step counter, game rotation vector or rotation vector, gyroscope, linear acceleration, gravity, accelerometer, magnetometer, and pressure sensor where available.

**Positioning technologies used:** BLE RSSI ranging, BLE trilateration, pedestrian dead reckoning, manual map anchors, and Kalman-filter-based sensor fusion.

**Databases / storage:** No external database is used. Calibration is stored in JSON assets, and experiment output is stored as timestamped CSV and JSON files in the Android app external files directory and copied to the project `TestLogs/` folder.

**Visualization tools:** Android custom dashboard/map view for live visualization, CSV tables for analysis, and generated SVG charts for path traces and error-over-time plots.

**Other tools:** Android Studio, Gradle, Logcat, PowerShell scripts, Git, the BLE Simulator helper application, and generated thesis report files under `TestLogs/Reports/`.


## 4. System Architecture

**System overview:** The system is a single-device Android prototype. The phone scans known BLE beacons, converts RSSI measurements to distance estimates, computes an absolute BLE position when enough valid beacons are available, combines this with relative motion from phone sensors, renders the result in the UI, and saves each session for evaluation.

**Main components/modules:** `MainActivity` coordinates lifecycle, permissions, scanning, UI, positioning, and logging. `PositioningEngine` decides whether BLE data is acceptable. `TrilaterationUtils` computes BLE position. `SensorManager` handles step/heading/PDR and Kalman fusion. `KalmanPositionFilter` implements the 2D constant-velocity filter. `UserPositionHistory` writes CSV/JSON logs. `TrackingDashboardView` displays tracking modes and coordinates. `JsonOps` loads calibration data.

**Input data:** BLE beacon identifiers and RSSI values, calibrated beacon coordinates, room dimensions, path-loss parameters, smartphone sensor events, and manual map taps.

**Output data:** Live rendered position, BLE-only position when available, mode/reason diagnostics, CSV position history, JSON session history, summary CSV files, and SVG charts.

**How BLE / beacon data is collected:** The app uses the Kontakt.io SDK to scan for configured BLE beacons. For each beacon, the app stores RSSI samples, computes smoothed/averaged RSSI, estimates distance with the configured path-loss exponent and `rssi1Meter`, and passes fresh beacons to the positioning engine.

**How smartphone / sensor data is collected:** Android sensor events are registered through the platform sensor manager. Step detector/counter events are used when available. Heading is derived from game rotation vector or rotation vector, with fallback support from accelerometer/magnetometer data. Gyroscope, linear acceleration, gravity, pressure, pitch, and roll are used for motion-state detection and step rejection.

**How data is processed:** BLE samples are filtered by distance gates, stale/missing beacon logic, residual checks, room bounds, and jump rejection. Accepted BLE fixes correct the Kalman state. PDR predicts motion using step length and heading. The final rendered position is clamped to the room and logged with mode and diagnostic metadata.

**How data is stored:** The active calibration is stored in `app/src/main/assets/CalibrationTests/current_test.json`. Runtime logs are written as timestamped CSV and JSON files. Copied device logs are stored under `TestLogs/`, and generated analysis output is stored under `TestLogs/Reports/`.

**How results are displayed / visualized:** The Android dashboard shows selected tracking mode, BLE/fused coordinates, state/reason information, and a room map. The thesis report script generates session summaries, sample-level CSV files, path-trace SVGs, and error SVGs when evaluation samples exist. Fixed defense figures are stored under `Documentation/figures/`.

**Important files, classes, scripts, or functions:** `MainActivity.java`, `PositioningEngine.java`, `KalmanPositionFilter.java`, `SensorManager.java`, `TrilaterationUtils.java`, `UserPositionHistory.java`, `TrackingDashboardView.java`, `current_test.json`, `scripts/pull-test-logs.ps1`, `scripts/generate-thesis-report.ps1`, and `scripts/check-delivery-readiness.ps1`.


## 5. Algorithms and Methodology

**Main algorithms used:** RSSI-to-distance conversion with a log-distance path-loss model, RSSI averaging, trilateration using three BLE beacons, pedestrian dead reckoning from step and heading estimates, outlier rejection, room-boundary constraints, and a 2D constant-velocity Kalman filter.

**Why these algorithms were selected:** BLE trilateration provides an absolute position estimate without a site-wide fingerprinting survey. PDR provides short-term smooth motion between BLE corrections. Kalman filtering is appropriate because it combines noisy absolute measurements with predicted motion while preserving an estimate of uncertainty.

**BLE-only positioning method:** The app filters live BLE beacons, estimates distance from averaged RSSI, requires at least three valid beacons, trilaterates the phone position, rejects results with high residuals or invalid room bounds, and logs the accepted BLE coordinate.

**Sensor fusion method:** The fused method uses manual anchors or accepted BLE fixes to initialize the tracking state. Step events predict displacement based on heading and step length. Accepted BLE fixes update the Kalman filter and correct accumulated PDR drift. When adaptive mode is enabled from the dashboard, BLE and PDR updates are assigned dynamic confidence values; low-confidence measurements affect the Kalman state less strongly than high-confidence measurements. In the reported controlled tests, adaptive mode remains disabled, so results correspond to the fixed Fusion configuration.

**Particle filter / Kalman filter / other filtering method:** The current implementation uses a constant-velocity 2D Kalman filter, not a particle filter. The state is modeled independently for x and y axes with position and velocity. Manual anchors reset the state with low variance; BLE initializes or updates the state with higher variance; steps add displacement and uncertainty.

**Step-by-step algorithm process:**
1. Load room dimensions, beacon positions, RSSI calibration, and model parameters from JSON.
2. Scan BLE beacons and maintain recent RSSI samples.
3. Convert averaged RSSI to distance using the path-loss model.
4. Reject unusable beacons and require at least three valid beacons.
5. Trilaterate a raw BLE position.
6. Reject BLE positions with high residuals, invalid bounds, or excessive jumps.
7. Read smartphone step and heading sensors to predict relative movement.
8. Update the Kalman filter with PDR predictions and accepted BLE fixes.
9. Render the selected BLE/PDR/fusion mode and save the sample.
10. Use report scripts to compute summary statistics and generate figures.

**Important formulas:** RSSI distance model: `d = 10 ^ ((RSSI_1m - RSSI) / (10 * n))`, where `n` is the path-loss exponent. Position error: `e = sqrt((x_est - x_truth)^2 + (y_est - y_truth)^2)`. RMSE: `sqrt(mean(e^2))`. PDR step displacement: `dx = stepLength * sin(correctedHeading)` and `dy = stepLength * cos(correctedHeading)`.

**Parameters used:** Current room size is 2.50 m by 2.50 m. Current beacon positions are go6G at (0.00, 0.00), yiBb at (0.00, 2.50), SamA15 at (2.50, 2.50), and Red10 at (1.25, 1.25). Current path-loss exponent is 2.0. RSSI average window is 3. Step length is 0.62 m, with min/max 0.35 m and 0.72 m. Heading offset is 146.0 degrees. BLE min distance is 0.08 m, distance margin is 1.25 m, boundary clamp margin is 0.20 m, max jump is 2.5 m, and residual rejection threshold is 0.85 m. Adaptive tuning starts disabled each launch and can be enabled with the dashboard button, but it was not tested in the controlled final evaluation.

**Assumptions made:** Beacon coordinates are known and fixed. The room is treated as a 2D plane. Manual taps are anchors that reset the tracker. Unknown movement samples are path traces, not ground truth. RSSI-to-distance behavior is approximated by a path-loss model even though indoor propagation is noisy.

**Limitations of the method:** Three beacons provide minimal trilateration redundancy. RSSI can fluctuate strongly in small rooms. PDR depends on heading calibration and step detection quality. Manual anchors are not independent accuracy evidence because they reset the tracker. The final dataset contains evaluation-eligible static samples, but the accepted sample counts are uneven across test points and the walking routes do not provide continuous independent ground truth.


## 6. Implementation Details

**Development environment:** Android Studio with Gradle, Java 11 compatibility, compile SDK 34, min SDK 26, target SDK 34, and app package `com.example.connectiontest`.

**Application structure:** The app uses a View-based Android structure. Runtime code is mostly Java, with Kotlin/Android template test files still present. Calibration assets are under `app/src/main/assets/CalibrationTests/`, Java source under `app/src/main/java/com/example/connectiontest/`, and thesis logs under `TestLogs/`.

**Backend implementation:** There is no remote backend. The device performs scanning, positioning, fusion, visualization, and logging locally. Data export is file-based through CSV/JSON logs.

**Frontend / visualization implementation:** The UI is built with custom Android views. `TrackingDashboardView` provides mode selection for `BLE_ONLY`, `PDR_ONLY`, and `FUSION`, displays tracking diagnostics, and draws the room/map state.

**Database models / tables:** No database tables are used. The equivalent data models are JSON structures and CSV rows. Important fields include timestamp, mode, reason, rendered x/y, BLE x/y, ground truth x/y, truth source, evaluation eligibility, error, live beacon count, and filtered beacon count.

**Data flow from collection to result:** BLE scanner and sensor listeners collect raw data. `JsonOps` supplies calibration. `PositioningEngine` produces accepted or rejected BLE fixes. `SensorManager` updates the Kalman/PDR state. `MainActivity` updates the dashboard and sends each sample to `UserPositionHistory`. Scripts later convert JSON logs into summary CSV and SVG outputs.

**User interface features:** Runtime permission handling, live BLE scanning state, room map display, manual anchor taps, tracking mode selection, adaptive tuning toggle, start/stop trial recording, trial labels, log export, coordinate display, and diagnostic mode/reason display.

**Problems faced during implementation:** Main issues include unstable RSSI, poor trilateration when fewer than three beacons pass filtering, heading-frame mismatch between Android azimuth and room coordinates, false step detection, BLE outliers, and separating true ground-truth samples from manually forced anchor positions.

**How these problems were solved:** RSSI averaging, distance gates, residual rejection, jump rejection, room clamping, heading offset tuning, stricter custom step thresholds, Kalman filtering, and explicit exclusion of manual anchors from accuracy metrics were added. The report script only treats evaluation-eligible static or externally annotated truth samples as accuracy evidence.


## 7. Experiments and Evaluation

**Experiment location:** Small indoor test room used for controlled room-scale testing.

**Description of test environment / floor plan:** Current calibration uses a 2.50 m by 2.50 m room represented as a 5 by 5 grid. The coordinate origin is at row 0, col 0, mapped to x=0, y=0. Rows increase with y and columns increase with x.

**Number of configured BLE transmitters:** Four in the active calibration: go6G, yiBb, SamA15, and Red10. SamA15 and Red10 are smartphone-simulator identities and should be described as helper transmitters, not as the main thesis contribution.

**Beacon positions:** go6G: row 0, col 0, x=0.00 m, y=0.00 m. yiBb: row 4, col 0, x=0.00 m, y=2.50 m. SamA15: row 4, col 4, x=2.50 m, y=2.50 m. Red10: row 2, col 2, x=1.25 m, y=1.25 m.

**Devices used:** Ulefone Armor 22 is the scanner/detector device. Xiaomi Redmi Note 10 and Samsung Galaxy A15 are additional test devices used as simulated BLE advertisers when required.

**Number of test paths / scenarios:** The final copied log set contains 28 JSON sessions. The documented final evidence set contains 15 static accuracy attempts, 6 walking route attempts, and 1 app restart logging sanity test. Nine static sessions produced evaluation-eligible samples, Route A produced qualitative walking evidence, Route B attempts were rejected or diagnostic because BLE support was insufficient, and the logging sanity test passed.

**Ground truth method:** Manual anchors on known map coordinates reset the tracker and are not counted as accuracy samples. Accuracy evaluation requires static or externally annotated ground-truth samples that do not simply force the tracker state to the truth coordinate.

**Metrics used:** Mean error, median error if added later, maximum error, RMSE, BLE fix ratio, number of evaluation samples, BLE raw mean/RMSE, fused mean/RMSE, and accuracy improvement percentage.

**Baseline method:** BLE-only accepted trilateration before Kalman smoothing, represented in the report script by `bleRawMeanErrorMeters` and `bleRawRmseMeters`.

**Proposed method:** Fused BLE plus PDR/Kalman tracking, represented by rendered/fused position error fields such as `meanErrorMeters` and `rmseMeters` for evaluation-eligible samples.

**Experiment procedure:**
1. Place the active BLE transmitters at the calibrated room coordinates.
2. Start the Android app and load `current_test.json`.
3. Select BLE, PDR, or Fusion tracking mode.
4. Tap a known point on the map to initialize the tracker when required.
5. Stand at known static points or walk between known route points.
6. Do not count anchor rows as accuracy samples.
7. Copy JSON logs from the device with `scripts/pull-test-logs.ps1`.
8. Generate thesis CSV/SVG outputs with `scripts/generate-thesis-report.ps1`.

**Data collected:** Timestamped positions, rendered/fused coordinates, BLE coordinates, ground-truth coordinates when available, mode/reason, live beacon counts, filtered beacon counts, BLE-fix availability, and error values for truth samples.

**Graphs / tables available:** `TestLogs/Reports/thesis_sessions_summary.csv`, `TestLogs/Reports/thesis_samples.csv`, and per-session SVG path traces. Error SVGs are generated only when evaluation-eligible samples exist.


## 8. Results

**BLE-only results:** For the 46 evaluation-eligible static samples, the accepted raw BLE trilateration baseline has mean error 0.7554 m, RMSE 0.8911 m, and maximum error 1.9353 m. These values come from accepted BLE fixes in the final Fusion-mode static tests, not from a separately repeated BLE-only experiment.

**Sensor fusion results:** The final Fusion-mode static dataset contains 46 evaluation-eligible samples across 9 valid static sessions. The fused/rendered position has mean error 0.7245 m, RMSE 0.8543 m, and maximum error 1.9315 m. Route A walking repeats produced qualitative path traces with intermittent BLE correction. Route B repeats were rejected as validation evidence because accepted BLE support was absent or too sparse.

**Comparison between methods:** On the final static evaluation samples, fused/rendered RMSE is 0.8543 m compared with raw accepted BLE RMSE of 0.8911 m. This is an approximate 4.1% RMSE reduction for the accepted static samples. The claim should remain conservative because the dataset is small, sample counts are uneven, and all quantitative samples come from Fusion-mode static trials.

**Average error:** The final generated report gives an overall fused mean error of 0.7245 m across 46 evaluation-eligible static samples.

**Maximum error:** The final generated report gives an overall fused maximum error of 1.9315 m across 46 evaluation-eligible static samples.

**Accuracy improvement:** Using the generated raw BLE baseline and fused/static results, `(0.8911 - 0.8543) / 0.8911 * 100%` gives approximately 4.1% RMSE reduction on the accepted static evaluation samples.

**Important observations:** BLE fix availability depends strongly on whether at least three transmitters pass filtering. Some static attempts were rejected because no accepted BLE fixes were recorded, and Route B repeats were not valid walking evidence for the same reason. Route A showed usable qualitative path traces but still contained dead-reckoning drift and boundary clamping when BLE support became intermittent.

**Cases where the method worked well:** The method works best when at least three transmitters are live, filtered count is 3 or higher, residuals are low, and the phone heading offset is correctly calibrated. BLE-only mode is useful for checking raw absolute fixes, and PDR-only mode is useful for checking step/heading behavior.

**Cases where the method did not work well:** The method is weaker when fewer than three beacons are accepted, when RSSI-derived distances disagree, when the phone heading is misaligned with the room coordinate frame, or when manual anchors are mistaken for independent ground truth.

**Explanation of the results:** The final logs show the expected behavior of a hybrid indoor-positioning system: accepted BLE fixes provide absolute correction, while PDR/fusion maintains continuity when BLE becomes unavailable. The quantitative result is based only on independently marked static points. Walking traces are discussed qualitatively because they do not contain continuous independent ground truth.


## 9. Related Work / Bibliography

**Paper/source 1:**
Title: A Survey of Indoor Localization Systems and Technologies
Authors: Faheem Zafari, Athanasios Gkelias, Kin K. Leung
Year: 2019
DOI/link: https://doi.org/10.1109/COMST.2019.2911558
Use in thesis: General background on indoor localization technologies, including RSSI, BLE, Wi-Fi, UWB, and sensor-based approaches.

**Paper/source 2:**
Title: An Improved BLE Indoor Localization with Kalman-Based Fusion: An Experimental Study
Authors: Yu-Chung Pu and Pei-Chun You
Year: 2017
DOI/link: https://doi.org/10.3390/s17050951
Use in thesis: Closely related BLE trilateration and dead-reckoning fusion study using Kalman filtering.

**Paper/source 3:**
Title: Real-Time Indoor Positioning Approach Using iBeacons and Smartphone Sensors
Authors: Liu Liu, Bofeng Li, Ling Yang, Tianxia Liu
Year: 2020
DOI/link: https://doi.org/10.3390/app10062003
Use in thesis: Reference for real-time fusion of iBeacon positioning and smartphone PDR/Kalman filtering.

**Paper/source 4:**
Title: Model-Based Localization and Tracking Using Bluetooth Low-Energy Beacons
Authors: F. Danis, A. T. Cemgil
Year: 2017
DOI/link: https://doi.org/10.3390/s17112484
Use in thesis: Reference for BLE beacon modeling, tracking, and the practical limitations of RSSI-based localization.

**Paper/source 5:**
Title: Smartphone based intelligent indoor positioning using fuzzy logic
Authors: Farid Orujov, Rytis Maskeliunas, Robertas Damasevicius, Wei Wei, Ye Li
Year: 2018
DOI/link: https://doi.org/10.1016/j.future.2018.06.030
Use in thesis: Useful comparison of BLE positioning algorithms such as proximity, centroid, weighted centroid, fingerprinting, and trilateration.


## 10. Figures, Tables, and Diagrams

**Figure 1:**
Description: System architecture diagram showing BLE scanner, sensor manager, positioning engine, Kalman filter, dashboard, and log storage.
File/image available: yes, `Documentation/figures/svg/system_architecture.svg`.

**Figure 2:**
Description: Room/floor-plan diagram with beacon positions go6G, yiBb, SamA15, and Red10.
File/image available: yes, `Documentation/figures/svg/current_room_beacon_layout.svg`.

**Figure 3:**
Description: Example path trace for final Route A session `20260602_225335_352`.
File/image available: yes, generated under `TestLogs/Reports/`.

**Table 1:**
Description: Beacon configuration table with ID, UUID, major, minor, x/y coordinates, and `rssi1Meter`.

**Table 2:**
Description: Algorithm parameter table with path-loss exponent, RSSI window, distance gates, residual threshold, step length, and Kalman noise values.

**Table 3:**
Description: Evaluation summary table with session ID, mode counts, sample counts, BLE fix ratio, mean error, RMSE, and maximum error.


## 11. Discussion

**What the results mean:** The preliminary results show that BLE-only positioning can produce accepted fixes, but fix availability changes across sessions. This justifies the fusion approach: BLE is useful as an absolute correction source, while PDR/Kalman tracking maintains continuity when BLE is unavailable or rejected.

**Why the proposed method is useful:** It provides a practical way to improve low-cost BLE positioning without requiring UWB hardware or a dense fingerprinting survey. The method also produces structured logs that make the experiment reproducible and easier to evaluate.

**Strengths of the system:** The system is fully implemented on Android, uses commodity smartphone sensors, separates calibration from code, includes multiple tracking modes, rejects suspicious BLE fixes, logs experiment metadata, and generates thesis-ready CSV/SVG outputs.

**Weaknesses of the system:** Reliable 2D trilateration still requires at least three simultaneously accepted transmitters, so accuracy depends heavily on active beacon geometry, RSSI stability, phone orientation, and careful test procedure.

**Technical limitations:** RSSI distance estimation is inherently noisy indoors. Adaptive tuning can estimate measurement reliability, but it cannot infer true calibration bias without reference information. Because adaptive tuning remains untested in the final evaluation, no accuracy claim is made for it. The Kalman filter assumes a simplified motion model. Step length is approximate. Heading offset may need device-specific tuning. BLE scan rate and Android sensor behavior can vary across devices.

**Experimental limitations:** The final logs contain 46 evaluation-eligible static samples, but the accepted samples are unevenly distributed across points. Several static repeats were rejected due to missing BLE fixes, and Route B did not provide successful BLE-assisted walking validation. The room is small, so small marking errors may represent a large percentage of the room size. Walking traces are qualitative because no continuous independent ground truth was recorded.

**Possible improvements:** Add more beacons, collect repeated static samples at marked points, add median error reporting, tune Kalman parameters using validation data, implement better heading calibration, compare against fingerprinting, and test in a larger room.


## 12. Conclusions

**Main conclusion:** BLE-only RSSI trilateration is useful but not reliable enough by itself for stable indoor positioning. A fusion approach that combines BLE corrections with smartphone PDR and Kalman filtering is a stronger design for practical indoor tracking.

**What was successfully implemented:** The Android prototype successfully loads calibration files, scans Kontakt BLE beacons, estimates BLE positions, performs PDR/Kalman fusion, displays live tracking modes, records structured CSV/JSON logs, and generates report-ready analysis files.

**What I learned from the thesis:** The project demonstrates that indoor positioning accuracy depends as much on calibration, filtering, experimental procedure, and ground-truth discipline as on the core algorithm.

**How the system can be useful:** The system can serve as a research prototype for indoor navigation, room-scale tracking experiments, BLE calibration studies, and future hybrid positioning work.

**Final summary paragraph ideas:** This thesis presents a smartphone-based indoor positioning system that improves on basic BLE trilateration through sensor fusion. By combining calibrated BLE beacon measurements, PDR, Kalman filtering, and structured evaluation logs, the system provides a practical foundation for studying indoor positioning accuracy and for comparing BLE-only and fused tracking methods.


## 13. Future Work

**Improvement 1:** Add more BLE beacons to improve geometry and provide redundancy beyond the minimum three-beacon case.

**Improvement 2:** Collect larger, controlled datasets with repeated static points and walking route traces so final mean error, RMSE, max error, and improvement percentages can be reported confidently.

**Improvement 3:** Improve automatic heading calibration so each device can align its sensor frame to the room coordinate frame with less manual tuning.

**Possible new features:** Live export of evaluation summaries, automatic experiment labels, support for multiple floor plans, comparison against fingerprinting, and optional cloud/database synchronization.

**Possible future experiments:** Test different beacon placements, different path-loss exponents, different RSSI averaging windows, more devices, larger rooms, obstacle conditions, and walking routes with independently marked ground-truth points.

**Possible real-world applications:** Indoor navigation, museum or campus guidance, smart-building context awareness, warehouse/asset tracking, accessibility support, and emergency personnel localization.


## 14. Extra Notes

**Anything else important:** The current generated summary contains 46 evaluation-eligible static samples across 9 sessions. Manual anchors are excluded because they force the rendered position to the tapped ground truth. Walking traces are retained as qualitative evidence only unless independently timed waypoint annotations are added. Use `TestLogs/EVIDENCE_CHECKLIST.md` during collection and `scripts/check-delivery-readiness.ps1` after report generation.

**Appendix note - BLE Simulator helper application:** A secondary Android application, the BLE Simulator, was developed as a supporting helper tool for the experimental setup. It was used to generate configurable iBeacon advertisements during testing and validation of the main Android thesis project, OIPASFT. The BLE Simulator was not the primary thesis contribution, but provided repeatable beacon identity parameters for controlled experiments. During experimentation, the Samsung Galaxy A15 and Xiaomi Redmi Note 10 were used as devices capable of acting as simulated BLE beacon advertisers, while the Ulefone Armor 22 was used as the scanner and detector device running the custom thesis application. Some tests used one simulated smartphone beacon at a time, while other tests used a mixed setup containing both physical BLE beacons and smartphones running the BLE Simulator software.

**Questions for supervisor:** Confirm whether the final evaluation should emphasize RMSE, mean error, median error, or all three. Confirm the minimum number of test points and repeated trials expected. Confirm whether comparison against fingerprinting or only BLE-only trilateration is required.

**Unclear parts that need help:** Exact experiment location wording, final committee member list, and final bibliography author formatting.
