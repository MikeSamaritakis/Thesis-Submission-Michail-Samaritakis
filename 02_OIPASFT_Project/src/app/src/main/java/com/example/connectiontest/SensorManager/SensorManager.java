package com.example.connectiontest.SensorManager;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.os.SystemClock;
import android.util.Log;

import com.example.connectiontest.JsonOps.JsonOps;
import com.example.connectiontest.Positioning.KalmanPositionFilter;

import java.util.Locale;

/**
 * Owns phone-sensor fusion and the final rendered position.
 *
 * Accepted BLE fixes arrive from MainActivity after PositioningEngine validation.
 * Step/heading sensors provide relative motion. Manual map taps reset the Kalman
 * filter to a known position and should remain the strongest anchor in the system.
 */
public class SensorManager implements SensorEventListener {

    private static final String TAG = "SensorFusion";

    // Tune these for your device/phone-carry style
    private static final double DEFAULT_STEP_LENGTH_M = 0.62;
    private static final double DEFAULT_BLE_BLEND_ALPHA = 0.65; // 0..1, higher = trust BLE more
    private static final double STATIONARY_BLE_BLEND_ALPHA = 0.82;
    private static final double MOVING_BLE_BLEND_ALPHA = 0.55;
    private static final double DEFAULT_MAX_STEP_LENGTH_M = 0.95;
    private static final double DEFAULT_MIN_STEP_LENGTH_M = 0.40;
    private static final double STATIONARY_LINEAR_ACCEL_EPS = 0.18;
    private static final double STATIONARY_GYRO_EPS = 0.07;
    private static final double MOVING_LINEAR_ACCEL_EPS = 0.30;
    private static final double MOVING_GYRO_EPS = 0.14;
    private static final double FLOOR_CHANGE_ALTITUDE_M = 0.70;
    private static final long MOTION_STATE_HYSTERESIS_MS = 700L;
    private static final boolean ENABLE_CUSTOM_ACCEL_STEP_DETECTION = true;
    private static final double DEFAULT_CUSTOM_STEP_PEAK_THRESHOLD = 1.40;
    private static final double DEFAULT_CUSTOM_STEP_RESET_THRESHOLD = 0.35;
    private static final long DEFAULT_CUSTOM_STEP_MIN_INTERVAL_MS = 650L;
    private static final double DEFAULT_CUSTOM_STEP_MAX_GYRO = 0.95;
    private static final double DEFAULT_CUSTOM_STEP_MAX_ABS_PITCH_DEGREES = 35.0;
    private static final double DEFAULT_CUSTOM_STEP_MAX_ABS_ROLL_DEGREES = 35.0;
    private static final long MANUAL_ANCHOR_STEP_SUPPRESSION_MS = 900L;

    public static final class StepVector {
        public final double dxMeters;
        public final double dyMeters;

        private StepVector(double dxMeters, double dyMeters) {
            this.dxMeters = dxMeters;
            this.dyMeters = dyMeters;
        }
    }

    private final android.hardware.SensorManager platformSensorManager;
    private final Sensor stepDetector;
    private final Sensor stepCounter;
    private final Sensor gameRotationVector;
    private final Sensor rotationVector;
    private final Sensor gyroscope;
    private final Sensor linearAcceleration;
    private final Sensor gravity;
    private final Sensor accelerometer;
    private final Sensor magnetometer;
    private final Sensor pressure;
    private final KalmanPositionFilter kalmanFilter = new KalmanPositionFilter();

    private final float[] rotationMatrix = new float[9];
    private final float[] orientation = new float[3];
    private final float[] gravityValues = new float[3];
    private final float[] accelerometerValues = new float[3];
    private final float[] magnetometerValues = new float[3];

    private boolean running = false;
    private boolean hasFusedPosition = false;
    private boolean hasHeading = false;
    private boolean hasPressureBaseline = false;
    private boolean isStationary = true;
    private boolean pendingStationaryState = true;
    private boolean adaptiveTuningEnabled = false;

    private double fusedXMeters = Double.NaN;
    private double fusedYMeters = Double.NaN;

    private double stepLengthMeters = DEFAULT_STEP_LENGTH_M;
    private double minStepLengthMeters = DEFAULT_MIN_STEP_LENGTH_M;
    private double maxStepLengthMeters = DEFAULT_MAX_STEP_LENGTH_M;
    private long customStepMinIntervalMs = DEFAULT_CUSTOM_STEP_MIN_INTERVAL_MS;
    private double customStepPeakThreshold = DEFAULT_CUSTOM_STEP_PEAK_THRESHOLD;
    private double customStepResetThreshold = DEFAULT_CUSTOM_STEP_RESET_THRESHOLD;
    private double customStepMaxGyro = DEFAULT_CUSTOM_STEP_MAX_GYRO;
    private double customStepMaxAbsPitchDegrees = DEFAULT_CUSTOM_STEP_MAX_ABS_PITCH_DEGREES;
    private double customStepMaxAbsRollDegrees = DEFAULT_CUSTOM_STEP_MAX_ABS_ROLL_DEGREES;
    private double bleBlendAlpha = DEFAULT_BLE_BLEND_ALPHA;
    private double headingOffsetRad = 0.0;
    private double rawHeadingRad = 0.0;
    private double headingRad = 0.0;
    private double pitchRad = 0.0;
    private double rollRad = 0.0;
    private double dynamicStepLengthMeters = DEFAULT_STEP_LENGTH_M;
    private double linearAccelEma = 0.0;
    private double gyroEma = 0.0;
    private double accelNormEma = 0.0;
    private double magneticFieldEma = 0.0;
    private double pressureHpa = Double.NaN;
    private double relativeAltitudeMeters = 0.0;
    private double pressureBaselineAltitudeMeters = 0.0;
    private long lastHeadingLogMs = 0L;
    private long lastStepDetectorMs = 0L;
    private long lastCustomStepMs = 0L;
    private long customStepSuppressedUntilMs = 0L;
    private long stepCount = 0L;
    private float stepCounterBaseline = Float.NaN;
    private String headingSource = "none";
    private long pendingMotionStateSinceMs = 0L;
    private long lastRejectedStepCandidateLogMs = 0L;
    private boolean customStepArmed = true;
    private double lastPdrQualityScore = 1.0;
    private double lastBleMeasurementStdMeters = KalmanPositionFilter.BLE_STD_MOVING_METERS;

    /**
     * Creates a sensor-fusion manager that combines absolute BLE corrections with
     * relative phone motion. The Android sensors are optional; BLE can still initialize
     * the filter when step or rotation sensors are missing.
     */
    public SensorManager(Context context) {
        platformSensorManager = (android.hardware.SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        stepDetector = platformSensorManager != null ? platformSensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR) : null;
        stepCounter = platformSensorManager != null ? platformSensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) : null;
        gameRotationVector = platformSensorManager != null ? platformSensorManager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR) : null;
        rotationVector = platformSensorManager != null ? platformSensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR) : null;
        gyroscope = platformSensorManager != null ? platformSensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE) : null;
        linearAcceleration = platformSensorManager != null ? platformSensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION) : null;
        gravity = platformSensorManager != null ? platformSensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY) : null;
        accelerometer = platformSensorManager != null ? platformSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) : null;
        magnetometer = platformSensorManager != null ? platformSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD) : null;
        pressure = platformSensorManager != null ? platformSensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE) : null;
    }

    public void start() {
        if (running || platformSensorManager == null) return;
        register(stepDetector, android.hardware.SensorManager.SENSOR_DELAY_GAME);
        register(stepCounter, android.hardware.SensorManager.SENSOR_DELAY_NORMAL);
        register(gameRotationVector, android.hardware.SensorManager.SENSOR_DELAY_GAME);
        register(rotationVector, android.hardware.SensorManager.SENSOR_DELAY_GAME);
        register(gyroscope, android.hardware.SensorManager.SENSOR_DELAY_GAME);
        register(linearAcceleration, android.hardware.SensorManager.SENSOR_DELAY_GAME);
        register(gravity, android.hardware.SensorManager.SENSOR_DELAY_GAME);
        register(accelerometer, android.hardware.SensorManager.SENSOR_DELAY_GAME);
        register(magnetometer, android.hardware.SensorManager.SENSOR_DELAY_UI);
        register(pressure, android.hardware.SensorManager.SENSOR_DELAY_NORMAL);
        running = true;
        log(
                "FUSION: started stepDetector=" + (stepDetector != null) +
                " stepCounter=" + (stepCounter != null) +
                " gameRotation=" + (gameRotationVector != null) +
                " rotation=" + (rotationVector != null) +
                " gyro=" + (gyroscope != null) +
                " linAcc=" + (linearAcceleration != null) +
                " gravity=" + (gravity != null) +
                " accel=" + (accelerometer != null) +
                " mag=" + (magnetometer != null) +
                " pressure=" + (pressure != null)
        );
    }

    public void stop() {
        if (!running || platformSensorManager == null) return;
        platformSensorManager.unregisterListener(this);
        running = false;
        log("FUSION: stopped");
    }

    /**
     * Sets stride length used by dead reckoning on each detected step.
     */
    public synchronized void setStepLengthMeters(double stepLengthMeters) {
        if (stepLengthMeters > 0.1 && stepLengthMeters < 1.5) {
            this.stepLengthMeters = stepLengthMeters;
            log(String.format(Locale.US, "FUSION: stepLength=%.2fm", this.stepLengthMeters));
        }
    }

    public synchronized void configurePdr(
            double stepLengthMeters,
            double minStepLengthMeters,
            double maxStepLengthMeters,
            long minStepIntervalMs,
            double customStepPeakThreshold,
            double customStepResetThreshold,
            double customStepMaxGyro,
            double customStepMaxAbsPitchDegrees,
            double customStepMaxAbsRollDegrees
    ) {
        setStepLengthMeters(stepLengthMeters);
        this.minStepLengthMeters = clamp(minStepLengthMeters, 0.10, 1.20);
        this.maxStepLengthMeters = clamp(maxStepLengthMeters, this.minStepLengthMeters, 1.50);
        this.customStepMinIntervalMs = Math.max(250L, minStepIntervalMs);
        this.customStepPeakThreshold = clamp(customStepPeakThreshold, 0.40, 5.00);
        this.customStepResetThreshold = clamp(customStepResetThreshold, 0.05, this.customStepPeakThreshold);
        this.customStepMaxGyro = clamp(customStepMaxGyro, 0.10, 5.00);
        this.customStepMaxAbsPitchDegrees = clamp(customStepMaxAbsPitchDegrees, 5.0, 85.0);
        this.customStepMaxAbsRollDegrees = clamp(customStepMaxAbsRollDegrees, 5.0, 85.0);
        log(String.format(Locale.US,
                "CONFIG: PDR stepLen=%.2f min=%.2f max=%.2f minIntervalMs=%d peak=%.2f reset=%.2f maxGyro=%.2f maxPitch=%.1f maxRoll=%.1f",
                this.stepLengthMeters,
                this.minStepLengthMeters,
                this.maxStepLengthMeters,
                this.customStepMinIntervalMs,
                this.customStepPeakThreshold,
                this.customStepResetThreshold,
                this.customStepMaxGyro,
                this.customStepMaxAbsPitchDegrees,
                this.customStepMaxAbsRollDegrees
        ));
    }

    /**
     * Kept for compatibility with older setup code. Kalman measurement noise now controls
     * how strongly accepted BLE fixes correct the fused position.
     */
    public synchronized void setBleBlendAlpha(double bleBlendAlpha) {
        this.bleBlendAlpha = clamp(bleBlendAlpha, 0.05, 1.0);
        log(String.format(Locale.US, "KALMAN: legacyBleBlendAlpha=%.2f measurementNoiseControlsBleTrust", this.bleBlendAlpha));
    }

    public synchronized void setAdaptiveTuningEnabled(boolean enabled) {
        adaptiveTuningEnabled = enabled;
        log("CONFIG: adaptiveTuningEnabled=" + adaptiveTuningEnabled);
    }

    /**
     * Manual heading offset for room alignment (degrees).
     */
    public synchronized void setHeadingOffsetDegrees(double offsetDegrees) {
        this.headingOffsetRad = Math.toRadians(offsetDegrees);
        this.headingRad = normalizeAngle(rawHeadingRad + headingOffsetRad);
        log(String.format(Locale.US, "FUSION: headingOffset=%.1fdeg", offsetDegrees));
    }

    /**
     * Aligns the phone heading frame to observed BLE motion in room coordinates.
     * This only makes sense after a meaningful BLE displacement.
     */
    public synchronized boolean alignHeadingToMotion(double deltaXMeters, double deltaYMeters) {
        if (!hasHeading) {
            return false;
        }
        double motionMagnitude = Math.hypot(deltaXMeters, deltaYMeters);
        if (motionMagnitude < 0.35) {
            return false;
        }

        double expectedHeadingRad = Math.atan2(deltaXMeters, deltaYMeters);
        headingOffsetRad = normalizeAngle(expectedHeadingRad - rawHeadingRad);
        headingRad = normalizeAngle(rawHeadingRad + headingOffsetRad);
        log(String.format(Locale.US,
                "FUSION: auto-aligned heading motion=%.2fm expected=%.1fdeg raw=%.1fdeg offset=%.1fdeg",
                motionMagnitude,
                Math.toDegrees(expectedHeadingRad),
                Math.toDegrees(rawHeadingRad),
                Math.toDegrees(headingOffsetRad)
        ));
        return true;
    }

    /**
     * BLE update hook:
     * - first fix initializes the Kalman-backed fused state
     * - later accepted fixes correct the predicted state
     */
    public synchronized void onBlePosition(double xMeters, double yMeters) {
        onBlePosition(xMeters, yMeters, 3);
    }

    public synchronized void onBlePosition(double xMeters, double yMeters, int filteredBeaconCount) {
        onBlePosition(xMeters, yMeters, filteredBeaconCount, 1.0);
    }

    public synchronized void onBlePosition(double xMeters, double yMeters, int filteredBeaconCount, double bleQualityScore) {
        if (!Double.isFinite(xMeters) || !Double.isFinite(yMeters)) {
            return;
        }

        long nowMs = currentSensorClockMs();
        double measurementStdMeters = bleMeasurementStdMeters(filteredBeaconCount, bleQualityScore);
        lastBleMeasurementStdMeters = measurementStdMeters;
        if (!kalmanFilter.isInitialized()) {
            kalmanFilter.resetFromBle(xMeters, yMeters, nowMs);
            syncFusedFromKalman();
            log(String.format(Locale.US,
                    "KALMAN: init BLE raw=(%.2f, %.2f) filtered=(%.2f, %.2f) std=%.2f quality=%.2f stationary=%s beacons=%d adaptive=%s",
                    xMeters, yMeters, fusedXMeters, fusedYMeters, measurementStdMeters, bleQualityScore, isStationary, filteredBeaconCount, adaptiveTuningEnabled
        ));
        } else {
            kalmanFilter.updateBle(xMeters, yMeters, nowMs, measurementStdMeters, isStationary);
            syncFusedFromKalman();
            log(String.format(Locale.US,
                    "KALMAN: BLE update raw=(%.2f, %.2f) filtered=(%.2f, %.2f) v=(%.2f, %.2f) std=%.2f quality=%.2f stationary=%s beacons=%d adaptive=%s",
                    xMeters,
                    yMeters,
                    fusedXMeters,
                    fusedYMeters,
                    kalmanFilter.getVx(),
                    kalmanFilter.getVy(),
                    measurementStdMeters,
                    bleQualityScore,
                    isStationary,
                    filteredBeaconCount,
                    adaptiveTuningEnabled
            ));
        }
    }

    /**
     * Returns latest fused position in meters, or null if not initialized yet.
     */
    public synchronized double[] getFusedPositionMeters() {
        if (!hasFusedPosition) return null;
        return new double[]{fusedXMeters, fusedYMeters};
    }

    public synchronized boolean hasFusedPosition() {
        return hasFusedPosition;
    }

    public synchronized double getPdrQualityScore() {
        return adaptiveTuningEnabled ? lastPdrQualityScore : 1.0;
    }

    public synchronized double getLastBleMeasurementStdMeters() {
        return lastBleMeasurementStdMeters;
    }

    public synchronized String getMotionStateLabel() {
        if (!adaptiveTuningEnabled) {
            return "FIXED";
        }
        return isStationary ? "STATIONARY" : "MOVING";
    }

    public synchronized boolean isCurrentlyStationary() {
        return isStationary;
    }

    public synchronized void setManualPosition(double xMeters, double yMeters) {
        if (!Double.isFinite(xMeters) || !Double.isFinite(yMeters)) {
            return;
        }
        stepCount = 0L;
        stepCounterBaseline = Float.NaN;
        linearAccelEma = 0.0;
        gyroEma = 0.0;
        isStationary = true;
        pendingStationaryState = true;
        pendingMotionStateSinceMs = System.currentTimeMillis();
        customStepArmed = false;
        lastCustomStepMs = currentSensorClockMs();
        customStepSuppressedUntilMs = lastCustomStepMs + MANUAL_ANCHOR_STEP_SUPPRESSION_MS;
        kalmanFilter.resetKnownPosition(xMeters, yMeters, lastCustomStepMs);
        syncFusedFromKalman();
        log(String.format(Locale.US, "KALMAN: reset manual anchor -> (%.2f, %.2f)", fusedXMeters, fusedYMeters));
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event == null || event.sensor == null) return;

        int type = event.sensor.getType();

        if (type == Sensor.TYPE_GAME_ROTATION_VECTOR || type == Sensor.TYPE_ROTATION_VECTOR) {
            synchronized (this) {
                if (type == Sensor.TYPE_ROTATION_VECTOR && gameRotationVector != null) {
                    return;
                }
                android.hardware.SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values);
                android.hardware.SensorManager.getOrientation(rotationMatrix, orientation);
                rawHeadingRad = normalizeAngle(orientation[0]);
                headingRad = normalizeAngle(rawHeadingRad + headingOffsetRad);
                pitchRad = orientation[1];
                rollRad = orientation[2];
                hasHeading = true;
                headingSource = (type == Sensor.TYPE_GAME_ROTATION_VECTOR) ? "game_rotation" : "rotation";
                long now = System.currentTimeMillis();
                if (now - lastHeadingLogMs >= 1000) {
                    lastHeadingLogMs = now;
                    log(String.format(Locale.US,
                            "FUSION: headingRaw=%.1fdeg headingRoom=%.1fdeg pitch=%.1fdeg roll=%.1fdeg offset=%.1fdeg src=%s",
                            Math.toDegrees(rawHeadingRad),
                            Math.toDegrees(headingRad),
                            Math.toDegrees(pitchRad),
                            Math.toDegrees(rollRad),
                            Math.toDegrees(headingOffsetRad)
                            , headingSource
                    ));
                }
            }
            return;
        }

        if (type == Sensor.TYPE_STEP_DETECTOR) {
            synchronized (this) {
                advanceOneStep("android_step");
            }
            return;
        }

        if (type == Sensor.TYPE_STEP_COUNTER) {
            synchronized (this) {
                if (Float.isNaN(stepCounterBaseline)) {
                    stepCounterBaseline = event.values[0];
                }
                long absoluteSteps = Math.max(0L, Math.round(event.values[0] - stepCounterBaseline));
                if (absoluteSteps != stepCount) {
                    log("FUSION: stepCounter absolute=" + absoluteSteps + " detectorSteps=" + stepCount);
                }
            }
            return;
        }

        if (type == Sensor.TYPE_LINEAR_ACCELERATION) {
            synchronized (this) {
                double linearMagnitude = magnitude(event.values);
                linearAccelEma = ema(linearAccelEma, linearMagnitude, 0.20);
                updateMotionState();
                maybeDetectCustomStep(linearMagnitude, event.timestamp);
            }
            return;
        }

        if (type == Sensor.TYPE_GYROSCOPE) {
            synchronized (this) {
                gyroEma = ema(gyroEma, magnitude(event.values), 0.20);
                updateMotionState();
            }
            return;
        }

        if (type == Sensor.TYPE_GRAVITY) {
            synchronized (this) {
                System.arraycopy(event.values, 0, gravityValues, 0, Math.min(3, event.values.length));
            }
            return;
        }

        if (type == Sensor.TYPE_ACCELEROMETER) {
            synchronized (this) {
                System.arraycopy(event.values, 0, accelerometerValues, 0, Math.min(3, event.values.length));
                accelNormEma = ema(accelNormEma, magnitude(event.values), 0.10);
            }
            return;
        }

        if (type == Sensor.TYPE_MAGNETIC_FIELD) {
            synchronized (this) {
                System.arraycopy(event.values, 0, magnetometerValues, 0, Math.min(3, event.values.length));
                magneticFieldEma = ema(magneticFieldEma, magnitude(event.values), 0.10);
            }
            return;
        }

        if (type == Sensor.TYPE_PRESSURE) {
            synchronized (this) {
                pressureHpa = event.values[0];
                double altitude = android.hardware.SensorManager.getAltitude(
                        android.hardware.SensorManager.PRESSURE_STANDARD_ATMOSPHERE, (float) pressureHpa
                );
                if (!hasPressureBaseline) {
                    pressureBaselineAltitudeMeters = altitude;
                    hasPressureBaseline = true;
                }
                relativeAltitudeMeters = altitude - pressureBaselineAltitudeMeters;
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // No-op
    }

    private synchronized void clampToRoom() {
        double w = JsonOps.roomWidthMeters;
        double h = JsonOps.roomHeightMeters;
        if (w > 0 && h > 0) {
            double oldX = fusedXMeters;
            double oldY = fusedYMeters;
            if (kalmanFilter.isInitialized()) {
                kalmanFilter.clampToRoom(w, h);
                fusedXMeters = kalmanFilter.getX();
                fusedYMeters = kalmanFilter.getY();
            } else {
                fusedXMeters = clamp(fusedXMeters, 0.0, w);
                fusedYMeters = clamp(fusedYMeters, 0.0, h);
            }
            if (oldX != fusedXMeters || oldY != fusedYMeters) {
                log(String.format(Locale.US, "FUSION: clamped to room -> (%.2f, %.2f)", fusedXMeters, fusedYMeters));
            }
        }
    }

    private static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }

    private void register(Sensor sensor, int rate) {
        if (sensor != null && platformSensorManager != null) {
            try {
                platformSensorManager.registerListener(this, sensor, rate);
            } catch (SecurityException e) {
                log("FUSION: sensor registration denied for " + sensor.getName() + ": " + e.getMessage());
            }
        }
    }

    private void updateMotionState() {
        long now = System.currentTimeMillis();
        boolean stationaryCandidate;

        if (isStationary) {
            stationaryCandidate = !(linearAccelEma > MOVING_LINEAR_ACCEL_EPS || gyroEma > MOVING_GYRO_EPS);
        } else {
            stationaryCandidate = linearAccelEma < STATIONARY_LINEAR_ACCEL_EPS && gyroEma < STATIONARY_GYRO_EPS;
        }

        if (stationaryCandidate != pendingStationaryState) {
            pendingStationaryState = stationaryCandidate;
            pendingMotionStateSinceMs = now;
            return;
        }

        if (pendingStationaryState != isStationary &&
                now - pendingMotionStateSinceMs >= MOTION_STATE_HYSTERESIS_MS) {
            isStationary = pendingStationaryState;
            log(String.format(Locale.US,
                    "FUSION: motion state -> %s linAcc=%.2f gyro=%.2f accel=%.2f mag=%.2f alt=%.2f",
                    isStationary ? "STATIONARY" : "MOVING",
                    linearAccelEma, gyroEma, accelNormEma, magneticFieldEma, relativeAltitudeMeters
            ));
        }
    }

    private double computeDynamicStepLengthMeters() {
        if (!adaptiveTuningEnabled) {
            lastPdrQualityScore = 1.0;
            return stepLengthMeters;
        }
        // Step length is adjusted gently from motion/tilt signals. The base stride
        // remains the thesis parameter, while these terms reduce obvious oversteps.
        lastPdrQualityScore = calculatePdrQualityScore();
        double step = stepLengthMeters;
        double motionBoost = clamp((linearAccelEma - 0.35) * 0.20, -0.08, 0.18);
        double tiltPenalty = clamp(Math.abs(Math.toDegrees(pitchRad)) / 90.0, 0.0, 0.35);
        double gyroPenalty = clamp((gyroEma - 0.8) * 0.08, 0.0, 0.12);
        step = step * (1.0 + motionBoost - tiltPenalty - gyroPenalty);
        if (Math.abs(relativeAltitudeMeters) >= FLOOR_CHANGE_ALTITUDE_M) {
            step *= 0.85;
        }
        step *= clamp(0.65 + (0.35 * lastPdrQualityScore), 0.65, 1.0);
        return clamp(step, minStepLengthMeters, maxStepLengthMeters);
    }

    private static double magnitude(float[] values) {
        if (values == null || values.length < 3) {
            return 0.0;
        }
        return Math.sqrt(
                (values[0] * values[0]) +
                (values[1] * values[1]) +
                (values[2] * values[2])
        );
    }

    private static double ema(double previous, double sample, double alpha) {
        return (alpha * sample) + ((1.0 - alpha) * previous);
    }

    private static double normalizeAngle(double angleRad) {
        while (angleRad <= -Math.PI) angleRad += 2.0 * Math.PI;
        while (angleRad > Math.PI) angleRad -= 2.0 * Math.PI;
        return angleRad;
    }

    private double bleMeasurementStdMeters(int filteredBeaconCount) {
        return bleMeasurementStdMeters(filteredBeaconCount, 1.0);
    }

    private double bleMeasurementStdMeters(int filteredBeaconCount, double bleQualityScore) {
        double std = isStationary
                ? KalmanPositionFilter.BLE_STD_STATIONARY_METERS
                : KalmanPositionFilter.BLE_STD_MOVING_METERS;
        if (filteredBeaconCount >= 4) {
            std *= 0.85;
        }
        if (adaptiveTuningEnabled) {
            double quality = clamp(bleQualityScore, 0.05, 1.0);
            std *= 1.0 + ((1.0 - quality) * 1.35);
            if (isStationary && quality >= 0.65) {
                std *= 0.85;
            }
        }
        return clamp(std, KalmanPositionFilter.BLE_STD_MIN_METERS, KalmanPositionFilter.BLE_STD_MAX_METERS);
    }

    private double calculatePdrQualityScore() {
        if (!hasHeading) {
            return 0.0;
        }
        double gyroScore = 1.0 - clamp(gyroEma / Math.max(customStepMaxGyro, 0.10), 0.0, 1.0);
        double accelScore = 1.0 - clamp(Math.abs(linearAccelEma - 0.45) / 2.0, 0.0, 1.0);
        double pitchScore = 1.0 - clamp(Math.abs(Math.toDegrees(pitchRad)) / customStepMaxAbsPitchDegrees, 0.0, 1.0);
        double rollScore = 1.0 - clamp(Math.abs(Math.toDegrees(rollRad)) / customStepMaxAbsRollDegrees, 0.0, 1.0);
        return clamp((0.30 * gyroScore) + (0.25 * accelScore) + (0.20 * pitchScore) + (0.20 * rollScore) + 0.05, 0.05, 1.0);
    }

    private void syncFusedFromKalman() {
        if (!kalmanFilter.isInitialized()) {
            return;
        }
        clampToRoom();
        fusedXMeters = kalmanFilter.getX();
        fusedYMeters = kalmanFilter.getY();
        hasFusedPosition = true;
    }

    private static void log(String message) {
        Log.d(TAG, message);
    }

    private void maybeDetectCustomStep(double linearMagnitude, long eventTimestampNs) {
        if (!ENABLE_CUSTOM_ACCEL_STEP_DETECTION) {
            return;
        }
        if (stepDetector != null) {
            return;
        }
        long nowMs = sensorEventTimeMs(eventTimestampNs);
        if (linearMagnitude < customStepResetThreshold) {
            customStepArmed = true;
            return;
        }
        if (nowMs < customStepSuppressedUntilMs) {
            logStepCandidate("reject:suppressed_after_anchor", nowMs, linearMagnitude);
            return;
        }
        if (isStationary) {
            logStepCandidate("reject:stationary", nowMs, linearMagnitude);
            return;
        }
        if (!customStepArmed) {
            logStepCandidate("reject:not_armed", nowMs, linearMagnitude);
            return;
        }
        if (linearMagnitude < customStepPeakThreshold) {
            logStepCandidate("reject:weak_peak", nowMs, linearMagnitude);
            return;
        }
        long sinceLastMs = nowMs - lastCustomStepMs;
        if (sinceLastMs < customStepMinIntervalMs) {
            logStepCandidate("reject:min_interval", nowMs, linearMagnitude);
            return;
        }
        if (gyroEma > customStepMaxGyro) {
            logStepCandidate("reject:gyro_unstable", nowMs, linearMagnitude);
            return;
        }
        if (Math.abs(Math.toDegrees(pitchRad)) > customStepMaxAbsPitchDegrees ||
                Math.abs(Math.toDegrees(rollRad)) > customStepMaxAbsRollDegrees) {
            logStepCandidate("reject:tilt_unstable", nowMs, linearMagnitude);
            return;
        }

        customStepArmed = false;
        lastCustomStepMs = nowMs;
        logStepCandidate("accept", nowMs, linearMagnitude);
        advanceOneStep("custom_accel");
    }

    private static long sensorEventTimeMs(long eventTimestampNs) {
        return eventTimestampNs > 0 ? eventTimestampNs / 1_000_000L : currentSensorClockMs();
    }

    private static long currentSensorClockMs() {
        return SystemClock.elapsedRealtimeNanos() / 1_000_000L;
    }

    private void advanceOneStep(String source) {
        if (!hasFusedPosition || !hasHeading) {
            return;
        }
        if (Math.abs(relativeAltitudeMeters) >= FLOOR_CHANGE_ALTITUDE_M) {
            log(String.format(Locale.US, "FUSION: %s step ignored due to altitude shift=%.2fm", source, relativeAltitudeMeters));
            return;
        }

        dynamicStepLengthMeters = computeDynamicStepLengthMeters();
        StepVector stepVector = headingToMapDelta(dynamicStepLengthMeters, headingRad);
        double dx = stepVector.dxMeters;
        double dy = stepVector.dyMeters;
        double beforeX = fusedXMeters;
        double beforeY = fusedYMeters;
        double predictedX = beforeX + dx;
        double predictedY = beforeY + dy;

        // Dead reckoning predicts the Kalman state. Later accepted BLE fixes correct
        // accumulated drift without discarding the smooth step path.
        double stepStd = adaptiveTuningEnabled
                ? KalmanPositionFilter.STEP_POSITION_STD_METERS * (1.0 + ((1.0 - lastPdrQualityScore) * 1.25))
                : KalmanPositionFilter.STEP_POSITION_STD_METERS;
        kalmanFilter.predictStep(dx, dy, currentSensorClockMs(), isStationary, stepStd);
        syncFusedFromKalman();
        boolean clamped = Math.abs(fusedXMeters - predictedX) > 1e-6 || Math.abs(fusedYMeters - predictedY) > 1e-6;
        stepCount++;
        lastStepDetectorMs = System.currentTimeMillis();
        log(String.format(Locale.US,
                "PDR_STEP: source=%s rawHeading=%.1fdeg offset=%.1fdeg correctedHeading=%.1fdeg len=%.2f dx=%.2f dy=%.2f before=(%.2f,%.2f) predicted=(%.2f,%.2f) after=(%.2f,%.2f) clamped=%s v=(%.2f, %.2f) linAcc=%.2f gyro=%.2f pitch=%.1f roll=%.1f stationary=%s pdrQuality=%.2f adaptive=%s",
                source,
                Math.toDegrees(rawHeadingRad),
                Math.toDegrees(headingOffsetRad),
                Math.toDegrees(headingRad),
                dynamicStepLengthMeters,
                dx,
                dy,
                beforeX,
                beforeY,
                predictedX,
                predictedY,
                fusedXMeters,
                fusedYMeters,
                clamped,
                kalmanFilter.getVx(),
                kalmanFilter.getVy(),
                linearAccelEma,
                gyroEma,
                Math.toDegrees(pitchRad),
                Math.toDegrees(rollRad),
                isStationary,
                lastPdrQualityScore,
                adaptiveTuningEnabled
        ));
    }

    /**
     * Coordinate convention:
     * - Android azimuth/heading is 0 deg toward magnetic north and increases clockwise.
     * - The app's map frame is x increasing with JSON columns and y increasing with JSON rows.
     * - A corrected heading of +90 deg moves +x; -90 deg moves -x; 0 deg moves +y.
     * Room/device alignment is handled only by headingOffsetDegrees from configuration.
     */
    public static StepVector headingToMapDelta(double stepLengthMeters, double correctedHeadingRad) {
        return new StepVector(
                stepLengthMeters * Math.sin(correctedHeadingRad),
                stepLengthMeters * Math.cos(correctedHeadingRad)
        );
    }

    private void logStepCandidate(String status, long nowMs, double linearMagnitude) {
        boolean acceptedOrImportant = "accept".equals(status)
                || status.contains("min_interval")
                || status.contains("gyro")
                || status.contains("tilt")
                || status.contains("suppressed");
        if (!acceptedOrImportant && nowMs - lastRejectedStepCandidateLogMs < 1000L) {
            return;
        }
        if (!acceptedOrImportant) {
            lastRejectedStepCandidateLogMs = nowMs;
        }
        Log.d(TAG, String.format(Locale.US,
                "PDR_STEP: candidate status=%s dtMs=%d linAcc=%.2f gyro=%.2f pitch=%.1f roll=%.1f armed=%s stationary=%s peak=%.2f reset=%.2f minIntervalMs=%d",
                status,
                nowMs - lastCustomStepMs,
                linearMagnitude,
                gyroEma,
                Math.toDegrees(pitchRad),
                Math.toDegrees(rollRad),
                customStepArmed,
                isStationary,
                customStepPeakThreshold,
                customStepResetThreshold,
                customStepMinIntervalMs
        ));
    }
}
