package com.example.connectiontest;

import com.example.connectiontest.Positioning.KalmanPositionFilter;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * JVM tests for the pure Kalman math.
 *
 * These tests protect the thesis-critical behavior: manual anchors are hard resets,
 * BLE updates are corrections rather than teleports, steps predict relative motion,
 * and room clamps prevent impossible rendered positions.
 */
public class KalmanPositionFilterTest {

    @Test
    public void manualReset_setsExactPositionAndZeroVelocity() {
        KalmanPositionFilter filter = new KalmanPositionFilter();

        filter.resetKnownPosition(1.25, 2.50, 1_000L);

        assertTrue(filter.isInitialized());
        assertEquals(1.25, filter.getX(), 0.0001);
        assertEquals(2.50, filter.getY(), 0.0001);
        assertEquals(0.0, filter.getVx(), 0.0001);
        assertEquals(0.0, filter.getVy(), 0.0001);
    }

    @Test
    public void manualReset_overridesPreviousBleState() {
        KalmanPositionFilter filter = new KalmanPositionFilter();
        filter.resetFromBle(0.0, 0.0, 1_000L);
        filter.updateBle(3.0, 3.0, 1_500L, 0.85);

        filter.resetKnownPosition(1.0, 1.5, 2_000L);

        assertEquals(1.0, filter.getX(), 0.0001);
        assertEquals(1.5, filter.getY(), 0.0001);
        assertEquals(0.0, filter.getVx(), 0.0001);
        assertEquals(0.0, filter.getVy(), 0.0001);
    }

    @Test
    public void bleUpdate_movesTowardMeasurementWithoutJumpingExactly() {
        KalmanPositionFilter filter = new KalmanPositionFilter();
        filter.resetFromBle(0.0, 0.0, 1_000L);

        filter.updateBle(2.0, 0.0, 1_250L, 1.0);

        assertTrue(filter.getX() > 0.0);
        assertTrue(filter.getX() < 2.0);
        assertEquals(0.0, filter.getY(), 0.0001);
    }

    @Test
    public void manualReset_stronglyResistsImmediateBleOutlier() {
        KalmanPositionFilter filter = new KalmanPositionFilter();
        filter.resetKnownPosition(1.0, 1.0, 1_000L);

        filter.updateBle(4.0, 4.0, 1_100L, KalmanPositionFilter.BLE_STD_MIN_METERS);

        assertTrue(filter.getX() > 1.0);
        assertTrue(filter.getY() > 1.0);
        assertTrue(filter.getX() < 1.5);
        assertTrue(filter.getY() < 1.5);
    }

    @Test
    public void repeatedBleUpdates_convergeTowardStableMeasurement() {
        KalmanPositionFilter filter = new KalmanPositionFilter();
        filter.resetFromBle(0.0, 0.0, 1_000L);

        filter.updateBle(2.0, 1.0, 1_500L, 0.85);
        filter.updateBle(2.0, 1.0, 2_000L, 0.85);
        filter.updateBle(2.0, 1.0, 2_500L, 0.85);
        filter.updateBle(2.0, 1.0, 3_000L, 0.85);

        assertTrue(filter.getX() > 1.0);
        assertTrue(filter.getX() < 2.2);
        assertTrue(filter.getY() > 0.5);
        assertTrue(filter.getY() < 1.2);
    }

    @Test
    public void stepPrediction_movesByHeadingDisplacement() {
        KalmanPositionFilter filter = new KalmanPositionFilter();
        filter.resetKnownPosition(1.0, 1.0, 1_000L);

        filter.predictStep(0.50, -0.25, 2_000L, false);

        assertEquals(1.50, filter.getX(), 0.0001);
        assertEquals(0.75, filter.getY(), 0.0001);
    }

    @Test
    public void stationaryPrediction_dampsStepVelocity() {
        KalmanPositionFilter filter = new KalmanPositionFilter();
        filter.resetKnownPosition(1.0, 1.0, 1_000L);
        filter.predictStep(0.50, 0.0, 2_000L, false);
        double movingVelocity = Math.abs(filter.getVx());

        filter.predictTime(3_000L, true);

        assertTrue(Math.abs(filter.getVx()) < movingVelocity);
    }

    @Test
    public void roomClamp_keepsPositionInsideRoom() {
        KalmanPositionFilter filter = new KalmanPositionFilter();
        filter.resetKnownPosition(1.0, 1.0, 1_000L);
        filter.predictStep(5.0, -5.0, 2_000L, false);

        filter.clampToRoom(3.0, 2.0);

        assertEquals(3.0, filter.getX(), 0.0001);
        assertEquals(0.0, filter.getY(), 0.0001);
    }

    @Test
    public void roomClamp_zeroesVelocityPointingOutOfBounds() {
        KalmanPositionFilter filter = new KalmanPositionFilter();
        filter.resetKnownPosition(1.0, 1.0, 1_000L);
        filter.predictStep(5.0, 0.0, 2_000L, false);

        filter.clampToRoom(3.0, 2.0);

        assertEquals(3.0, filter.getX(), 0.0001);
        assertEquals(0.0, filter.getVx(), 0.0001);
    }
}
