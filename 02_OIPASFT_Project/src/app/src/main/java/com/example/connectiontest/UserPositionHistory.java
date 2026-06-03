package com.example.connectiontest;

import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.util.Log;

import com.example.connectiontest.BeaconManager.BeaconLocation;
import com.example.connectiontest.JsonOps.JsonOps;
import com.example.connectiontest.Positioning.KalmanPositionFilter;
import com.example.connectiontest.TrilaterationUtils.CoordinateUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Session logger for thesis evidence.
 *
 * Each app run gets a timestamped CSV and JSON file. CSV is convenient for quick
 * tables; JSON carries the full experiment context needed to reproduce and defend
 * the run later: room geometry, beacon calibration, algorithm settings, device info,
 * sample positions, and error metrics when ground truth is known.
 */
public class UserPositionHistory {

    private static final String TAG = "UserPositionHistory";
    private static final int SCHEMA_VERSION = 4;
    private static final String THESIS_TITLE = "Optimizing Indoor Positioning Accuracy through Sensor Fusion Techniques";
    private static final String CSV_FILE_SUFFIX = "_user_position_history.csv";
    private static final String JSON_DIR_NAME = "TestLogs";
    private static final String JSON_FILE_SUFFIX = "_user_position_history.json";
    private static final String CSV_HEADER =
            "sampleIndex,timestampMs,elapsedMs,mode,reason,"
                    + "renderXMeters,renderYMeters,renderRow,renderCol,"
                    + "bleXMeters,bleYMeters,bleRow,bleCol,hasBleFix,"
                    + "groundTruthXMeters,groundTruthYMeters,groundTruthRow,groundTruthCol,"
                    + "truthSource,evaluationEligible,errorMeters,bleErrorMeters,"
                    + "liveCount,filteredCount,adaptiveTuningEnabled,bleQualityScore,"
                    + "pdrQualityScore,rssiNoiseDb,bleMeasurementStdMeters,"
                    + "bleResidualRmsMeters,bleResidualMaxMeters,motionState,"
                    + "recordingEnabled,trialLabel\n";
    private static final long MIN_SAMPLE_INTERVAL_MS = 1000L;
    private static final double MIN_SAMPLE_DISTANCE_M = 0.05;

    private final List<UserPositionSample> samples = new ArrayList<>();
    private final File outputFile;
    private final File jsonOutputFile;
    private final long sessionStartedAtMs;
    private final String sessionId;
    private String calibrationAssetPath = "";
    private int gridRows = 0;
    private int gridCols = 0;
    private double roomWidthMeters = Double.NaN;
    private double roomHeightMeters = Double.NaN;
    private double metersPerRow = Double.NaN;
    private double metersPerCol = Double.NaN;
    private double minSolveDistanceMeters = Double.NaN;
    private double distanceMarginMeters = Double.NaN;
    private double maxJumpMeters = Double.NaN;
    private long beaconStaleMs = 0L;
    private long maxSolveBeaconAgeMs = 0L;
    private long sensorRenderIntervalMs = 0L;
    private int rssiAverageWindowSize = 1;
    private double manualHeadingCalibrationMinDeltaMeters = Double.NaN;
    private boolean manualHeadingCalibrationEnabled = false;
    private boolean bleHeadingAutoAlignEnabled = false;
    private List<BeaconLocation> beacons = new ArrayList<>();
    private double currentAnchorXMeters = Double.NaN;
    private double currentAnchorYMeters = Double.NaN;
    private boolean headerWritten = false;
    private long lastSampleTimeMs = 0L;
    private double lastRenderX = Double.NaN;
    private double lastRenderY = Double.NaN;
    private boolean adaptiveTuningEnabled = false;
    private double bleQualityScore = Double.NaN;
    private double pdrQualityScore = Double.NaN;
    private double rssiNoiseDb = Double.NaN;
    private double bleMeasurementStdMeters = Double.NaN;
    private double bleResidualRmsMeters = Double.NaN;
    private double bleResidualMaxMeters = Double.NaN;
    private String motionState = "FIXED";
    private boolean recordingEnabled = true;
    private String trialLabel = "UNLABELED";

    public UserPositionHistory(Context context) {
        Context appContext = context == null ? null : context.getApplicationContext();
        File dir = appContext == null ? null : appContext.getExternalFilesDir(null);
        if (dir == null && appContext != null) {
            dir = appContext.getFilesDir();
        }

        Date sessionStart = new Date();
        sessionStartedAtMs = sessionStart.getTime();
        sessionId = new SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.ENGLISH).format(sessionStart);

        File jsonDir = null;
        if (appContext != null) {
            jsonDir = appContext.getExternalFilesDir(JSON_DIR_NAME);
            if (jsonDir == null) {
                jsonDir = new File(appContext.getFilesDir(), JSON_DIR_NAME);
            }
        }
        if (jsonDir != null && !jsonDir.exists() && !jsonDir.mkdirs()) {
            Log.w(TAG, "Could not create JSON log directory: " + jsonDir);
        }
        File outputDir = jsonDir != null ? jsonDir : dir;
        outputFile = outputDir == null ? null : uniqueSessionFile(outputDir, sessionId + CSV_FILE_SUFFIX);
        jsonOutputFile = outputDir == null ? null : uniqueSessionFile(outputDir, sessionId + JSON_FILE_SUFFIX);
        writeJsonSnapshot();
    }

    public void configureAlgorithmContext(
            double minSolveDistanceMeters,
            double distanceMarginMeters,
            double maxJumpMeters,
            long beaconStaleMs,
            long maxSolveBeaconAgeMs,
            long sensorRenderIntervalMs,
            int rssiAverageWindowSize,
            double manualHeadingCalibrationMinDeltaMeters,
            boolean manualHeadingCalibrationEnabled,
            boolean bleHeadingAutoAlignEnabled
    ) {
        this.minSolveDistanceMeters = minSolveDistanceMeters;
        this.distanceMarginMeters = distanceMarginMeters;
        this.maxJumpMeters = maxJumpMeters;
        this.beaconStaleMs = beaconStaleMs;
        this.maxSolveBeaconAgeMs = maxSolveBeaconAgeMs;
        this.sensorRenderIntervalMs = sensorRenderIntervalMs;
        this.rssiAverageWindowSize = Math.max(1, rssiAverageWindowSize);
        this.manualHeadingCalibrationMinDeltaMeters = manualHeadingCalibrationMinDeltaMeters;
        this.manualHeadingCalibrationEnabled = manualHeadingCalibrationEnabled;
        this.bleHeadingAutoAlignEnabled = bleHeadingAutoAlignEnabled;
        writeJsonSnapshot();
    }

    public void configureExperimentContext(
            String calibrationAssetPath,
            int[][] floorGrid,
            List<BeaconLocation> beaconLocations
    ) {
        this.calibrationAssetPath = normalize(calibrationAssetPath);
        this.gridRows = floorGrid == null ? 0 : floorGrid.length;
        this.gridCols = floorGrid == null || floorGrid.length == 0 || floorGrid[0] == null ? 0 : floorGrid[0].length;
        this.roomWidthMeters = JsonOps.roomWidthMeters;
        this.roomHeightMeters = JsonOps.roomHeightMeters;
        this.metersPerRow = CoordinateUtils.metersPerRow();
        this.metersPerCol = CoordinateUtils.metersPerCol();
        this.beacons = beaconLocations == null ? new ArrayList<>() : new ArrayList<>(beaconLocations);
        writeJsonSnapshot();
    }

    public void setGroundTruthMeters(double xMeters, double yMeters) {
        currentAnchorXMeters = xMeters;
        currentAnchorYMeters = yMeters;
        writeJsonSnapshot();
    }

    public void clearGroundTruth() {
        currentAnchorXMeters = Double.NaN;
        currentAnchorYMeters = Double.NaN;
        writeJsonSnapshot();
    }

    public void updateAdaptiveTelemetry(
            boolean adaptiveTuningEnabled,
            double bleQualityScore,
            double pdrQualityScore,
            double rssiNoiseDb,
            double bleMeasurementStdMeters,
            double bleResidualRmsMeters,
            double bleResidualMaxMeters,
            String motionState
    ) {
        this.adaptiveTuningEnabled = adaptiveTuningEnabled;
        this.bleQualityScore = bleQualityScore;
        this.pdrQualityScore = pdrQualityScore;
        this.rssiNoiseDb = rssiNoiseDb;
        this.bleMeasurementStdMeters = bleMeasurementStdMeters;
        this.bleResidualRmsMeters = bleResidualRmsMeters;
        this.bleResidualMaxMeters = bleResidualMaxMeters;
        this.motionState = motionState == null || motionState.isEmpty() ? "FIXED" : motionState;
    }

    public void setRecordingEnabled(boolean enabled) {
        recordingEnabled = enabled;
        writeJsonSnapshot();
    }

    public boolean isRecordingEnabled() {
        return recordingEnabled;
    }

    public void setTrialLabel(String label) {
        trialLabel = label == null || label.trim().isEmpty() ? "UNLABELED" : label.trim().toUpperCase(Locale.US);
        writeJsonSnapshot();
    }

    public String getTrialLabel() {
        return trialLabel;
    }

    public boolean hasGroundTruth() {
        return Double.isFinite(currentAnchorXMeters) && Double.isFinite(currentAnchorYMeters);
    }

    public double getCurrentGroundTruthXMeters() {
        return currentAnchorXMeters;
    }

    public double getCurrentGroundTruthYMeters() {
        return currentAnchorYMeters;
    }

    public boolean addPosition(
            double renderX,
            double renderY,
            double bleX,
            double bleY,
            String mode,
            String reason,
            int liveCount,
            int filteredCount,
            Handler backgroundHandler
    ) {
        return addPosition(
                renderX,
                renderY,
                bleX,
                bleY,
                Double.NaN,
                Double.NaN,
                UserPositionSample.TRUTH_NONE,
                false,
                mode,
                reason,
                liveCount,
                filteredCount,
                backgroundHandler,
                false
        );
    }

    public boolean addPositionWithTruth(
            double renderX,
            double renderY,
            double bleX,
            double bleY,
            double truthX,
            double truthY,
            String truthSource,
            boolean evaluationEligible,
            String mode,
            String reason,
            int liveCount,
            int filteredCount,
            Handler backgroundHandler
    ) {
        return addPosition(
                renderX,
                renderY,
                bleX,
                bleY,
                truthX,
                truthY,
                truthSource,
                evaluationEligible,
                mode,
                reason,
                liveCount,
                filteredCount,
                backgroundHandler,
                true
        );
    }

    private boolean addPosition(
            double renderX,
            double renderY,
            double bleX,
            double bleY,
            double truthX,
            double truthY,
            String truthSource,
            boolean evaluationEligible,
            String mode,
            String reason,
            int liveCount,
            int filteredCount,
            Handler backgroundHandler,
            boolean forceAdd
    ) {
        if (!Double.isFinite(renderX) || !Double.isFinite(renderY)) {
            return false;
        }
        if (!recordingEnabled) {
            return false;
        }

        long nowMs = System.currentTimeMillis();
        synchronized (samples) {
            if (!forceAdd && isDuplicateSample(nowMs, renderX, renderY)) {
                return false;
            }
        }

        UserPositionSample sample = new UserPositionSample(
                nowMs,
                nowMs - sessionStartedAtMs,
                renderX,
                renderY,
                bleX,
                bleY,
                truthX,
                truthY,
                truthSource,
                evaluationEligible,
                normalize(mode),
                normalize(reason),
                liveCount,
                filteredCount,
                adaptiveTuningEnabled,
                bleQualityScore,
                pdrQualityScore,
                rssiNoiseDb,
                bleMeasurementStdMeters,
                bleResidualRmsMeters,
                bleResidualMaxMeters,
                motionState,
                recordingEnabled,
                trialLabel
        );

        synchronized (samples) {
            samples.add(sample);
            lastSampleTimeMs = nowMs;
            lastRenderX = renderX;
            lastRenderY = renderY;
        }

        // Disk writes run on the background handler when available so BLE callbacks
        // and UI rendering are not delayed by JSON serialization.
        Runnable writeTask = () -> {
            appendSample(sample);
            writeJsonSnapshot();
        };
        if (backgroundHandler != null) {
            backgroundHandler.post(writeTask);
        } else {
            writeTask.run();
        }

        return true;
    }

    public int size() {
        synchronized (samples) {
            return samples.size();
        }
    }

    public List<UserPositionSample> getSnapshot() {
        synchronized (samples) {
            return new ArrayList<>(samples);
        }
    }

    public File getOutputFile() {
        return outputFile;
    }

    public File getJsonOutputFile() {
        return jsonOutputFile;
    }

    private void appendSample(UserPositionSample sample) {
        if (outputFile == null) {
            Log.w(TAG, "No output file available for user position history.");
            return;
        }

        BufferedWriter writer = null;
        try {
            writer = new BufferedWriter(new FileWriter(outputFile, true));
            if (!headerWritten) {
                writer.write(CSV_HEADER);
                headerWritten = true;
            }
            writer.write(toCsvLine(sample));
        } catch (IOException e) {
            Log.e(TAG, "Error writing user position history", e);
        } finally {
            if (writer != null) {
                try {
                    writer.close();
                } catch (IOException ignored) {}
            }
        }
    }

    private boolean isDuplicateSample(long nowMs, double renderX, double renderY) {
        // Avoid filling logs with identical dashboard refreshes while the phone is still.
        if (!Double.isFinite(lastRenderX) || !Double.isFinite(lastRenderY)) {
            return false;
        }
        double distanceMeters = Math.hypot(renderX - lastRenderX, renderY - lastRenderY);
        return nowMs - lastSampleTimeMs < MIN_SAMPLE_INTERVAL_MS && distanceMeters < MIN_SAMPLE_DISTANCE_M;
    }

    private String toCsvLine(UserPositionSample sample) {
        return String.format(
                Locale.US,
                "%d,%d,%d,%s,%s,%.6f,%.6f,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%d,%d,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s\n",
                sampleIndex(sample),
                sample.getTimestampMs(),
                sample.getElapsedMs(),
                csvEscape(sample.getMode()),
                csvEscape(sample.getReason()),
                sample.getRenderXMeters(),
                sample.getRenderYMeters(),
                formatDouble(CoordinateUtils.yMetersToGridRow(sample.getRenderYMeters())),
                formatDouble(CoordinateUtils.xMetersToGridCol(sample.getRenderXMeters())),
                formatDouble(sample.getBleXMeters()),
                formatDouble(sample.getBleYMeters()),
                formatDouble(CoordinateUtils.yMetersToGridRow(sample.getBleYMeters())),
                formatDouble(CoordinateUtils.xMetersToGridCol(sample.getBleXMeters())),
                Boolean.toString(sample.hasBleFix()),
                formatDouble(sample.getGroundTruthXMeters()),
                formatDouble(sample.getGroundTruthYMeters()),
                formatDouble(CoordinateUtils.yMetersToGridRow(sample.getGroundTruthYMeters())),
                formatDouble(CoordinateUtils.xMetersToGridCol(sample.getGroundTruthXMeters())),
                csvEscape(sample.getTruthSource()),
                Boolean.toString(sample.isEvaluationEligible()),
                formatDouble(sample.getErrorMeters()),
                formatDouble(sample.getBleErrorMeters()),
                sample.getLiveCount(),
                sample.getFilteredCount(),
                Boolean.toString(sample.isAdaptiveTuningEnabled()),
                formatDouble(sample.getBleQualityScore()),
                formatDouble(sample.getPdrQualityScore()),
                formatDouble(sample.getRssiNoiseDb()),
                formatDouble(sample.getBleMeasurementStdMeters()),
                formatDouble(sample.getBleResidualRmsMeters()),
                formatDouble(sample.getBleResidualMaxMeters()),
                csvEscape(sample.getMotionState()),
                Boolean.toString(sample.isRecordingEnabled()),
                csvEscape(sample.getTrialLabel())
        );
    }

    private String formatDouble(double value) {
        return Double.isFinite(value) ? String.format(Locale.US, "%.6f", value) : "";
    }

    private String normalize(String value) {
        return value == null ? "" : value;
    }

    private String csvEscape(String value) {
        String safe = normalize(value);
        if (safe.contains(",") || safe.contains("\"") || safe.contains("\n") || safe.contains("\r")) {
            return "\"" + safe.replace("\"", "\"\"") + "\"";
        }
        return safe;
    }

    private void writeJsonSnapshot() {
        if (jsonOutputFile == null) {
            Log.w(TAG, "No JSON output file available for user position history.");
            return;
        }

        List<UserPositionSample> snapshot = getSnapshot();
        JSONObject root = new JSONObject();
        JSONArray positions = new JSONArray();
        try {
            root.put("sessionId", sessionId);
            root.put("schemaVersion", SCHEMA_VERSION);
            root.put("thesisTitle", THESIS_TITLE);
            root.put("session", buildSessionJson(snapshot));
            root.put("experiment", buildExperimentJson());
            root.put("device", buildDeviceJson());
            root.put("coordinateSystem", buildCoordinateSystemJson());
            root.put("room", buildRoomJson());
            root.put("floorGrid", buildFloorGridJson());
            root.put("beacons", buildBeaconsJson());
            root.put("summary", buildSummaryJson(snapshot));
            for (UserPositionSample sample : snapshot) {
                positions.put(toJson(sample));
            }
            root.put("positions", positions);
        } catch (JSONException e) {
            Log.e(TAG, "Error building user position JSON", e);
            return;
        }

        BufferedWriter writer = null;
        try {
            writer = new BufferedWriter(new FileWriter(jsonOutputFile, false));
            writer.write(root.toString(2));
            writer.write('\n');
        } catch (IOException | JSONException e) {
            Log.e(TAG, "Error writing user position JSON", e);
        } finally {
            if (writer != null) {
                try {
                    writer.close();
                } catch (IOException ignored) {}
            }
        }
    }

    private JSONObject toJson(UserPositionSample sample) throws JSONException {
        JSONObject object = new JSONObject();
        object.put("sampleIndex", sampleIndex(sample));
        object.put("timestampMs", sample.getTimestampMs());
        object.put("elapsedMs", sample.getElapsedMs());
        object.put("renderXMeters", sample.getRenderXMeters());
        object.put("renderYMeters", sample.getRenderYMeters());
        putOptionalDouble(object, "renderRow", CoordinateUtils.yMetersToGridRow(sample.getRenderYMeters()));
        putOptionalDouble(object, "renderCol", CoordinateUtils.xMetersToGridCol(sample.getRenderXMeters()));
        putOptionalDouble(object, "bleXMeters", sample.getBleXMeters());
        putOptionalDouble(object, "bleYMeters", sample.getBleYMeters());
        putOptionalDouble(object, "bleRow", CoordinateUtils.yMetersToGridRow(sample.getBleYMeters()));
        putOptionalDouble(object, "bleCol", CoordinateUtils.xMetersToGridCol(sample.getBleXMeters()));
        object.put("hasBleFix", sample.hasBleFix());
        putOptionalDouble(object, "groundTruthXMeters", sample.getGroundTruthXMeters());
        putOptionalDouble(object, "groundTruthYMeters", sample.getGroundTruthYMeters());
        putOptionalDouble(object, "groundTruthRow", CoordinateUtils.yMetersToGridRow(sample.getGroundTruthYMeters()));
        putOptionalDouble(object, "groundTruthCol", CoordinateUtils.xMetersToGridCol(sample.getGroundTruthXMeters()));
        object.put("truthSource", sample.getTruthSource());
        object.put("evaluationEligible", sample.isEvaluationEligible());
        putOptionalDouble(object, "errorMeters", sample.getErrorMeters());
        putOptionalDouble(object, "bleErrorMeters", sample.getBleErrorMeters());
        object.put("mode", sample.getMode());
        object.put("reason", sample.getReason());
        object.put("liveCount", sample.getLiveCount());
        object.put("filteredCount", sample.getFilteredCount());
        object.put("adaptiveTuningEnabled", sample.isAdaptiveTuningEnabled());
        putOptionalDouble(object, "bleQualityScore", sample.getBleQualityScore());
        putOptionalDouble(object, "pdrQualityScore", sample.getPdrQualityScore());
        putOptionalDouble(object, "rssiNoiseDb", sample.getRssiNoiseDb());
        putOptionalDouble(object, "bleMeasurementStdMeters", sample.getBleMeasurementStdMeters());
        putOptionalDouble(object, "bleResidualRmsMeters", sample.getBleResidualRmsMeters());
        putOptionalDouble(object, "bleResidualMaxMeters", sample.getBleResidualMaxMeters());
        object.put("motionState", sample.getMotionState());
        object.put("recordingEnabled", sample.isRecordingEnabled());
        object.put("trialLabel", sample.getTrialLabel());
        return object;
    }

    private void putOptionalDouble(JSONObject object, String key, double value) throws JSONException {
        if (Double.isFinite(value)) {
            object.put(key, value);
        } else {
            object.put(key, JSONObject.NULL);
        }
    }

    private int sampleIndex(UserPositionSample sample) {
        synchronized (samples) {
            int index = samples.indexOf(sample);
            return index >= 0 ? index : samples.size();
        }
    }

    private JSONObject buildSessionJson(List<UserPositionSample> snapshot) throws JSONException {
        JSONObject session = new JSONObject();
        session.put("sessionId", sessionId);
        session.put("startedAtMs", sessionStartedAtMs);
        session.put("sampleCount", snapshot.size());
        session.put("csvFile", outputFile == null ? JSONObject.NULL : outputFile.getAbsolutePath());
        session.put("jsonFile", jsonOutputFile == null ? JSONObject.NULL : jsonOutputFile.getAbsolutePath());
        session.put("recordingEnabled", recordingEnabled);
        session.put("trialLabel", trialLabel);
        return session;
    }

    private JSONObject buildExperimentJson() throws JSONException {
        JSONObject experiment = new JSONObject();
        experiment.put("calibrationAssetPath", calibrationAssetPath);
        experiment.put("notes", "Manual anchor truth is recorded for trace context only. Unknown movement samples are trace-only and are not continuous ground truth.");
        experiment.put("algorithm", buildAlgorithmJson());
        experiment.put("modes", new JSONArray()
                .put("BLE_ONLY")
                .put("PDR_ONLY")
                .put("FUSION")
                .put("FUSION_FALLBACK")
                .put("ANCHOR"));
        return experiment;
    }

    private JSONObject buildAlgorithmJson() throws JSONException {
        JSONObject algorithm = new JSONObject();
        putOptionalDouble(algorithm, "minSolveDistanceMeters", minSolveDistanceMeters);
        putOptionalDouble(algorithm, "distanceMarginMeters", distanceMarginMeters);
        putOptionalDouble(algorithm, "maxJumpMeters", maxJumpMeters);
        algorithm.put("beaconStaleMs", beaconStaleMs);
        algorithm.put("maxSolveBeaconAgeMs", maxSolveBeaconAgeMs);
        algorithm.put("sensorRenderIntervalMs", sensorRenderIntervalMs);
        algorithm.put("rssiAverageWindowSize", rssiAverageWindowSize);
        algorithm.put("rssiAveragingRationale", "RSS distances are computed from averaged RSS measurements before trilateration, following the BLE trilateration accuracy study design.");
        putOptionalDouble(algorithm, "pathLossExponent", JsonOps.pathLossExponent);
        algorithm.put("fusionFilter", "KALMAN_CONSTANT_VELOCITY");
        algorithm.put("kalmanEnabled", true);
        algorithm.put("manualAnchorResetsKalman", true);
        putOptionalDouble(algorithm, "kalmanManualPositionVariance", KalmanPositionFilter.MANUAL_POSITION_VARIANCE);
        putOptionalDouble(algorithm, "kalmanBleInitialPositionVariance", KalmanPositionFilter.BLE_INITIAL_POSITION_VARIANCE);
        putOptionalDouble(algorithm, "kalmanStepStdMeters", KalmanPositionFilter.STEP_POSITION_STD_METERS);
        putOptionalDouble(algorithm, "kalmanBleStdStationaryMeters", KalmanPositionFilter.BLE_STD_STATIONARY_METERS);
        putOptionalDouble(algorithm, "kalmanBleStdMovingMeters", KalmanPositionFilter.BLE_STD_MOVING_METERS);
        putOptionalDouble(algorithm, "kalmanStationaryAccelNoiseMps2", KalmanPositionFilter.STATIONARY_ACCEL_NOISE_MPS2);
        putOptionalDouble(algorithm, "kalmanMovingAccelNoiseMps2", KalmanPositionFilter.MOVING_ACCEL_NOISE_MPS2);
        putOptionalDouble(algorithm, "manualHeadingCalibrationMinDeltaMeters", manualHeadingCalibrationMinDeltaMeters);
        algorithm.put("manualHeadingCalibrationEnabled", manualHeadingCalibrationEnabled);
        algorithm.put("headingCalibrationMethod", "Disabled during static baseline tests unless explicitly enabled; consecutive known map anchors can calibrate the phone heading frame from observed room-coordinate displacement.");
        algorithm.put("bleHeadingAutoAlignEnabled", bleHeadingAutoAlignEnabled);
        algorithm.put("adaptiveTuningRuntimeToggle", "Session-only dashboard button; starts disabled each app launch.");
        return algorithm;
    }

    private JSONObject buildDeviceJson() throws JSONException {
        JSONObject device = new JSONObject();
        device.put("manufacturer", Build.MANUFACTURER);
        device.put("model", Build.MODEL);
        device.put("brand", Build.BRAND);
        device.put("device", Build.DEVICE);
        device.put("sdkInt", Build.VERSION.SDK_INT);
        device.put("release", Build.VERSION.RELEASE);
        return device;
    }

    private JSONObject buildCoordinateSystemJson() throws JSONException {
        JSONObject coordinateSystem = new JSONObject();
        coordinateSystem.put("displayConvention", "col,row");
        coordinateSystem.put("internalConvention", "xMeters,yMeters");
        coordinateSystem.put("rowMeaning", "grid row derived from yMeters");
        coordinateSystem.put("colMeaning", "grid column derived from xMeters");
        coordinateSystem.put("displayMapping", "col,row matches x,y order");
        coordinateSystem.put("origin", "row=0,col=0 equals x=0,y=0");
        coordinateSystem.put("rowIncreasesToward", "larger yMeters");
        coordinateSystem.put("colIncreasesToward", "larger xMeters");
        return coordinateSystem;
    }

    private JSONObject buildRoomJson() throws JSONException {
        JSONObject room = new JSONObject();
        putOptionalDouble(room, "widthMeters", roomWidthMeters);
        putOptionalDouble(room, "heightMeters", roomHeightMeters);
        putOptionalDouble(room, "diagonalMeters", Math.hypot(roomWidthMeters, roomHeightMeters));
        return room;
    }

    private JSONObject buildFloorGridJson() throws JSONException {
        JSONObject floor = new JSONObject();
        floor.put("rows", gridRows);
        floor.put("cols", gridCols);
        putOptionalDouble(floor, "metersPerRow", metersPerRow);
        putOptionalDouble(floor, "metersPerCol", metersPerCol);
        return floor;
    }

    private JSONArray buildBeaconsJson() throws JSONException {
        JSONArray array = new JSONArray();
        for (BeaconLocation beacon : beacons) {
            JSONObject object = new JSONObject();
            object.put("id", beacon.getUniqueId());
            object.put("uuid", beacon.beaconUUID);
            object.put("major", beacon.major);
            object.put("minor", beacon.minor);
            object.put("row", beacon.getRow());
            object.put("col", beacon.getCol());
            putOptionalDouble(object, "xMeters", CoordinateUtils.beaconXMeters(beacon));
            putOptionalDouble(object, "yMeters", CoordinateUtils.beaconYMeters(beacon));
            object.put("positionMetersSource", beacon.hasExactPositionMeters() ? "json" : "gridFallback");
            object.put("rssi1Meter", beacon.rssi1Meter);
            array.put(object);
        }
        return array;
    }

    private JSONObject buildSummaryJson(List<UserPositionSample> snapshot) throws JSONException {
        // Summary is duplicated in JSON so thesis/report scripts can read aggregate
        // metrics without reprocessing every sample each time.
        JSONObject summary = new JSONObject();
        int bleFixSamples = 0;
        int groundTruthSamples = 0;
        int evaluationSamples = 0;
        int bleEvaluationSamples = 0;
        int bleRawEvaluationSamples = 0;
        double errorSum = 0.0;
        double squaredErrorSum = 0.0;
        double maxError = 0.0;
        double bleErrorSum = 0.0;
        double bleSquaredErrorSum = 0.0;
        double bleMaxError = 0.0;
        double bleRawErrorSum = 0.0;
        double bleRawSquaredErrorSum = 0.0;
        double bleRawMaxError = 0.0;

        JSONObject modeCounts = new JSONObject();
        for (UserPositionSample sample : snapshot) {
            boolean bleFixMode = isBleFixMode(sample);
            if (bleFixMode) {
                bleFixSamples++;
            }
            modeCounts.put(sample.getMode(), modeCounts.optInt(sample.getMode(), 0) + 1);
            double error = sample.getErrorMeters();
            if (Double.isFinite(error)) {
                groundTruthSamples++;
            }
            if (Double.isFinite(error) && sample.isEvaluationEligible()) {
                evaluationSamples++;
                errorSum += error;
                squaredErrorSum += error * error;
                maxError = Math.max(maxError, error);
                if (bleFixMode) {
                    bleEvaluationSamples++;
                    bleErrorSum += error;
                    bleSquaredErrorSum += error * error;
                    bleMaxError = Math.max(bleMaxError, error);
                }
            }
            double bleRawError = sample.getBleErrorMeters();
            if (Double.isFinite(bleRawError) && bleFixMode && sample.isEvaluationEligible()) {
                bleRawEvaluationSamples++;
                bleRawErrorSum += bleRawError;
                bleRawSquaredErrorSum += bleRawError * bleRawError;
                bleRawMaxError = Math.max(bleRawMaxError, bleRawError);
            }
        }

        summary.put("sampleCount", snapshot.size());
        summary.put("bleFixSamples", bleFixSamples);
        summary.put("adaptiveSamples", countAdaptiveSamples(snapshot));
        summary.put("groundTruthSamples", groundTruthSamples);
        summary.put("evaluationSamples", evaluationSamples);
        summary.put("bleEvaluationSamples", bleEvaluationSamples);
        summary.put("bleRawEvaluationSamples", bleRawEvaluationSamples);
        summary.put("modeCounts", modeCounts);
        putOptionalDouble(summary, "bleFixRatio", snapshot.isEmpty() ? Double.NaN : (double) bleFixSamples / snapshot.size());
        putOptionalDouble(summary, "meanErrorMeters", evaluationSamples == 0 ? Double.NaN : errorSum / evaluationSamples);
        putOptionalDouble(summary, "rmseMeters", evaluationSamples == 0 ? Double.NaN : Math.sqrt(squaredErrorSum / evaluationSamples));
        putOptionalDouble(summary, "maxErrorMeters", evaluationSamples == 0 ? Double.NaN : maxError);
        putOptionalDouble(summary, "bleMeanErrorMeters", bleEvaluationSamples == 0 ? Double.NaN : bleErrorSum / bleEvaluationSamples);
        putOptionalDouble(summary, "bleRmseMeters", bleEvaluationSamples == 0 ? Double.NaN : Math.sqrt(bleSquaredErrorSum / bleEvaluationSamples));
        putOptionalDouble(summary, "bleMaxErrorMeters", bleEvaluationSamples == 0 ? Double.NaN : bleMaxError);
        putOptionalDouble(summary, "bleRawMeanErrorMeters", bleRawEvaluationSamples == 0 ? Double.NaN : bleRawErrorSum / bleRawEvaluationSamples);
        putOptionalDouble(summary, "bleRawRmseMeters", bleRawEvaluationSamples == 0 ? Double.NaN : Math.sqrt(bleRawSquaredErrorSum / bleRawEvaluationSamples));
        putOptionalDouble(summary, "bleRawMaxErrorMeters", bleRawEvaluationSamples == 0 ? Double.NaN : bleRawMaxError);
        return summary;
    }

    private int countAdaptiveSamples(List<UserPositionSample> snapshot) {
        int count = 0;
        for (UserPositionSample sample : snapshot) {
            if (sample.isAdaptiveTuningEnabled()) {
                count++;
            }
        }
        return count;
    }

    private boolean isBleFixMode(UserPositionSample sample) {
        if (sample == null || !sample.hasBleFix()) {
            return false;
        }
        String mode = sample.getMode();
        return "BLE_ONLY".equals(mode) ||
                "FUSION".equals(mode) ||
                "BLE_PLUS_FUSION".equals(mode) ||
                "BLE_ACCEPTED".equals(mode);
    }

    private File uniqueSessionFile(File dir, String fileName) {
        File candidate = new File(dir, fileName);
        if (!candidate.exists()) {
            return candidate;
        }

        int dot = fileName.lastIndexOf('.');
        String base = dot >= 0 ? fileName.substring(0, dot) : fileName;
        String ext = dot >= 0 ? fileName.substring(dot) : "";
        int counter = 1;
        do {
            candidate = new File(dir, base + "_" + counter + ext);
            counter++;
        } while (candidate.exists());
        return candidate;
    }
}
