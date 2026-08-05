# XXL-JOB runbook

Use BEAN / FIRST / single-machine serial / retry 0 / timeout 0 / disabled Cron. Save a one-line JSON parameter, reopen it, verify the ending is intact, run `shipBehaviorProfilePreflightJobHandler`, and only after `status=PASS` manually run `shipBehaviorAuditJobHandler`.

Validate every parameter with `scripts/validate-xxl-job-param`. The default ceiling is 420 UTF-8 bytes and the validator also enforces at least 20% headroom. Never put WKT, SQL, credentials, thresholds, or table names in executor parameters.

Acceptance requires the six output files to pass `sha256sum -c checksums.sha256`, `databaseWriteCount=0`, `formalWriteCount=0`, complete query/replay evidence, and unchanged before/after behavior-event database snapshots. PID and open ports alone are not business acceptance.

Run alongside and loitering separately. Complete SHIPS, LIST 10, and REGION 2–6 hour gates before any 200-ship run. Prepare 5,000-ship parameters only; do not trigger them without separate authorization.
