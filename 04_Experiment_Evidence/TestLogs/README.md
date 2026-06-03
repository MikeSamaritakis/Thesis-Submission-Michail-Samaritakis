# Experiment Test Logs

This folder contains the OIPASFT position-history logs and generated evaluation outputs used as thesis evidence.

## Contents

- `*_user_position_history.json`: raw timestamped session logs copied from the Android device.
- `*_user_position_history.csv`: CSV exports for the same sessions.
- `evaluation_annotations.csv`: ground-truth annotations used for quantitative evaluation.
- `evaluation_annotations_template.csv`: template for adding additional annotations.
- `Reports/thesis_sessions_summary.csv`: session-level metrics.
- `Reports/thesis_samples.csv`: sample-level metrics.
- `Reports/*_path.svg`: generated path plots.
- `Reports/*_error.svg`: generated error plots for sessions with eligible samples.

## Review Notes

- Quantitative claims should be checked against the generated CSV files in `Reports/`.
- Only samples marked as evaluation-eligible are used for reported accuracy.
- Manual anchors and walking traces are retained as context, but they are not treated as continuous ground truth.
