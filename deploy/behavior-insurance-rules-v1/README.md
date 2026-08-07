# Behavior Insurance Rules V1

This release separates physical behavior detection from scene classification and insurance interpretation.

- `WORLD_SEA_102_V1R1` remains a named-sea-area gate; it is not a legal high-seas/EEZ classification.
- Loitering is detected outside port operational zones and anchorages at SOG >= 1 kn. Port proximity classifies the event as `PORT_APPROACH` or `OFFSHORE`; it is not an existence gate.
- Alongside retains valid `PORT_APPROACH` and `OFFSHORE` events. `PORT_OPERATIONAL` and `ANCHORAGE` are excluded from formal risk persistence.
- Every supplied job is a dry run: `enableInsert=false`, `parallelism=1`. Do not reuse an old batch number and do not run the 5000-ship job.

Run the preflight/build checks in `validation-checklist.md`, deploy the property delta, then schedule only one requested dry-run entry from `xxl-job-params.json`.
