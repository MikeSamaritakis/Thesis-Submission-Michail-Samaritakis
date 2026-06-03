# LOGGING_RESTART_SANITY

## Basic Info

- App: OIPASFT
- Date: 02-06-2026
- Start time: 23:08
- End time: 23:09
- Operator: Michail Samaritakis
- Scanner phone: Ulefone Armor 22
- Tracking mode: Fusion
- Adaptive tuning: Off
- Session ID: 20260602_230827_736
- Raw log file: TestLogs/20260602_230827_736_user_position_history.json
- Status: pass

## Purpose

Confirm that OIPASFT saves a usable log after a fresh app restart.

- Manual anchor: not required

## Procedure

1. Fully close OIPASFT.
2. Reopen OIPASFT.
3. Select tracking mode: Fusion.
4. Keep adaptive tuning Off.
5. Select the available app trial label closest to sanity/logging, or leave the normal label if no custom label exists.
6. Wait until the dashboard is running and showing live scanner/tracking data.
7. Start recording.
8. Stay still for about 15-20 seconds.
9. Stop recording.
10. Fully close OIPASFT again.
11. Pull logs from the phone.

## Pass Criteria

- A new JSON log file is created after the restart.
- The log has a unique session ID.
- The log contains at least one sample.
- The generated report includes the session.
- No crash or missing-log behavior occurs.

## Fail Criteria

- No new JSON log appears.
- The session ID is missing or duplicated unexpectedly.
- The log has zero samples after a normal recording.
- The report generator cannot parse the log.

## Notes

- Matching pulled log is `20260602_230827_736_user_position_history.json`.
- The report generator parsed the log successfully.
- The session summary includes this session with 8 samples.
- Trial label is `UNLABELED`, which is acceptable for this sanity test because the purpose is logging after restart, not route or static accuracy evaluation.
- Adaptive tuning was off.
- Tracking mode samples are all `FUSION`.
- Samples 0, 2, 3, 4, 5, 6, and 7 are `FUSION`, `OK`, `hasBleFix=true`, and `filteredCount=3`.
- Sample 1 is `DEAD_RECKONING`.
- BLE fix ratio is `0.8750`, so 7 of 8 samples had accepted BLE fixes.
- Logged duration is approximately 33 seconds.

## Problems

- None. The restart logging sanity test passed.



