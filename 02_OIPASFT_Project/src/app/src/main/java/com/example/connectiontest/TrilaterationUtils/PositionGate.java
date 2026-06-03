package com.example.connectiontest.TrilaterationUtils;

import com.example.connectiontest.BeaconManager.bleBeacon;

import java.util.ArrayList;
import java.util.List;

/**
 * Small acceptance gates that run before or after trilateration.
 *
 * These checks are intentionally separate from the solver so tests can verify
 * why a beacon or solved position was rejected without touching the math.
 */
public final class PositionGate {

    private PositionGate() {}

    public static List<bleBeacon> filterByDistance(List<bleBeacon> beacons,
                                                   double minDistMeters,
                                                   double maxDistMeters) {
        return filterByDistance(beacons, minDistMeters, maxDistMeters, 1);
    }

    public static List<bleBeacon> filterByDistance(List<bleBeacon> beacons,
                                                   double minDistMeters,
                                                   double maxDistMeters,
                                                   int rssiAverageWindowSize) {
        List<bleBeacon> out = new ArrayList<>();
        for (bleBeacon b : beacons) {
            double d = b.getDistanceFromAveragedRssi(rssiAverageWindowSize);
            if (d <= 0) continue;
            if (d < minDistMeters) continue;
            if (d > maxDistMeters) continue;
            out.add(b);
        }
        return out;
    }

    public static boolean isJumpTooLarge(double newX, double newY,
                                         double lastX, double lastY,
                                         double maxJumpMeters) {
        double dx = newX - lastX;
        double dy = newY - lastY;
        return Math.hypot(dx, dy) > maxJumpMeters;
    }
}
