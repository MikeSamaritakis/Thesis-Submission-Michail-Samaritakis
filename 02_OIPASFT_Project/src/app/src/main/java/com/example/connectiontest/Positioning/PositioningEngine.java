package com.example.connectiontest.Positioning;

import com.example.connectiontest.BeaconManager.BeaconLocation;
import com.example.connectiontest.BeaconManager.bleBeacon;
import com.example.connectiontest.JsonOps.JsonOps;
import com.example.connectiontest.TrilaterationUtils.CoordinateUtils;
import com.example.connectiontest.TrilaterationUtils.PositionGate;

import android.util.Log;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static com.example.connectiontest.TrilaterationUtils.TrilaterationUtils.trilaterationImplementation;

/**
 * BLE positioning decision engine.
 *
 * MainActivity gives this class the current live beacons and the fresh scan batch.
 * The engine decides whether BLE is trustworthy enough to produce an absolute
 * position: it reports missing beacons, filters impossible distances, solves
 * trilateration, clamps near-boundary results, and rejects large jumps.
 */
public class PositioningEngine {

    private static final String TAG = "PositioningEngine";

    public static final class Config {
        public final double minDistanceMeters;
        public final double distanceMarginMeters;
        public final double boundaryClampMarginMeters;
        public final double maxJumpMeters;
        public final int rssiAverageWindowSize;
        public final double residualRejectMeters;

        public Config(double minDistanceMeters, double distanceMarginMeters, double maxJumpMeters) {
            this(minDistanceMeters, distanceMarginMeters, 0.20, maxJumpMeters, 1);
        }

        public Config(double minDistanceMeters, double distanceMarginMeters, double maxJumpMeters, int rssiAverageWindowSize) {
            this(minDistanceMeters, distanceMarginMeters, 0.20, maxJumpMeters, rssiAverageWindowSize);
        }

        public Config(double minDistanceMeters, double distanceMarginMeters, double boundaryClampMarginMeters, double maxJumpMeters, int rssiAverageWindowSize) {
            this(minDistanceMeters, distanceMarginMeters, boundaryClampMarginMeters, maxJumpMeters, rssiAverageWindowSize, 0.85);
        }

        public Config(double minDistanceMeters, double distanceMarginMeters, double boundaryClampMarginMeters, double maxJumpMeters, int rssiAverageWindowSize, double residualRejectMeters) {
            this.minDistanceMeters = minDistanceMeters;
            this.distanceMarginMeters = distanceMarginMeters;
            this.boundaryClampMarginMeters = Math.max(0.0, boundaryClampMarginMeters);
            this.maxJumpMeters = maxJumpMeters;
            this.rssiAverageWindowSize = Math.max(1, rssiAverageWindowSize);
            this.residualRejectMeters = Math.max(0.10, residualRejectMeters);
        }
    }

    public static final class Result {
        public final String mode;
        public final String reason;
        public final List<String> missingBeaconIds;
        public final int liveCount;
        public final int filteredCount;
        public final double bleXMeters;
        public final double bleYMeters;
        public final boolean acceptedBle;
        public final boolean needsFusionFallback;
        public final boolean hadPreviousAccepted;
        public final double previousAcceptedXMeters;
        public final double previousAcceptedYMeters;
        public final boolean adaptiveTuningEnabled;
        public final double bleQualityScore;
        public final double rssiNoiseDb;
        public final double residualRmsMeters;
        public final double residualMaxMeters;
        public final double geometryAreaMeters;

        private Result(
                String mode,
                String reason,
                List<String> missingBeaconIds,
                int liveCount,
                int filteredCount,
                double bleXMeters,
                double bleYMeters,
                boolean acceptedBle,
                boolean needsFusionFallback,
                boolean hadPreviousAccepted,
                double previousAcceptedXMeters,
                double previousAcceptedYMeters,
                boolean adaptiveTuningEnabled,
                double bleQualityScore,
                double rssiNoiseDb,
                double residualRmsMeters,
                double residualMaxMeters,
                double geometryAreaMeters
        ) {
            this.mode = mode;
            this.reason = reason;
            this.missingBeaconIds = missingBeaconIds;
            this.liveCount = liveCount;
            this.filteredCount = filteredCount;
            this.bleXMeters = bleXMeters;
            this.bleYMeters = bleYMeters;
            this.acceptedBle = acceptedBle;
            this.needsFusionFallback = needsFusionFallback;
            this.hadPreviousAccepted = hadPreviousAccepted;
            this.previousAcceptedXMeters = previousAcceptedXMeters;
            this.previousAcceptedYMeters = previousAcceptedYMeters;
            this.adaptiveTuningEnabled = adaptiveTuningEnabled;
            this.bleQualityScore = bleQualityScore;
            this.rssiNoiseDb = rssiNoiseDb;
            this.residualRmsMeters = residualRmsMeters;
            this.residualMaxMeters = residualMaxMeters;
            this.geometryAreaMeters = geometryAreaMeters;
        }
    }

    private final Config config;
    private double lastAcceptedXMeters = Double.NaN;
    private double lastAcceptedYMeters = Double.NaN;
    private long lastAcceptedTimeMs = 0L;

    public PositioningEngine(Config config) {
        this.config = config;
    }

    public Result process(
            List<bleBeacon> currentLiveBeacons,
            List<bleBeacon> solveBeacons,
            List<BeaconLocation> knownLocations,
            int[][] floorGrid
    ) {
        return process(currentLiveBeacons, solveBeacons, knownLocations, floorGrid, false);
    }

    public Result process(
            List<bleBeacon> currentLiveBeacons,
            List<bleBeacon> solveBeacons,
            List<BeaconLocation> knownLocations,
            int[][] floorGrid,
            boolean adaptiveTuningEnabled
    ) {
        List<bleBeacon> safeLive = currentLiveBeacons == null ? new ArrayList<>() : currentLiveBeacons;
        List<bleBeacon> safeSolve = solveBeacons == null ? new ArrayList<>() : solveBeacons;
        List<BeaconLocation> safeKnown = knownLocations == null ? new ArrayList<>() : knownLocations;

        // Work only with beacons from the current scan batch for solving, while the
        // live list remains useful for diagnostics and missing-beacon reporting.
        List<String> missingBeaconIds = findMissingBeaconIds(safeLive, safeKnown);
        double roomDiag = Math.hypot(JsonOps.roomWidthMeters, JsonOps.roomHeightMeters);
        double maxSolveDist = roomDiag + config.distanceMarginMeters;
        List<bleBeacon> filtered = PositionGate.filterByDistance(
                safeSolve,
                config.minDistanceMeters,
                maxSolveDist,
                config.rssiAverageWindowSize
        );
        logSolveBeaconDiagnostics(safeSolve, maxSolveDist);

        double geometryArea = geometryAreaMeters(filtered);
        Log.d(TAG, String.format(
                Locale.US,
                "gate min=%.2f max=%.2f roomDiag=%.2f live=%d solve=%d filtered=%d geometryArea=%.2f rssiAvgWindow=%d",
                config.minDistanceMeters,
                maxSolveDist,
                roomDiag,
                safeLive.size(),
                safeSolve.size(),
                filtered.size(),
                geometryArea,
                config.rssiAverageWindowSize
        ));

        if (filtered.size() < 3) {
            return fallback("LESS_THAN_3_BEACONS", missingBeaconIds, safeLive.size(), filtered.size(), adaptiveTuningEnabled, filtered);
        }

        double[] position = trilaterationImplementation(filtered, config.rssiAverageWindowSize);
        if (position == null || floorGrid == null) {
            return fallback("TRILATERATION_NULL_OR_GRID_NULL", missingBeaconIds, safeLive.size(), filtered.size(), adaptiveTuningEnabled, filtered);
        }

        double rawXMeters = position[0];
        double rawYMeters = position[1];
        ResidualStats residualStats = calculateResidualStats(filtered, rawXMeters, rawYMeters);
        Log.d(TAG, String.format(
                Locale.US,
                "BLE_RESIDUAL: solved=(%.2f,%.2f) rms=%.2f max=%.2f worst=%s threshold=%.2f",
                rawXMeters,
                rawYMeters,
                residualStats.rmsResidualMeters,
                residualStats.maxResidualMeters,
                residualStats.worstBeaconId,
                config.residualRejectMeters
        ));
        if (residualStats.rmsResidualMeters > config.residualRejectMeters ||
                residualStats.maxResidualMeters > config.residualRejectMeters) {
            Log.d(TAG, String.format(
                    Locale.US,
                    "reject residual solved=(%.2f,%.2f) rms=%.2f max=%.2f threshold=%.2f worst=%s",
                    rawXMeters,
                    rawYMeters,
                    residualStats.rmsResidualMeters,
                    residualStats.maxResidualMeters,
                    config.residualRejectMeters,
                    residualStats.worstBeaconId
            ));
            return rejected("HIGH_RESIDUAL", missingBeaconIds, safeLive.size(), filtered.size(), adaptiveTuningEnabled, filtered, residualStats, geometryArea);
        }
        if (isOutsideRoomByMoreThanMargin(rawXMeters, rawYMeters, config.boundaryClampMarginMeters)) {
            Log.d(TAG, String.format(
                    Locale.US,
                    "reject OOB pos(%.2f,%.2f) room(%.2f,%.2f) boundaryMargin=%.2f",
                    rawXMeters,
                    rawYMeters,
                    JsonOps.roomWidthMeters,
                    JsonOps.roomHeightMeters,
                    config.boundaryClampMarginMeters
            ));
            return rejected("OUT_OF_BOUNDS", missingBeaconIds, safeLive.size(), filtered.size(), adaptiveTuningEnabled, filtered, residualStats, geometryArea);
        }

        double xMeters = clamp(rawXMeters, 0.0, JsonOps.roomWidthMeters);
        double yMeters = clamp(rawYMeters, 0.0, JsonOps.roomHeightMeters);
        boolean boundaryClamped = xMeters != rawXMeters || yMeters != rawYMeters;
        if (boundaryClamped) {
            Log.d(TAG, String.format(
                    Locale.US,
                    "clamp near-boundary BLE pos raw(%.2f,%.2f) -> room(%.2f,%.2f)",
                    rawXMeters,
                    rawYMeters,
                    xMeters,
                    yMeters
            ));
        }

        // Jump rejection protects the Kalman filter from BLE outliers that survive
        // the distance gates. Rejected BLE positions are not passed into fusion.
        if (!Double.isNaN(lastAcceptedXMeters) &&
                PositionGate.isJumpTooLarge(
                        xMeters,
                        yMeters,
                        lastAcceptedXMeters,
                        lastAcceptedYMeters,
                        config.maxJumpMeters
                )) {
            Log.d(TAG, String.format(
                    Locale.US,
                    "reject jump new(%.2f,%.2f) last(%.2f,%.2f) maxJump=%.2f",
                    xMeters,
                    yMeters,
                    lastAcceptedXMeters,
                    lastAcceptedYMeters,
                    config.maxJumpMeters
            ));
            return rejected("JUMP_TOO_LARGE", missingBeaconIds, safeLive.size(), filtered.size(), adaptiveTuningEnabled, filtered, residualStats, geometryArea);
        }

        double previousX = lastAcceptedXMeters;
        double previousY = lastAcceptedYMeters;
        boolean hadPrevious = !Double.isNaN(previousX) && !Double.isNaN(previousY);
        lastAcceptedXMeters = xMeters;
        lastAcceptedYMeters = yMeters;
        lastAcceptedTimeMs = System.currentTimeMillis();
        double avgNoise = averageRssiNoise(filtered);
        double qualityScore = adaptiveTuningEnabled
                ? calculateBleQuality(filtered, residualStats, geometryArea)
                : 1.0;

        Log.d(TAG, String.format(Locale.US,
                "accept BLE pos(m)=(%.2f, %.2f) adaptive=%s quality=%.2f rssiNoise=%.2f residualRms=%.2f geometry=%.2f",
                xMeters, yMeters, adaptiveTuningEnabled, qualityScore, avgNoise,
                residualStats.rmsResidualMeters, geometryArea));
        return new Result(
                "BLE_ACCEPTED",
                boundaryClamped ? "OK_BOUNDARY_CLAMPED" : "OK",
                missingBeaconIds,
                safeLive.size(),
                filtered.size(),
                xMeters,
                yMeters,
                true,
                false,
                hadPrevious,
                previousX,
                previousY,
                adaptiveTuningEnabled,
                qualityScore,
                avgNoise,
                residualStats.rmsResidualMeters,
                residualStats.maxResidualMeters,
                geometryArea
        );
    }

    private boolean isOutsideRoomByMoreThanMargin(double xMeters, double yMeters, double marginMeters) {
        return xMeters < -marginMeters ||
                xMeters > JsonOps.roomWidthMeters + marginMeters ||
                yMeters < -marginMeters ||
                yMeters > JsonOps.roomHeightMeters + marginMeters;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    public long getLastAcceptedTimeMs() {
        return lastAcceptedTimeMs;
    }

    /**
     * Manual map anchors are stronger than old BLE history. Seed the jump gate from
     * the known anchor so the next BLE fix is judged against the reset tracker state.
     */
    public void resetLastAcceptedPosition(double xMeters, double yMeters) {
        if (!Double.isFinite(xMeters) || !Double.isFinite(yMeters)) {
            lastAcceptedXMeters = Double.NaN;
            lastAcceptedYMeters = Double.NaN;
            lastAcceptedTimeMs = 0L;
            return;
        }
        lastAcceptedXMeters = xMeters;
        lastAcceptedYMeters = yMeters;
        lastAcceptedTimeMs = System.currentTimeMillis();
    }

    private Result fallback(String reason, List<String> missingBeaconIds, int liveCount, int filteredCount,
                            boolean adaptiveTuningEnabled, List<bleBeacon> filtered) {
        return new Result(
                "FUSION_FALLBACK",
                reason,
                missingBeaconIds,
                liveCount,
                filteredCount,
                Double.NaN,
                Double.NaN,
                false,
                true,
                false,
                Double.NaN,
                Double.NaN,
                adaptiveTuningEnabled,
                0.0,
                averageRssiNoise(filtered),
                Double.NaN,
                Double.NaN,
                geometryAreaMeters(filtered)
        );
    }

    private Result rejected(String reason, List<String> missingBeaconIds, int liveCount, int filteredCount,
                            boolean adaptiveTuningEnabled, List<bleBeacon> filtered,
                            ResidualStats residualStats, double geometryArea) {
        return new Result(
                "BLE_REJECTED",
                reason,
                missingBeaconIds,
                liveCount,
                filteredCount,
                Double.NaN,
                Double.NaN,
                false,
                false,
                false,
                Double.NaN,
                Double.NaN,
                adaptiveTuningEnabled,
                0.0,
                averageRssiNoise(filtered),
                residualStats == null ? Double.NaN : residualStats.rmsResidualMeters,
                residualStats == null ? Double.NaN : residualStats.maxResidualMeters,
                geometryArea
        );
    }

    private double calculateBleQuality(List<bleBeacon> filtered, ResidualStats residualStats, double geometryArea) {
        double residualScore = 1.0 - clamp(residualStats.rmsResidualMeters / config.residualRejectMeters, 0.0, 1.0);
        double maxResidualScore = 1.0 - clamp(residualStats.maxResidualMeters / config.residualRejectMeters, 0.0, 1.0);
        double noise = averageRssiNoise(filtered);
        double noiseScore = Double.isFinite(noise) ? 1.0 - clamp(noise / 12.0, 0.0, 1.0) : 0.45;
        double countScore = Math.min(1.0, filtered.size() / 4.0);
        double geometryScore = clamp(geometryArea / 1.0, 0.25, 1.0);
        return clamp(
                (0.35 * residualScore) +
                        (0.20 * maxResidualScore) +
                        (0.25 * noiseScore) +
                        (0.10 * countScore) +
                        (0.10 * geometryScore),
                0.05,
                1.0
        );
    }

    private double averageRssiNoise(List<bleBeacon> beacons) {
        if (beacons == null || beacons.isEmpty()) {
            return Double.NaN;
        }
        double sum = 0.0;
        int count = 0;
        for (bleBeacon beacon : beacons) {
            double noise = beacon.getRssiNoiseDb(config.rssiAverageWindowSize);
            if (Double.isFinite(noise)) {
                sum += noise;
                count++;
            }
        }
        return count == 0 ? Double.NaN : sum / count;
    }

    private List<String> findMissingBeaconIds(List<bleBeacon> liveBeacons, List<BeaconLocation> knownLocations) {
        Set<String> liveKeys = new HashSet<>();
        for (bleBeacon beacon : liveBeacons) {
            liveKeys.add(beacon.getCompositeKey());
        }

        List<String> missingIds = new ArrayList<>();
        for (BeaconLocation known : knownLocations) {
            if (!liveKeys.contains(known.getCompositeKey())) {
                missingIds.add(known.getUniqueId());
                Log.d(TAG, "missing beacon key=" + known.getCompositeKey() + " id=" + known.getUniqueId());
            }
        }
        return missingIds;
    }

    private void logSolveBeaconDiagnostics(List<bleBeacon> solveBeacons, double maxSolveDist) {
        for (bleBeacon beacon : solveBeacons) {
            double distance = beacon.getDistanceFromAveragedRssi(config.rssiAverageWindowSize);
            double avgRssi = beacon.getAverageRssi(config.rssiAverageWindowSize);
            double quality = estimateBeaconQuality(beacon, distance, maxSolveDist);
            String gate = "keep";
            if (distance <= 0.0) {
                gate = "drop:non_positive";
            } else if (distance < config.minDistanceMeters) {
                gate = "drop:too_close";
            } else if (distance > maxSolveDist) {
                gate = "drop:too_far";
            }
            Log.d(TAG, String.format(
                    Locale.US,
                    "BLE_DISTANCE: id=%s row=%d col=%d x=%.2f y=%.2f rawRssi=%d medianRssi=%d avgRssi=%.1f rssi1m=%d n=%.2f samples=%d avgWindow=%d dist=%.2f quality=%.2f gate=%s",
                    beacon.getJsonId(),
                    beacon.getBeaconRow(),
                    beacon.getBeaconCol(),
                    CoordinateUtils.beaconXMeters(beacon),
                    CoordinateUtils.beaconYMeters(beacon),
                    beacon.getLatestRssi(),
                    beacon.getSmoothedRssi(),
                    avgRssi,
                    beacon.getRssi1Meter(),
                    JsonOps.pathLossExponent,
                    beacon.getRssiSampleCount(),
                    config.rssiAverageWindowSize,
                    distance,
                    quality,
                    gate
            ));
        }
    }

    private double estimateBeaconQuality(bleBeacon beacon, double distance, double maxSolveDist) {
        if (beacon == null || !Double.isFinite(distance) || distance <= 0.0) {
            return 0.0;
        }
        double sampleScore = Math.min(1.0, beacon.getRssiSampleCount() / (double) config.rssiAverageWindowSize);
        double distanceScore = distance <= maxSolveDist ? 1.0 : 0.0;
        double rssiJump = Math.abs(beacon.getLatestRssi() - beacon.getAverageRssi(config.rssiAverageWindowSize));
        double stabilityScore = Math.max(0.0, 1.0 - (rssiJump / 20.0));
        return sampleScore * distanceScore * stabilityScore;
    }

    private ResidualStats calculateResidualStats(List<bleBeacon> beacons, double xMeters, double yMeters) {
        if (beacons == null || beacons.isEmpty()) {
            return new ResidualStats(Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, "none");
        }
        double sumSquares = 0.0;
        double maxResidual = 0.0;
        String worst = "none";
        int count = 0;
        for (bleBeacon beacon : beacons) {
            double bx = CoordinateUtils.beaconXMeters(beacon);
            double by = CoordinateUtils.beaconYMeters(beacon);
            double expected = beacon.getDistanceFromAveragedRssi(config.rssiAverageWindowSize);
            if (!Double.isFinite(bx) || !Double.isFinite(by) || !Double.isFinite(expected) || expected <= 0.0) {
                continue;
            }
            double actual = Math.hypot(xMeters - bx, yMeters - by);
            double residual = Math.abs(actual - expected);
            Log.d(TAG, String.format(
                    Locale.US,
                    "BLE_RESIDUAL: id=%s solvedDist=%.2f rssiDist=%.2f residual=%.2f",
                    beacon.getJsonId(),
                    actual,
                    expected,
                    residual
            ));
            sumSquares += residual * residual;
            if (residual > maxResidual) {
                maxResidual = residual;
                worst = beacon.getJsonId();
            }
            count++;
        }
        if (count == 0) {
            return new ResidualStats(Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, "none");
        }
        return new ResidualStats(Math.sqrt(sumSquares / count), maxResidual, worst);
    }

    private static final class ResidualStats {
        final double rmsResidualMeters;
        final double maxResidualMeters;
        final String worstBeaconId;

        ResidualStats(double rmsResidualMeters, double maxResidualMeters, String worstBeaconId) {
            this.rmsResidualMeters = rmsResidualMeters;
            this.maxResidualMeters = maxResidualMeters;
            this.worstBeaconId = worstBeaconId;
        }
    }

    private double geometryAreaMeters(List<bleBeacon> beacons) {
        if (beacons == null || beacons.size() < 3) {
            return 0.0;
        }

        double bestArea = 0.0;
        for (int i = 0; i < beacons.size() - 2; i++) {
            for (int j = i + 1; j < beacons.size() - 1; j++) {
                for (int k = j + 1; k < beacons.size(); k++) {
                    double area = triangleAreaMeters(beacons.get(i), beacons.get(j), beacons.get(k));
                    if (Double.isFinite(area)) {
                        bestArea = Math.max(bestArea, area);
                    }
                }
            }
        }
        if (bestArea < 0.25) {
            Log.d(TAG, String.format(Locale.US, "weak beacon geometry area=%.2f; spread beacons farther apart", bestArea));
        }
        return bestArea;
    }

    private double triangleAreaMeters(bleBeacon a, bleBeacon b, bleBeacon c) {
        double ax = CoordinateUtils.beaconXMeters(a);
        double ay = CoordinateUtils.beaconYMeters(a);
        double bx = CoordinateUtils.beaconXMeters(b);
        double by = CoordinateUtils.beaconYMeters(b);
        double cx = CoordinateUtils.beaconXMeters(c);
        double cy = CoordinateUtils.beaconYMeters(c);
        if (!Double.isFinite(ax) || !Double.isFinite(ay) ||
                !Double.isFinite(bx) || !Double.isFinite(by) ||
                !Double.isFinite(cx) || !Double.isFinite(cy)) {
            return Double.NaN;
        }
        return Math.abs((ax * (by - cy) + bx * (cy - ay) + cx * (ay - by)) / 2.0);
    }
}
