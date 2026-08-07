# Validation checklist

- [ ] Confirm active WORLD_SEA dataset is `WORLD_SEA_102_V1R1` and port/anchorage scene load succeeds.
- [ ] Confirm the batch number has never been claimed; the config hash must differ from the prior port-approach rule.
- [ ] Run a single 20-ship dry run (`enableInsert=false`, `parallelism=1`); do not run the 100/500 jobs yet.
- [ ] Check `LOITERING_PORT_SCENE` counters: below-1kn, port-operational, and anchorage are split/rejected; offshore is not reported as a rejection.
- [ ] Confirm formal Loitering includes both `PORT_APPROACH` and `OFFSHORE` only, with `belowMinSpeedEvidenceCount=0`.
- [ ] Confirm Alongside reports `PORT_OPERATIONAL`/`ANCHORAGE` as excluded and retains valid `PORT_APPROACH`/`OFFSHORE` events.
- [ ] Inspect `nearestPortId`, `nearestPortName`, `distanceToPortBoundaryNm`, `distanceToPortCentroidNm`, and `distanceBucket` audit fields.
- [ ] Use `query.sql` to compare the 20-ship output before deciding whether to schedule the 100-ship run.
