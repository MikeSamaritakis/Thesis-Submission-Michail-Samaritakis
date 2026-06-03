package com.example.connectiontest;

/**
 * Immutable record of one rendered position sample.
 *
 * renderX/Y is the final position shown by the app, currently Kalman fused.
 * bleX/Y is the raw accepted BLE fix when available. Manual anchor truth is
 * recorded for trace context only and is not an accuracy sample.
 */
public final class UserPositionSample {

    public static final String TRUTH_NONE = "NONE";
    public static final String TRUTH_MANUAL_ANCHOR = "MANUAL_ANCHOR";

    private final long timestampMs;
    private final long elapsedMs;
    private final double renderXMeters;
    private final double renderYMeters;
    private final double bleXMeters;
    private final double bleYMeters;
    private final double groundTruthXMeters;
    private final double groundTruthYMeters;
    private final String truthSource;
    private final boolean evaluationEligible;
    private final String mode;
    private final String reason;
    private final int liveCount;
    private final int filteredCount;
    private final boolean adaptiveTuningEnabled;
    private final double bleQualityScore;
    private final double pdrQualityScore;
    private final double rssiNoiseDb;
    private final double bleMeasurementStdMeters;
    private final double bleResidualRmsMeters;
    private final double bleResidualMaxMeters;
    private final String motionState;
    private final boolean recordingEnabled;
    private final String trialLabel;

    public UserPositionSample(
            long timestampMs,
            long elapsedMs,
            double renderXMeters,
            double renderYMeters,
            double bleXMeters,
            double bleYMeters,
            double groundTruthXMeters,
            double groundTruthYMeters,
            String truthSource,
            boolean evaluationEligible,
            String mode,
            String reason,
            int liveCount,
            int filteredCount
    ) {
        this(
                timestampMs,
                elapsedMs,
                renderXMeters,
                renderYMeters,
                bleXMeters,
                bleYMeters,
                groundTruthXMeters,
                groundTruthYMeters,
                truthSource,
                evaluationEligible,
                mode,
                reason,
                liveCount,
                filteredCount,
                false,
                Double.NaN,
                Double.NaN,
                Double.NaN,
                Double.NaN,
                Double.NaN,
                Double.NaN,
                "FIXED",
                true,
                "UNLABELED"
        );
    }

    public UserPositionSample(
            long timestampMs,
            long elapsedMs,
            double renderXMeters,
            double renderYMeters,
            double bleXMeters,
            double bleYMeters,
            double groundTruthXMeters,
            double groundTruthYMeters,
            String truthSource,
            boolean evaluationEligible,
            String mode,
            String reason,
            int liveCount,
            int filteredCount,
            boolean adaptiveTuningEnabled,
            double bleQualityScore,
            double pdrQualityScore,
            double rssiNoiseDb,
            double bleMeasurementStdMeters,
            double bleResidualRmsMeters,
            double bleResidualMaxMeters,
            String motionState,
            boolean recordingEnabled,
            String trialLabel
    ) {
        this.timestampMs = timestampMs;
        this.elapsedMs = elapsedMs;
        this.renderXMeters = renderXMeters;
        this.renderYMeters = renderYMeters;
        this.bleXMeters = bleXMeters;
        this.bleYMeters = bleYMeters;
        this.groundTruthXMeters = groundTruthXMeters;
        this.groundTruthYMeters = groundTruthYMeters;
        this.truthSource = truthSource == null || truthSource.isEmpty() ? TRUTH_NONE : truthSource;
        this.evaluationEligible = evaluationEligible;
        this.mode = mode;
        this.reason = reason;
        this.liveCount = liveCount;
        this.filteredCount = filteredCount;
        this.adaptiveTuningEnabled = adaptiveTuningEnabled;
        this.bleQualityScore = bleQualityScore;
        this.pdrQualityScore = pdrQualityScore;
        this.rssiNoiseDb = rssiNoiseDb;
        this.bleMeasurementStdMeters = bleMeasurementStdMeters;
        this.bleResidualRmsMeters = bleResidualRmsMeters;
        this.bleResidualMaxMeters = bleResidualMaxMeters;
        this.motionState = motionState == null || motionState.isEmpty() ? "FIXED" : motionState;
        this.recordingEnabled = recordingEnabled;
        this.trialLabel = trialLabel == null || trialLabel.isEmpty() ? "UNLABELED" : trialLabel;
    }

    public long getTimestampMs() {
        return timestampMs;
    }

    public long getElapsedMs() {
        return elapsedMs;
    }

    public double getRenderXMeters() {
        return renderXMeters;
    }

    public double getRenderYMeters() {
        return renderYMeters;
    }

    public double getBleXMeters() {
        return bleXMeters;
    }

    public double getBleYMeters() {
        return bleYMeters;
    }

    public double getGroundTruthXMeters() {
        return groundTruthXMeters;
    }

    public double getGroundTruthYMeters() {
        return groundTruthYMeters;
    }

    public String getTruthSource() {
        return truthSource;
    }

    public boolean isEvaluationEligible() {
        return evaluationEligible;
    }

    public String getMode() {
        return mode;
    }

    public String getReason() {
        return reason;
    }

    public int getLiveCount() {
        return liveCount;
    }

    public int getFilteredCount() {
        return filteredCount;
    }

    public boolean hasBleFix() {
        return Double.isFinite(bleXMeters) && Double.isFinite(bleYMeters);
    }

    public boolean isAdaptiveTuningEnabled() {
        return adaptiveTuningEnabled;
    }

    public double getBleQualityScore() {
        return bleQualityScore;
    }

    public double getPdrQualityScore() {
        return pdrQualityScore;
    }

    public double getRssiNoiseDb() {
        return rssiNoiseDb;
    }

    public double getBleMeasurementStdMeters() {
        return bleMeasurementStdMeters;
    }

    public double getBleResidualRmsMeters() {
        return bleResidualRmsMeters;
    }

    public double getBleResidualMaxMeters() {
        return bleResidualMaxMeters;
    }

    public String getMotionState() {
        return motionState;
    }

    public boolean isRecordingEnabled() {
        return recordingEnabled;
    }

    public String getTrialLabel() {
        return trialLabel;
    }

    public boolean hasGroundTruth() {
        return Double.isFinite(groundTruthXMeters) && Double.isFinite(groundTruthYMeters);
    }

    public double getErrorMeters() {
        if (!hasGroundTruth()) {
            return Double.NaN;
        }
        return Math.hypot(renderXMeters - groundTruthXMeters, renderYMeters - groundTruthYMeters);
    }

    public double getBleErrorMeters() {
        if (!hasGroundTruth() || !hasBleFix()) {
            return Double.NaN;
        }
        return Math.hypot(bleXMeters - groundTruthXMeters, bleYMeters - groundTruthYMeters);
    }
}
