package com.example.connectiontest.Positioning;

/**
 * Constant-velocity 2D Kalman filter for the final rendered position.
 *
 * The filter is intentionally framework-free so it can be unit tested on the JVM.
 * SensorManager owns the policy: manual anchors reset this filter exactly, steps
 * call predictStep, and accepted BLE fixes call updateBle.
 */
public final class KalmanPositionFilter {

    public static final double MANUAL_POSITION_VARIANCE = 0.01;
    public static final double BLE_INITIAL_POSITION_VARIANCE = 1.0;
    public static final double INITIAL_VELOCITY_VARIANCE = 1.0;
    public static final double STATIONARY_ACCEL_NOISE_MPS2 = 0.08;
    public static final double MOVING_ACCEL_NOISE_MPS2 = 0.60;
    public static final double STEP_POSITION_STD_METERS = 0.55;
    public static final double BLE_STD_STATIONARY_METERS = 0.60;
    public static final double BLE_STD_MOVING_METERS = 0.90;
    public static final double BLE_STD_MIN_METERS = 0.60;
    public static final double BLE_STD_MAX_METERS = 1.80;

    private static final double MIN_DT_SECONDS = 0.02;
    private static final double MAX_DT_SECONDS = 2.0;

    private final AxisFilter xAxis = new AxisFilter();
    private final AxisFilter yAxis = new AxisFilter();
    private boolean initialized = false;
    private long lastUpdateMs = 0L;

    public boolean isInitialized() {
        return initialized;
    }

    public void resetKnownPosition(double xMeters, double yMeters, long nowMs) {
        reset(xMeters, yMeters, MANUAL_POSITION_VARIANCE, INITIAL_VELOCITY_VARIANCE, nowMs);
    }

    /**
     * Initializes from BLE when no stronger anchor exists. This is less certain than
     * a manual map tap, so it starts with a wider position variance.
     */
    public void resetFromBle(double xMeters, double yMeters, long nowMs) {
        reset(xMeters, yMeters, BLE_INITIAL_POSITION_VARIANCE, INITIAL_VELOCITY_VARIANCE, nowMs);
    }

    public void predictTime(long nowMs, boolean stationary) {
        if (!initialized) {
            return;
        }
        predictTo(nowMs, stationary);
    }

    public void predictStep(double dxMeters, double dyMeters, long nowMs, boolean stationary) {
        predictStep(dxMeters, dyMeters, nowMs, stationary, STEP_POSITION_STD_METERS);
    }

    public void predictStep(double dxMeters, double dyMeters, long nowMs, boolean stationary, double stepStdMeters) {
        if (!initialized) {
            return;
        }
        double dtSeconds = predictTo(nowMs, stationary);
        double velocityDtSeconds = Math.max(dtSeconds, 0.25);
        double safeStepStd = clamp(stepStdMeters, 0.10, 1.50);
        xAxis.applyDisplacement(dxMeters, safeStepStd, velocityDtSeconds);
        yAxis.applyDisplacement(dyMeters, safeStepStd, velocityDtSeconds);
    }

    public void updateBle(double xMeters, double yMeters, long nowMs, double measurementStdMeters) {
        updateBle(xMeters, yMeters, nowMs, measurementStdMeters, false);
    }

    public void updateBle(double xMeters, double yMeters, long nowMs, double measurementStdMeters, boolean stationary) {
        if (!initialized) {
            resetFromBle(xMeters, yMeters, nowMs);
            return;
        }
        predictTo(nowMs, stationary);
        double std = clamp(measurementStdMeters, BLE_STD_MIN_METERS, BLE_STD_MAX_METERS);
        double variance = std * std;
        xAxis.update(xMeters, variance);
        yAxis.update(yMeters, variance);
    }

    public void clampToRoom(double widthMeters, double heightMeters) {
        if (!initialized || widthMeters <= 0.0 || heightMeters <= 0.0) {
            return;
        }
        xAxis.clamp(0.0, widthMeters);
        yAxis.clamp(0.0, heightMeters);
    }

    public double getX() {
        return xAxis.position;
    }

    public double getY() {
        return yAxis.position;
    }

    public double getVx() {
        return xAxis.velocity;
    }

    public double getVy() {
        return yAxis.velocity;
    }

    private void reset(double xMeters, double yMeters, double positionVariance, double velocityVariance, long nowMs) {
        xAxis.reset(xMeters, 0.0, positionVariance, velocityVariance);
        yAxis.reset(yMeters, 0.0, positionVariance, velocityVariance);
        initialized = true;
        lastUpdateMs = nowMs;
    }

    private double predictTo(long nowMs, boolean stationary) {
        long elapsedMs = lastUpdateMs == 0L ? 0L : nowMs - lastUpdateMs;
        double dtSeconds = clamp(elapsedMs / 1000.0, MIN_DT_SECONDS, MAX_DT_SECONDS);
        double accelNoise = stationary ? STATIONARY_ACCEL_NOISE_MPS2 : MOVING_ACCEL_NOISE_MPS2;
        // Velocity is damped aggressively when stationary so old walking motion does
        // not keep dragging the estimate after the phone settles.
        double velocityRetention = Math.pow(stationary ? 0.20 : 0.70, dtSeconds);
        xAxis.predict(dtSeconds, accelNoise, velocityRetention);
        yAxis.predict(dtSeconds, accelNoise, velocityRetention);
        lastUpdateMs = nowMs;
        return dtSeconds;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static final class AxisFilter {
        private double position = Double.NaN;
        private double velocity = 0.0;
        private double p00 = 1.0;
        private double p01 = 0.0;
        private double p10 = 0.0;
        private double p11 = 1.0;

        void reset(double position, double velocity, double positionVariance, double velocityVariance) {
            this.position = position;
            this.velocity = velocity;
            this.p00 = positionVariance;
            this.p01 = 0.0;
            this.p10 = 0.0;
            this.p11 = velocityVariance;
        }

        void predict(double dtSeconds, double accelNoise, double velocityRetention) {
            position += velocity * dtSeconds;

            double accelVariance = accelNoise * accelNoise;
            double dt2 = dtSeconds * dtSeconds;
            double dt3 = dt2 * dtSeconds;
            double dt4 = dt2 * dt2;
            double q00 = 0.25 * dt4 * accelVariance;
            double q01 = 0.5 * dt3 * accelVariance;
            double q11 = dt2 * accelVariance;

            double nextP00 = p00 + (dtSeconds * (p10 + p01)) + (dt2 * p11) + q00;
            double nextP01 = p01 + (dtSeconds * p11) + q01;
            double nextP10 = p10 + (dtSeconds * p11) + q01;
            double nextP11 = p11 + q11;

            p00 = nextP00;
            p01 = nextP01;
            p10 = nextP10;
            p11 = nextP11;
            velocity *= velocityRetention;
        }

        void applyDisplacement(double deltaMeters, double stepStdMeters, double dtSeconds) {
            position += deltaMeters;
            double measuredVelocity = deltaMeters / Math.max(dtSeconds, MIN_DT_SECONDS);
            velocity = (0.65 * velocity) + (0.35 * measuredVelocity);
            double stepVariance = stepStdMeters * stepStdMeters;
            p00 += stepVariance;
            p11 += stepVariance / Math.max(dtSeconds * dtSeconds, MIN_DT_SECONDS * MIN_DT_SECONDS);
        }

        void update(double measurement, double measurementVariance) {
            double residual = measurement - position;
            double innovation = p00 + measurementVariance;
            if (innovation <= 0.0) {
                return;
            }

            double k0 = p00 / innovation;
            double k1 = p10 / innovation;
            double oldP00 = p00;
            double oldP01 = p01;

            position += k0 * residual;
            velocity += k1 * residual;
            p00 = (1.0 - k0) * oldP00;
            p01 = (1.0 - k0) * oldP01;
            p10 = p10 - (k1 * oldP00);
            p11 = p11 - (k1 * oldP01);
        }

        void clamp(double min, double max) {
            double oldPosition = position;
            position = KalmanPositionFilter.clamp(position, min, max);
            if (oldPosition != position) {
                // A wall clamp is a constraint violation, not a confident motion
                // measurement. Increase uncertainty so later BLE fixes can pull
                // the estimate back instead of treating the boundary as certain.
                p00 += 0.25;
                p11 += 0.25;
            }
            if ((oldPosition < min && velocity < 0.0) || (oldPosition > max && velocity > 0.0)) {
                velocity = 0.0;
            }
        }
    }
}
