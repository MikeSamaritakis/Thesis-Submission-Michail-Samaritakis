# Validation Notes

This file records concise validation notes associated with the BLE Simulator helper tool and the OIPASFT scanner and detector application. The purpose of these notes is to identify the evidence files that support the validation claim and to describe the observed result without introducing unverified assumptions.

## Validation Session

Experiment date: Tuesday, May 26, 2026

Advertising device used: Samsung Galaxy A15 running the BLE Simulator application

Additional advertising device visible in the evidence: Xiaomi Redmi Note 10 running the BLE Simulator application

Scanner and detector device: Ulefone Armor 22

Scanner and detector application: OIPASFT

Physical BLE beacons also used: Not confirmed by this validation note. The Logcat excerpt confirms raw iBeacon detections and BLE positioning input, but this file should not classify any additional beacon as physical unless separate experimental notes or photographs confirm it.

## Configured Beacon Parameters For SamA15

Beacon ID: SamA15

UUID: `f7826da6-4fa2-4e98-8024-bc5b71e0893e`

Major: `20641`

Minor: `50361`

Measured power: `-80`

TX power level: `3`

## Current Evidence Files

1. `validation/logcat_success_example/OIPASFT_RAW_FALLBACK_success_excerpt_2026-05-26.logcat`

   Concise Logcat excerpt containing the essential evidence lines for the SamA15 validation case.

2. `validation/logcat_success_example/FULL_LOG_OIPASFT_RAW_FALLBACK_success_excerpt_2026-05-26.logcat`

   Larger Logcat export from the same validation context.

3. `validation/logcat_success_example/Xiaomi-M2101K6G-Android-13_2026-05-26_171022_logcat_success_example.logcat`

   Raw Logcat export associated with the OIPASFT scanner and detector validation session.

4. `validation/logcat_success_example/Screenshot 2026-05-26 171051-BLE_Simulator.png`

   Screenshot evidence associated with the BLE Simulator configuration.

5. `validation/logcat_success_example/Screenshot 2026-05-26 171703-OIPASFT-SamA15.png`

   Screenshot evidence associated with the SamA15 detection result in OIPASFT.

6. `validation/logcat_success_example/Screenshot 2026-05-26 171614-OIPASFT-Red10.png`

   Screenshot evidence associated with the Red10 detection result in OIPASFT.

## Essential Logcat Evidence

The following lines are the key proof lines for the SamA15 validation case:

```text
2026-05-26 17:09:25.547 26904-26904 MainActivity            com.example.connectiontest           D  All requested permissions granted in callback
2026-05-26 17:09:25.566 26904-26904 MainActivity            com.example.connectiontest           I  RAW_BLE: debug scan started; watch for RAW_BLE iBeacon lines
2026-05-26 17:09:25.657 26904-26904 JsonOps                 com.example.connectiontest           D  UUID from JSON: f7826da6-4fa2-4e98-8024-bc5b71e0893e
2026-05-26 17:09:25.729 26904-26904 BeaconManager           com.example.connectiontest           D  RAW_FALLBACK: Beacon f7826da6-4fa2-4e98-8024-bc5b71e0893e|20641|50361 id=SamA15 is now: 1.4125376 from user
2026-05-26 17:09:25.729 26904-26904 BeaconManagerBEACON_MAP com.example.connectiontest           D  source=RAW_FALLBACK id=SamA15 uuid=f7826da6-4fa2-4e98-8024-bc5b71e0893e major=20641 minor=50361 col(X)=4 row(Y)=4 xMeters=1.85 yMeters=1.7 exactMeters=false
2026-05-26 17:09:25.729 26904-26904 MainActivity            com.example.connectiontest           I  RAW_BLE: iBeacon address=55:9C:E4:36:71:9E rssi=-83 uuid=f7826da6-4fa2-4e98-8024-bc5b71e0893e major=20641 minor=50361 measuredPower=-80 key=f7826da6-4fa2-4e98-8024-bc5b71e0893e|20641|50361 fallbackAccepted=true
2026-05-26 17:09:26.048 26904-26904 MainActivity            com.example.connectiontest           D  BLE_POS: include fresh fallback solve beacon id=SamA15 key=f7826da6-4fa2-4e98-8024-bc5b71e0893e|20641|50361 ageMs=319
2026-05-26 17:09:26.049 26904-26904 PositioningEngine       com.example.connectiontest           D  BLE_DISTANCE: id=SamA15 row=4 col=4 x=1.85 y=1.70 rawRssi=-83 medianRssi=-83 avgRssi=-83.0 rssi1m=-80 n=2.00 samples=1 avgWindow=3 dist=1.41 quality=0.33 gate=keep
2026-05-26 17:09:26.050 26904-26904 TrilaterationUtils      com.example.connectiontest           D  Multilateration result (m): x=1.01 y=0.80 using 3 beacons avgWindow=3
2026-05-26 17:09:26.050 26904-26904 TrilaterationUtils      com.example.connectiontest           D  Multilateration beacons: RAW_iBeacon, RAW_iBeacon, RAW_iBeacon
2026-05-26 17:09:26.051 26904-26904 MainActivity            com.example.connectiontest           I  FUSION_OUTPUT: positioningMode=FUSION source=BLE raw=(1.01,0.80)
```

## Observed Scanner And Detector Result

The Logcat evidence shows that OIPASFT received an iBeacon advertisement matching the BLE Simulator configuration for SamA15. The detected packet contained UUID `f7826da6-4fa2-4e98-8024-bc5b71e0893e`, major value `20641`, minor value `50361`, and measured power `-80`. The packet was processed through the raw iBeacon fallback path, accepted as a known beacon, and then included in the BLE positioning pipeline.

The line containing `source=RAW_FALLBACK id=SamA15` is important because it demonstrates that OIPASFT associated the parsed raw iBeacon data with the known beacon entry for SamA15. The line containing `fallbackAccepted=true` is important because it demonstrates that the advertisement was accepted by the fallback parser. The later `BLE_DISTANCE`, `Multilateration result`, and `FUSION_OUTPUT` lines demonstrate that the accepted beacon data was used by the scanner and detector application after detection.

## Interpretation

This validation evidence supports the claim that the BLE Simulator can provide a configurable iBeacon identity source for thesis experimentation. It also documents the scanner side behaviour of OIPASFT when the Kontakt SDK does not produce an `IBeaconDevice` object for the simulated packet. In this validation session, the raw fallback path allowed the SamA15 advertisement to reach the known beacon matching and positioning stages.

This evidence should not be interpreted as calibrated RF measurement evidence. RSSI and distance related values are observational outputs from the Android devices and are influenced by device orientation, antenna placement, chipset behaviour, environmental interference, and multipath propagation.
