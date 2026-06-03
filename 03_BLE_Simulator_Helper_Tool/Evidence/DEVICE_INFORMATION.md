# Device Information

This file records the confirmed Android devices used with the BLE Simulator helper tool and the OIPASFT scanner and detector application.

## Confirmed Device Roles

Device 1:

Name: Samsung Galaxy A15

Role: BLE Simulator advertiser

Android version: Android 14

Application: BLE Simulator

Device 2:

Name: Xiaomi Redmi Note 10

Role: BLE Simulator advertiser

Android version: Android 13

Application: BLE Simulator

Device 3:

Name: Ulefone Armor 22

Role: Scanner and detector

Android version: Android 13

Application: OIPASFT

## Experimental Configuration Notes

The experimental device arrangement varied according to the requirements of each test scenario. In certain tests, a single smartphone running the BLE Simulator application was used as the simulated BLE beacon. In other tests, smartphones running the BLE Simulator application were incorporated into a mixed beacon environment that also included physical BLE beacons.

The Ulefone Armor 22 was used as the scanner and detector device running OIPASFT, along with the Logcat logging.

## Evidence Handling Note

The following Logcat lines should be retained as concise evidence that the OIPASFT scanner and detector application received the BLE Simulator advertisement and processed it through the raw iBeacon fallback path. These lines are excerpts from the validation session recorded on 2026-05-26.

Related evidence files:

1. `validation/logcat_success_example/OIPASFT_RAW_FALLBACK_success_excerpt_2026-05-26.logcat`

2. `validation/logcat_success_example/FULL_LOG_OIPASFT_RAW_FALLBACK_success_excerpt_2026-05-26.logcat`

3. `validation/logcat_success_example/Screenshot 2026-05-26 171703-OIPASFT-SamA15.png`

Essential Logcat lines:

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

These lines demonstrate that OIPASFT had the required runtime permissions, initiated raw BLE scanning, loaded the configured UUID, parsed the SamA15 iBeacon identity through the raw fallback path, associated the parsed beacon with the known beacon configuration, and used the resulting beacon data in the BLE positioning pipeline.
