package com.example.connectiontest.BeaconManager;

import com.example.connectiontest.TrilaterationUtils.CoordinateUtils;
import com.example.connectiontest.JsonOps.JsonOps;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;

/**
 * Runtime state for one live beacon.
 *
 * BeaconLocation is static calibration data; bleBeacon adds live RSSI history,
 * last-seen time, and the latest distance estimate. The class keeps a small RSSI
 * window because every positioning solve should use recent radio evidence only.
 */
public class bleBeacon {
    private String name;
    private String address;
    private String UUID;
    private int major;
    private int minor;
    private String jsonId;
    private int rssi1Meter;
    private int beaconRow; // row=y in meters
    private int beaconCol; // col=x in meters
    private double beaconXMeters;
    private double beaconYMeters;
    private boolean exactPositionMeters;
    private float distanceFromUser;
    private long lastSeenMs;
    private int latestRssi = -100;
    private int smoothedRssi = -100;

    private Deque<Integer> rssiHistory = new ArrayDeque<>();
    private static final int RSSI_HISTORY_SIZE = 10;
    public bleBeacon(String name, String address, String UUID, int major, int minor, String jsonId, int rssi1Meter, int beaconRow, int beaconCol) {
        this(
                name,
                address,
                UUID,
                major,
                minor,
                jsonId,
                rssi1Meter,
                beaconRow,
                beaconCol,
                CoordinateUtils.gridColToXMeters(beaconCol),
                CoordinateUtils.gridRowToYMeters(beaconRow),
                false
        );
    }

    public bleBeacon(String name, String address, String UUID, int major, int minor, String jsonId, int rssi1Meter,
                     int beaconRow, int beaconCol, double beaconXMeters, double beaconYMeters, boolean exactPositionMeters) {
        this.name = name;
        this.address = address;
        this.UUID = UUID;
        this.major = major;
        this.minor = minor;
        this.jsonId = jsonId;
        this.rssi1Meter = rssi1Meter;
        this.beaconRow = beaconRow;
        this.beaconCol = beaconCol;
        this.beaconXMeters = beaconXMeters;
        this.beaconYMeters = beaconYMeters;
        this.exactPositionMeters = exactPositionMeters;
    }

    public synchronized void updateRssi(int newRssi) {
        updateRssi(newRssi, System.currentTimeMillis());
    }

    public synchronized void updateRssi(int newRssi, long seenAtMs) {
        if (rssiHistory.size() >= RSSI_HISTORY_SIZE) {
            rssiHistory.pollFirst();
        }
        rssiHistory.addLast(newRssi);
        this.latestRssi = newRssi;
        this.smoothedRssi = computeSmoothedRssi();
        this.distanceFromUser = calculateDistance(smoothedRssi);
        this.lastSeenMs = seenAtMs;
    }

    /**
     * Median smoothing is kept alongside averaged RSSI. The median is useful for
     * diagnostics because it shows whether a single spike is dominating the window.
     */
    private int computeSmoothedRssi() {
        if (rssiHistory.isEmpty()) return -100;
        List<Integer> sorted = new ArrayList<>(rssiHistory);
        Collections.sort(sorted);
        int mid = sorted.size() / 2;
        if ((sorted.size() & 1) == 1) {
            return sorted.get(mid);
        }
        return (sorted.get(mid - 1) + sorted.get(mid)) / 2;
    }

    private float calculateDistance(double rssi) {
        int txPower = rssi1Meter;   // calibrated RSSI at 1 m
        if (rssi == 0) return -1.0f;

        double n = JsonOps.pathLossExponent;
        if (!Double.isFinite(n) || n <= 0.0) {
            n = 2.0;
        }
        return (float) Math.pow(10d, (txPower - rssi) / (10.0 * n));
    }

    public synchronized float getDistanceFromUser() {
        return distanceFromUser;
    }

    public synchronized float getDistanceFromAveragedRssi(int measurementCount) {
        double averagedRssi = getAverageRssi(measurementCount);
        if (!Double.isFinite(averagedRssi)) {
            return distanceFromUser;
        }
        return calculateDistance(averagedRssi);
    }

    /**
     * Uses the newest N RSSI samples from the rolling history. This is the value used
     * by distance gating and trilateration in the current positioning pipeline.
     */
    public synchronized double getAverageRssi(int measurementCount) {
        if (rssiHistory.isEmpty()) {
            return Double.NaN;
        }
        int samplesToUse = Math.max(1, Math.min(measurementCount, rssiHistory.size()));
        int skipped = rssiHistory.size() - samplesToUse;
        int index = 0;
        double sum = 0.0;
        int used = 0;
        for (Integer rssi : rssiHistory) {
            if (index++ < skipped) {
                continue;
            }
            sum += rssi;
            used++;
        }
        return used == 0 ? Double.NaN : sum / used;
    }

    public synchronized double getRssiNoiseDb(int measurementCount) {
        if (rssiHistory.size() < 2) {
            return Double.NaN;
        }
        int samplesToUse = Math.max(2, Math.min(measurementCount, rssiHistory.size()));
        int skipped = rssiHistory.size() - samplesToUse;
        int index = 0;
        List<Integer> samples = new ArrayList<>();
        for (Integer rssi : rssiHistory) {
            if (index++ < skipped) {
                continue;
            }
            samples.add(rssi);
        }
        Collections.sort(samples);
        double median = median(samples);
        List<Double> deviations = new ArrayList<>();
        for (Integer rssi : samples) {
            deviations.add(Math.abs(rssi - median));
        }
        Collections.sort(deviations);
        double mad = medianDouble(deviations);
        // 1.4826 scales MAD toward standard deviation for normally distributed noise.
        return mad * 1.4826;
    }

    public synchronized double getRssiQualityScore(int measurementCount) {
        int samples = getRssiSampleCount();
        if (samples == 0) {
            return 0.0;
        }
        double sampleScore = Math.min(1.0, samples / (double) Math.max(1, measurementCount));
        double noise = getRssiNoiseDb(measurementCount);
        double noiseScore = Double.isFinite(noise) ? Math.max(0.0, 1.0 - (noise / 12.0)) : 0.45;
        return clamp(sampleScore * noiseScore, 0.0, 1.0);
    }

    public synchronized int getRssiSampleCount() {
        return rssiHistory.size();
    }

    public synchronized long getLastSeenMs() {
        return lastSeenMs;
    }

    public synchronized int getLatestRssi() {
        return latestRssi;
    }

    public synchronized int getSmoothedRssi() {
        return smoothedRssi;
    }

    public int getBeaconRow() {
        return beaconRow;
    }

    public int getBeaconCol() {
        return beaconCol;
    }

    public double getBeaconXMeters() {
        return beaconXMeters;
    }

    public double getBeaconYMeters() {
        return beaconYMeters;
    }

    public boolean hasExactPositionMeters() {
        return exactPositionMeters;
    }

    public String getUUID() {
        return UUID;
    }

    public int getMajor() {
        return major;
    }

    public int getMinor() {
        return minor;
    }

    public String getJsonId() {
        return jsonId;
    }

    public int getRssi1Meter() {
        return rssi1Meter;
    }

    public String getCompositeKey() {
        return BeaconLocation.buildCompositeKey(UUID, major, minor);
    }

    public String getAddress() {
        return address;
    }

    public String getName() {
        return name;
    }

    private static double median(List<Integer> values) {
        if (values == null || values.isEmpty()) {
            return Double.NaN;
        }
        int mid = values.size() / 2;
        if ((values.size() & 1) == 1) {
            return values.get(mid);
        }
        return (values.get(mid - 1) + values.get(mid)) / 2.0;
    }

    private static double medianDouble(List<Double> values) {
        if (values == null || values.isEmpty()) {
            return Double.NaN;
        }
        int mid = values.size() / 2;
        if ((values.size() & 1) == 1) {
            return values.get(mid);
        }
        return (values.get(mid - 1) + values.get(mid)) / 2.0;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
