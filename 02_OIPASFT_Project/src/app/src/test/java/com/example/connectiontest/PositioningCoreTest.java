package com.example.connectiontest;

import com.example.connectiontest.BeaconManager.BeaconLocation;
import com.example.connectiontest.BeaconManager.bleBeacon;
import com.example.connectiontest.JsonOps.JsonOps;
import com.example.connectiontest.Positioning.PositioningEngine;
import com.example.connectiontest.TrilaterationUtils.CoordinateUtils;
import com.example.connectiontest.TrilaterationUtils.PositionGate;
import com.example.connectiontest.TrilaterationUtils.TrilaterationUtils;

import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Unit tests for the positioning math that can run without a phone, BLE hardware, or Android UI.
 *
 * These tests use a small fake room and fake beacons. Each test controls the inputs directly,
 * then checks that the app returns a stable, predictable result. That makes them useful as
 * guardrails: if calibration parsing, distance gating, or trilateration changes later, a failing
 * test tells us which behavior changed.
 *
 * How we know the expected values are correct:
 *
 * 1. The fake room is deliberately simple: 4 meters wide, 4 meters high, and represented by
 *    a 5 x 5 grid. A 5-point grid has 4 intervals, so:
 *
 *        4 meters / (5 - 1) = 1 meter per grid step
 *
 *    Therefore row=2, col=2 must map to x=2.0 m, y=2.0 m.
 *
 * 2. The fake beacons use rssi1Meter = -60. The app's distance model is:
 *
 *        distance = 10 ^ ((rssi1Meter - observedRssi) / (10 * pathLossExponent))
 *
 *    The app currently uses pathLossExponent = 2.0. So when observedRssi is also -60:
 *
 *        distance = 10 ^ ((-60 - -60) / 20) = 10 ^ 0 = 1.0 m
 *
 * 3. For trilateration tests, we place fake beacons at known coordinates and feed them RSSI
 *    values that correspond to a known point. The expected result is allowed a small tolerance
 *    because RSSI values are integers and multilateration is approximate.
 *
 * 4. For behavior tests, the expected values come from explicit rules in PositioningEngine:
 *    fewer than 3 beacons means fallback, an out-of-room coordinate means parser rejection,
 *    and a movement larger than maxJumpMeters means JUMP_TOO_LARGE.
 */
public class PositioningCoreTest {

    /**
     * Reset shared static calibration state before every test.
     *
     * The fake room is 4 m x 4 m with a 5 x 5 grid, so each grid step equals 1 meter.
     * Beacon cells are marked with 1. Parser tests must place beacons on those cells.
     */
    @Before
    public void setUp() {
        JsonOps.floorPlan = new int[][]{
                {1, 0, 0, 0, 1},
                {0, 0, 0, 0, 0},
                {0, 0, 1, 0, 0},
                {0, 0, 0, 0, 0},
                {1, 0, 0, 0, 1}
        };
        JsonOps.roomWidthMeters = 4.0;
        JsonOps.roomHeightMeters = 4.0;
        JsonOps.pathLossExponent = 2.0;
        JsonOps.beaconLocation.clear();
    }

    /**
     * Verifies the core grid-to-meter conversion.
     *
     * Expected values:
     * - The room is 4 m wide and has 5 grid columns, which creates 4 equal intervals.
     * - 4 / 4 = 1 m per column.
     * - The same applies to rows because the room height is also 4 m.
     * - Therefore col=3 maps to x=3 m, row=2 maps to y=2 m, and converting back
     *   from meters returns the same row/col numbers.
     */
    @Test
    public void coordinateUtils_mapsGridAndMetersConsistently() {
        assertEquals(1.0, CoordinateUtils.metersPerCol(), 0.0001);
        assertEquals(1.0, CoordinateUtils.metersPerRow(), 0.0001);
        assertEquals(3.0, CoordinateUtils.gridColToXMeters(3), 0.0001);
        assertEquals(2.0, CoordinateUtils.gridRowToYMeters(2), 0.0001);
        assertEquals(3.0, CoordinateUtils.xMetersToGridCol(3.0), 0.0001);
        assertEquals(2.0, CoordinateUtils.yMetersToGridRow(2.0), 0.0001);
    }

    @Test
    public void coordinateUtils_mapsCurrentSmallRoomGrid() {
        JsonOps.floorPlan = new int[][]{
                {1, 0, 0, 0, 0},
                {0, 0, 0, 0, 0},
                {0, 0, 0, 0, 1},
                {0, 0, 0, 0, 0},
                {1, 0, 0, 0, 0}
        };
        JsonOps.roomWidthMeters = 1.85;
        JsonOps.roomHeightMeters = 1.70;

        assertEquals(0.0, CoordinateUtils.gridColToXMeters(0), 0.0001);
        assertEquals(0.0, CoordinateUtils.gridRowToYMeters(0), 0.0001);
        assertEquals(0.0, CoordinateUtils.gridColToXMeters(0), 0.0001);
        assertEquals(1.70, CoordinateUtils.gridRowToYMeters(4), 0.0001);
        assertEquals(1.85, CoordinateUtils.gridColToXMeters(4), 0.0001);
        assertEquals(0.85, CoordinateUtils.gridRowToYMeters(2), 0.0001);
        assertEquals(0.0, CoordinateUtils.gridColToXMeters(0), 0.0001);
        assertEquals(0.85, CoordinateUtils.gridRowToYMeters(2), 0.0001);
    }

    @Test
    public void headingOffset_canAlignObservedHeadingToLeftwardRoomMovement() {
        double rawHeadingDegrees = 124.0;
        double offsetDegrees = 146.0;
        double correctedHeading = Math.toRadians(rawHeadingDegrees + offsetDegrees);

        com.example.connectiontest.SensorManager.SensorManager.StepVector vector =
                com.example.connectiontest.SensorManager.SensorManager.headingToMapDelta(0.62, correctedHeading);

        assertTrue(vector.dxMeters < -0.60);
        assertEquals(0.0, vector.dyMeters, 0.05);
    }

    /**
     * Verifies distance filtering and jump-size filtering.
     *
     * Expected values:
     * - near uses observed RSSI -60 with rssi1Meter -60, so its distance is exactly 1 m.
     * - far uses observed RSSI -90, which is much weaker. With the app's formula it becomes
     *   about 31.6 m, which is greater than maxDist=2.0, so it must be filtered out.
     * - A move from (1.5,1.5) to (1.0,1.0) is about 0.71 m, so it is not too large for max 1 m.
     * - A move from (1.0,1.0) to (4.0,4.0) is about 4.24 m, so it is too large for max 2 m.
     */
    @Test
    public void positionGate_filtersInvalidDistancesAndLargeJumps() {
        bleBeacon near = beacon("near", 0, 0);
        near.updateRssi(-60);
        bleBeacon far = beacon("far", 4, 4);
        far.updateRssi(-90);

        List<bleBeacon> filtered = PositionGate.filterByDistance(Arrays.asList(near, far), 0.5, 2.0);

        assertEquals(1, filtered.size());
        assertEquals("near", filtered.get(0).getJsonId());
        assertFalse(PositionGate.isJumpTooLarge(1.0, 1.0, 1.5, 1.5, 1.0));
        assertTrue(PositionGate.isJumpTooLarge(4.0, 4.0, 1.0, 1.0, 2.0));
    }

    /**
     * Verifies the simplest RSSI-to-distance case.
     *
     * Expected value:
     * - The helper creates a beacon with rssi1Meter = -60.
     * - If the observed RSSI is also -60, the formula exponent is 0.
     * - 10^0 equals 1, so the estimated distance must be 1 meter.
     */
    @Test
    public void beaconDistance_usesCalibratedOneMeterRssi() {
        bleBeacon beacon = beacon("calibrated", 0, 0);

        beacon.updateRssi(-60);

        assertEquals(1.0, beacon.getDistanceFromUser(), 0.0001);
    }

    @Test
    public void beaconDistance_usesConfiguredPathLossExponent() {
        JsonOps.pathLossExponent = 4.0;
        bleBeacon beacon = beacon("calibrated", 0, 0);

        beacon.updateRssi(-80);

        assertEquals(Math.sqrt(10.0), beacon.getDistanceFromUser(), 0.0001);
    }

    @Test
    public void beaconRssiQuality_detectsStableAndNoisyWindows() {
        bleBeacon stable = beacon("stable", 0, 0);
        int[] stableSamples = {-80, -81, -80, -79, -80, -80};
        for (int rssi : stableSamples) {
            stable.updateRssi(rssi);
        }

        bleBeacon noisy = beacon("noisy", 0, 0);
        int[] noisySamples = {-80, -70, -90, -77, -94, -73};
        for (int rssi : noisySamples) {
            noisy.updateRssi(rssi);
        }

        assertTrue(stable.getRssiNoiseDb(6) < noisy.getRssiNoiseDb(6));
        assertTrue(stable.getRssiQualityScore(6) > noisy.getRssiQualityScore(6));
    }

    /**
     * Verifies that trilateration can solve a known point from grid-derived beacon coordinates.
     *
     * Expected values:
     * - The beacons are at (0,0), (4,0), and (0,4) because the grid is 1 m per step.
     * - The RSSI values were chosen to represent a point near (1,1):
     *   - Top-left beacon is closest, so it gets a stronger RSSI (-63).
     *   - Top-right and bottom-left are farther, so they get weaker RSSI (-70).
     * - Because RSSI values are rounded and the solver is approximate, the assertion allows
     *   0.12 m of tolerance around x=1.0 and y=1.0.
     */
    @Test
    public void trilateration_solvesKnownPointFromThreeDistances() {
        bleBeacon topLeft = beacon("topLeft", 0, 0);
        bleBeacon topRight = beacon("topRight", 0, 4);
        bleBeacon bottomLeft = beacon("bottomLeft", 4, 0);
        topLeft.updateRssi(-63);
        topRight.updateRssi(-70);
        bottomLeft.updateRssi(-70);

        double[] position = TrilaterationUtils.trilaterationImplementation(
                Arrays.asList(topLeft, topRight, bottomLeft)
        );

        assertEquals(1.0, position[0], 0.12);
        assertEquals(1.0, position[1], 0.12);
    }

    /**
     * Verifies that exact meter coordinates override row/col geometry.
     *
     * Expected values:
     * - The room is temporarily made 10 m x 10 m so row=0,col=0 would not explain all beacons.
     * - exactBeacon deliberately gives every beacon row=0,col=0 internally.
     * - The only way the solver can find the point near (1,1) is if it uses the exact
     *   meter coordinates: (0,0), (4,0), and (0,4).
     * - This protects the feature added for real tape-measured beacon locations.
     */
    @Test
    public void trilateration_usesExactMeterBeaconCoordinatesWhenPresent() {
        JsonOps.roomWidthMeters = 10.0;
        JsonOps.roomHeightMeters = 10.0;

        bleBeacon left = exactBeacon("left", 0.0, 0.0);
        bleBeacon right = exactBeacon("right", 4.0, 0.0);
        bleBeacon bottom = exactBeacon("bottom", 0.0, 4.0);
        left.updateRssi(-63);
        right.updateRssi(-70);
        bottom.updateRssi(-70);

        double[] position = TrilaterationUtils.trilaterationImplementation(
                Arrays.asList(left, right, bottom)
        );

        assertEquals(1.0, position[0], 0.12);
        assertEquals(1.0, position[1], 0.12);
    }

    /**
     * Verifies that duplicate physical beacon definitions are rejected.
     *
     * Expected value:
     * - A beacon is identified by uuid + major + minor, not by display id.
     * - This JSON creates two different ids, "a" and "b", with the same uuid/major/minor.
     * - The parser must reject that, because later BLE scans could not know which JSON
     *   beacon location to use for that one physical transmitter.
     */
    @Test
    public void jsonBeaconParser_rejectsDuplicateBeaconKeys() throws Exception {
        String json = "{"
                + "\"beacons\":["
                + beaconJson("a", 0, 0, 1, 1)
                + ","
                + beaconJson("b", 0, 4, 1, 1)
                + "]}";

        try {
            JsonOps.JsonToBeaconArray(new JSONObject(json));
        } catch (RuntimeException expected) {
            assertTrue(expected.getCause().getMessage().contains("Duplicate beacon definition"));
            return;
        }
        throw new AssertionError("Expected duplicate beacon key to be rejected");
    }

    /**
     * Verifies that a failed beacon parse does not leave half-loaded calibration data.
     *
     * Expected value:
     * - The first parse loads a known existing beacon.
     * - The second parse starts with one valid beacon and then hits a duplicate physical key.
     * - The parser should throw and keep the previous complete beacon list intact instead of
     *   replacing it with the one beacon parsed before the error.
     */
    @Test
    public void jsonBeaconParser_keepsPreviousBeaconListWhenParseFails() throws Exception {
        String validJson = "{"
                + "\"beacons\":["
                + beaconJson("existing", 0, 0, 1, 1)
                + "]}";
        JsonOps.JsonToBeaconArray(new JSONObject(validJson));

        String invalidJson = "{"
                + "\"beacons\":["
                + beaconJson("first", 0, 0, 2, 2)
                + ","
                + beaconJson("duplicate", 4, 4, 2, 2)
                + "]}";

        try {
            JsonOps.JsonToBeaconArray(new JSONObject(invalidJson));
        } catch (RuntimeException expected) {
            assertEquals(1, JsonOps.beaconLocation.size());
            assertEquals("existing", JsonOps.beaconLocation.get(0).getUniqueId());
            return;
        }
        throw new AssertionError("Expected failed parse to be rejected");
    }

    /**
     * Verifies that exact measured beacon coordinates are parsed from JSON.
     *
     * Expected values:
     * - The JSON gives positionMeters x=3.5 and y=2.6.
     * - The parser should store those exact values without converting them through row/col.
     * - hasExactPositionMeters should be true so downstream code can label the source as exact.
     */
    @Test
    public void jsonBeaconParser_readsExactMeterCoordinates() throws Exception {
        String json = "{"
                + "\"beacons\":["
                + beaconJsonWithMeters("metered", 4, 4, 1, 1, 3.5, 2.6)
                + "]}";

        JsonOps.JsonToBeaconArray(new JSONObject(json));

        BeaconLocation beacon = JsonOps.beaconLocation.get(0);
        assertEquals(3.5, beacon.getXMeters(), 0.0001);
        assertEquals(2.6, beacon.getYMeters(), 0.0001);
        assertTrue(beacon.hasExactPositionMeters());
    }

    /**
     * Verifies backward compatibility with old row/col-only calibration JSON.
     *
     * Expected values:
     * - The JSON omits positionMeters.
     * - The fake room has 1 m per grid step.
     * - row=2,col=2 therefore falls back to x=2.0 m, y=2.0 m.
     * - hasExactPositionMeters should be false so exports can report gridFallback as the source.
     */
    @Test
    public void jsonBeaconParser_fallsBackToGridMetersWhenExactCoordinatesAreMissing() throws Exception {
        String json = "{"
                + "\"beacons\":["
                + beaconJson("gridOnly", 2, 2, 1, 1)
                + "]}";

        JsonOps.JsonToBeaconArray(new JSONObject(json));

        BeaconLocation beacon = JsonOps.beaconLocation.get(0);
        assertEquals(2.0, beacon.getXMeters(), 0.0001);
        assertEquals(2.0, beacon.getYMeters(), 0.0001);
        assertFalse(beacon.hasExactPositionMeters());
    }

    /**
     * Verifies that impossible measured coordinates are rejected early.
     *
     * Expected value:
     * - The fake room width is 4.0 m.
     * - This JSON says the beacon x coordinate is 4.5 m.
     * - That is outside the room, so the parser should throw instead of letting bad geometry
     *   reach the positioning engine.
     */
    @Test
    public void jsonBeaconParser_rejectsExactCoordinatesOutsideRoom() throws Exception {
        String json = "{"
                + "\"beacons\":["
                + beaconJsonWithMeters("outside", 4, 4, 1, 1, 4.5, 2.0)
                + "]}";

        try {
            JsonOps.JsonToBeaconArray(new JSONObject(json));
        } catch (RuntimeException expected) {
            assertTrue(expected.getCause().getMessage().contains("outside room bounds"));
            return;
        }
        throw new AssertionError("Expected out-of-room positionMeters to be rejected");
    }

    /**
     * Verifies the normal happy path through PositioningEngine.
     *
     * Expected values:
     * - Three beacons are available, so trilateration is allowed.
     * - Their distances are inside the configured min/max gate, so filteredCount stays 3.
     * - The solved point is inside the room and there is no previous point yet, so there is
     *   no jump rejection.
     * - The result should therefore be BLE_ACCEPTED with reason OK.
     */
    @Test
    public void positioningEngine_acceptsValidBleSolve() {
        bleBeacon topLeft = beacon("topLeft", 0, 0);
        bleBeacon topRight = beacon("topRight", 0, 4);
        bleBeacon bottomLeft = beacon("bottomLeft", 4, 0);
        topLeft.updateRssi(-63);
        topRight.updateRssi(-70);
        bottomLeft.updateRssi(-70);

        PositioningEngine engine = new PositioningEngine(new PositioningEngine.Config(0.15, 0.5, 2.5));
        PositioningEngine.Result result = engine.process(
                Arrays.asList(topLeft, topRight, bottomLeft),
                Arrays.asList(topLeft, topRight, bottomLeft),
                Collections.emptyList(),
                JsonOps.floorPlan
        );

        assertTrue(result.acceptedBle);
        assertEquals("BLE_ACCEPTED", result.mode);
        assertEquals("OK", result.reason);
        assertEquals(3, result.liveCount);
        assertEquals(3, result.filteredCount);
    }

    /**
     * Verifies the predictable fallback path when trilateration cannot run.
     *
     * Expected values:
     * - Trilateration requires at least 3 usable beacons.
     * - This test provides only 2 live/solve beacons.
     * - The engine should not invent a BLE position. It should report FUSION_FALLBACK and
     *   reason LESS_THAN_3_BEACONS, allowing the app to render the sensor-fusion fallback.
     */
    @Test
    public void positioningEngine_fallsBackPredictablyWhenLessThanThreeBeaconsRemain() {
        bleBeacon first = exactBeacon("first", 0.0, 0.0);
        bleBeacon second = exactBeacon("second", 4.0, 0.0);
        first.updateRssi(-63);
        second.updateRssi(-70);

        PositioningEngine engine = new PositioningEngine(new PositioningEngine.Config(0.15, 0.5, 2.5));
        PositioningEngine.Result result = engine.process(
                Arrays.asList(first, second),
                Arrays.asList(first, second),
                Collections.emptyList(),
                JsonOps.floorPlan
        );

        assertFalse(result.acceptedBle);
        assertTrue(result.needsFusionFallback);
        assertEquals("FUSION_FALLBACK", result.mode);
        assertEquals("LESS_THAN_3_BEACONS", result.reason);
        assertEquals(2, result.liveCount);
        assertEquals(2, result.filteredCount);
    }

    /**
     * Verifies that a noisy solve far beyond the wall is rejected instead of being
     * clamped back onto the room boundary and treated as a good BLE fix.
     */
    @Test
    public void positioningEngine_rejectsSolveBeyondBoundaryClampMargin() {
        List<bleBeacon> outsideFix = exactBeaconsForPoint(2.0, 4.35);
        PositioningEngine engine = new PositioningEngine(new PositioningEngine.Config(0.15, 1.25, 2.5));

        PositioningEngine.Result result = engine.process(
                outsideFix,
                outsideFix,
                Collections.emptyList(),
                JsonOps.floorPlan
        );

        assertFalse(result.acceptedBle);
        assertEquals("BLE_REJECTED", result.mode);
        assertEquals("OUT_OF_BOUNDS", result.reason);
    }

    /**
     * Verifies the missing-beacon reporting used by the UI/logs.
     *
     * Expected values:
     * - knownLocations contains two expected beacons: one live beacon and one missing beacon.
     * - currentLiveBeacons contains only the live beacon.
     * - The engine compares composite keys, sees that "missing" is not live, and returns
     *   missingBeaconIds containing exactly "missing".
     */
    @Test
    public void positioningEngine_reportsMissingKnownBeaconIds() {
        bleBeacon live = exactBeacon("live", 0.0, 0.0);
        live.updateRssi(-63);

        BeaconLocation knownLive = knownLocationFor(live);
        BeaconLocation missing = new BeaconLocation(
                "missing",
                "f7826da6-4fa2-4e98-8024-bc5b71e0893e",
                99,
                100,
                4,
                4,
                4.0,
                4.0,
                true,
                -60
        );

        PositioningEngine engine = new PositioningEngine(new PositioningEngine.Config(0.15, 0.5, 2.5));
        PositioningEngine.Result result = engine.process(
                Collections.singletonList(live),
                Collections.singletonList(live),
                Arrays.asList(knownLive, missing),
                JsonOps.floorPlan
        );

        assertEquals(1, result.missingBeaconIds.size());
        assertEquals("missing", result.missingBeaconIds.get(0));
    }

    /**
     * Verifies jump rejection after the engine has already accepted one position.
     *
     * Expected values:
     * - The first solve is built to land near (1,1), and is accepted.
     * - The second solve is built to land near (3,3).
     * - The straight-line jump is sqrt((3-1)^2 + (3-1)^2), about 2.83 m.
     * - maxJumpMeters is configured as 1.0, so the second update must be rejected with
     *   reason JUMP_TOO_LARGE.
     */
    @Test
    public void positioningEngine_rejectsLargeJumpAfterAcceptedFix() {
        PositioningEngine engine = new PositioningEngine(new PositioningEngine.Config(0.15, 0.5, 1.0));
        List<bleBeacon> firstFix = exactBeaconsForPoint(1.0, 1.0);
        List<bleBeacon> jumpedFix = exactBeaconsForPoint(3.0, 3.0);

        PositioningEngine.Result first = engine.process(firstFix, firstFix, Collections.emptyList(), JsonOps.floorPlan);
        PositioningEngine.Result jumped = engine.process(jumpedFix, jumpedFix, Collections.emptyList(), JsonOps.floorPlan);

        assertTrue(first.acceptedBle);
        assertFalse(jumped.acceptedBle);
        assertEquals("BLE_REJECTED", jumped.mode);
        assertEquals("JUMP_TOO_LARGE", jumped.reason);
    }

    @Test
    public void positioningEngine_manualAnchorResetsBleJumpReference() {
        PositioningEngine engine = new PositioningEngine(new PositioningEngine.Config(0.15, 0.5, 1.0));
        List<bleBeacon> oldFix = exactBeaconsForPoint(3.0, 3.0);
        List<bleBeacon> anchorFix = exactBeaconsForPoint(1.0, 1.0);
        List<bleBeacon> farFromAnchor = exactBeaconsForPoint(3.0, 3.0);

        PositioningEngine.Result first = engine.process(oldFix, oldFix, Collections.emptyList(), JsonOps.floorPlan);
        engine.resetLastAcceptedPosition(1.0, 1.0);
        PositioningEngine.Result acceptedAtAnchor = engine.process(anchorFix, anchorFix, Collections.emptyList(), JsonOps.floorPlan);
        PositioningEngine.Result rejectedFarFromAnchor = engine.process(farFromAnchor, farFromAnchor, Collections.emptyList(), JsonOps.floorPlan);

        assertTrue(first.acceptedBle);
        assertTrue(acceptedAtAnchor.acceptedBle);
        assertFalse(rejectedFarFromAnchor.acceptedBle);
        assertEquals("JUMP_TOO_LARGE", rejectedFarFromAnchor.reason);
    }

    @Test
    public void positioningEngine_rejectsHighResidualBadBeacon() {
        List<bleBeacon> inconsistent = exactBeaconsForPoint(1.0, 1.0);
        inconsistent.get(2).updateRssi(-60);

        PositioningEngine engine = new PositioningEngine(new PositioningEngine.Config(0.15, 1.25, 0.20, 2.5, 3, 0.25));
        PositioningEngine.Result result = engine.process(
                inconsistent,
                inconsistent,
                Collections.emptyList(),
                JsonOps.floorPlan
        );

        assertFalse(result.acceptedBle);
        assertEquals("BLE_REJECTED", result.mode);
        assertEquals("HIGH_RESIDUAL", result.reason);
    }

    @Test
    public void positioningEngine_adaptiveQualityDropsForNoisyRssi() {
        PositioningEngine engine = new PositioningEngine(new PositioningEngine.Config(0.15, 0.5, 2.5));
        List<bleBeacon> stableFix = exactBeaconsForPoint(1.0, 1.0);
        List<bleBeacon> noisyFix = exactBeaconsForPoint(1.0, 1.0);
        int[] noise = {-60, -72, -88, -65, -91, -70};
        for (int rssi : noise) {
            noisyFix.get(0).updateRssi(rssi);
        }

        PositioningEngine.Result stable = engine.process(stableFix, stableFix, Collections.emptyList(), JsonOps.floorPlan, true);
        PositioningEngine.Result noisy = engine.process(noisyFix, noisyFix, Collections.emptyList(), JsonOps.floorPlan, true);

        assertTrue(stable.acceptedBle);
        assertTrue(noisy.bleQualityScore < stable.bleQualityScore);
    }

    @Test
    public void calibrationDocumentationJsonFilesCanBecomeCurrentTest() throws Exception {
        String[] files = {
                "3_big_test_documentation.json",
                "3_small_test_documentation.json",
                "3_smaller_test_documentation.json",
                "4_small_test_documentation.json",
                "5_big_test_documentation.json",
                "5_small_test_documentation.json",
                "current_test.json"
        };

        for (String file : files) {
            JSONObject json = readCalibrationJson(file);
            JSONObject room = json.getJSONObject("room");
            JsonOps.roomWidthMeters = room.getDouble("width");
            JsonOps.roomHeightMeters = room.getDouble("height");
            JsonOps.floorPlan = floorPlanFromJson(json);
            JsonOps.JsonToBeaconArray(json);

            assertTrue(file + " should define beacons", JsonOps.beaconLocation.size() >= 3);
            assertTrue(file + " should include model defaults", json.has("model"));
            assertTrue(file + " should include path-loss default", json.getJSONObject("model").has("pathLossExponent"));
        }
    }

    @Test
    public void fourSmallDocumentationExcludesF2gk() throws Exception {
        JSONObject json = readCalibrationJson("4_small_test_documentation.json");
        JsonOps.roomWidthMeters = json.getJSONObject("room").getDouble("width");
        JsonOps.roomHeightMeters = json.getJSONObject("room").getDouble("height");
        JsonOps.floorPlan = floorPlanFromJson(json);
        JsonOps.JsonToBeaconArray(json);

        assertEquals(4, JsonOps.beaconLocation.size());
        for (BeaconLocation location : JsonOps.beaconLocation) {
            assertFalse("f2gk".equals(location.getUniqueId()));
        }
    }

    @Test
    public void userPositionHistory_countsFusionSamplesWithFreshBleFix() throws Exception {
        UserPositionHistory history = new UserPositionHistory(null);
        UserPositionSample fusionSample = new UserPositionSample(
                0L,
                0L,
                1.0,
                1.0,
                1.1,
                1.1,
                1.0,
                1.0,
                UserPositionSample.TRUTH_NONE,
                false,
                "FUSION",
                "OK",
                3,
                3
        );

        Method method = UserPositionHistory.class.getDeclaredMethod("isBleFixMode", UserPositionSample.class);
        method.setAccessible(true);

        assertTrue((Boolean) method.invoke(history, fusionSample));
    }

    /**
     * Creates a fake beacon using row/col only.
     *
     * rssi1Meter is fixed at -60 so tests can predict distance from RSSI:
     * -60 means about 1 m, -66 means about 2 m, -70 means about 3.16 m.
     */
    private static bleBeacon beacon(String id, int row, int col) {
        return new bleBeacon(
                id,
                "00:00:00:00:00:" + id,
                "f7826da6-4fa2-4e98-8024-bc5b71e0893e",
                row + 1,
                col + 1,
                id,
                -60,
                row,
                col
        );
    }

    /**
     * Creates a fake beacon with exact meter coordinates.
     *
     * The row/col values are deliberately not meaningful here. This helps confirm that
     * exact meter geometry is what the positioning code actually uses.
     */
    private static bleBeacon exactBeacon(String id, double xMeters, double yMeters) {
        return new bleBeacon(
                id,
                "00:00:00:00:00:" + id,
                "f7826da6-4fa2-4e98-8024-bc5b71e0893e",
                id.hashCode(),
                id.hashCode() + 1,
                id,
                -60,
                0,
                0,
                xMeters,
                yMeters,
                true
        );
    }

    /**
     * Builds three fake beacons and assigns RSSI values that correspond to a target point.
     *
     * This lets tests ask: "If the user were at x/y, would the engine solve a nearby point?"
     */
    private static List<bleBeacon> exactBeaconsForPoint(double xMeters, double yMeters) {
        bleBeacon left = exactBeacon("left", 0.0, 0.0);
        bleBeacon right = exactBeacon("right", 4.0, 0.0);
        bleBeacon bottom = exactBeacon("bottom", 0.0, 4.0);
        updateRssiForPoint(left, xMeters, yMeters);
        updateRssiForPoint(right, xMeters, yMeters);
        updateRssiForPoint(bottom, xMeters, yMeters);
        return Arrays.asList(left, right, bottom);
    }

    /**
     * Converts a known distance back into an RSSI value using the same simple path-loss
     * assumption as the app's distance model. Keeping this in one helper makes the tests readable.
     */
    private static void updateRssiForPoint(bleBeacon beacon, double xMeters, double yMeters) {
        double distance = Math.hypot(xMeters - beacon.getBeaconXMeters(), yMeters - beacon.getBeaconYMeters());
        int rssi = (int) Math.round(-60.0 - (20.0 * Math.log10(Math.max(distance, 1.0))));
        beacon.updateRssi(rssi);
    }

    /**
     * Converts a live fake beacon into the JSON-style known location object used by
     * PositioningEngine when it checks missing beacons.
     */
    private static BeaconLocation knownLocationFor(bleBeacon beacon) {
        return new BeaconLocation(
                beacon.getJsonId(),
                beacon.getUUID(),
                beacon.getMajor(),
                beacon.getMinor(),
                beacon.getBeaconRow(),
                beacon.getBeaconCol(),
                beacon.getBeaconXMeters(),
                beacon.getBeaconYMeters(),
                beacon.hasExactPositionMeters(),
                beacon.getRssi1Meter()
        );
    }

    /**
     * Minimal beacon JSON for parser tests that only care about row/col calibration.
     */
    private static String beaconJson(String id, int row, int col, int major, int minor) {
        return "{"
                + "\"id\":\"" + id + "\","
                + "\"uuid\":\"f7826da6-4fa2-4e98-8024-bc5b71e0893e\","
                + "\"major\":" + major + ","
                + "\"minor\":" + minor + ","
                + "\"position\":{\"row\":" + row + ",\"col\":" + col + "},"
                + "\"rssi1Meter\":-60"
                + "}";
    }

    /**
     * Minimal beacon JSON for parser tests that care about exact measured coordinates.
     */
    private static String beaconJsonWithMeters(String id, int row, int col, int major, int minor, double xMeters, double yMeters) {
        return "{"
                + "\"id\":\"" + id + "\","
                + "\"uuid\":\"f7826da6-4fa2-4e98-8024-bc5b71e0893e\","
                + "\"major\":" + major + ","
                + "\"minor\":" + minor + ","
                + "\"position\":{\"row\":" + row + ",\"col\":" + col + "},"
                + "\"positionMeters\":{\"x\":" + xMeters + ",\"y\":" + yMeters + "},"
                + "\"rssi1Meter\":-60"
                + "}";
    }

    private static JSONObject readCalibrationJson(String fileName) throws Exception {
        Path path = Paths.get("app", "src", "main", "assets", "CalibrationTests", fileName);
        if (!Files.exists(path)) {
            path = Paths.get("src", "main", "assets", "CalibrationTests", fileName);
        }
        String json = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
        return new JSONObject(json);
    }

    private static int[][] floorPlanFromJson(JSONObject json) throws Exception {
        org.json.JSONArray rows = json.getJSONArray("floorPlan");
        int[][] floor = new int[rows.length()][rows.getJSONArray(0).length()];
        for (int r = 0; r < rows.length(); r++) {
            org.json.JSONArray row = rows.getJSONArray(r);
            for (int c = 0; c < row.length(); c++) {
                floor[r][c] = row.getInt(c);
            }
        }
        return floor;
    }
}
