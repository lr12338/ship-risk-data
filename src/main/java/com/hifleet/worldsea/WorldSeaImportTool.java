package com.hifleet.worldsea;

import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.io.WKBReader;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Fixed-scope, resumable WORLD_SEA_MASK release tool. Credentials are never logged. */
public final class WorldSeaImportTool {
    private static final String IMPORT_FILE = "/data/zhengyi/import/world-sea-mask-v1/world-sea-dataanalysis-import.sql";
    private static final String CONFIG_FILE = "/data/zhengyi/application.properties";
    private static final String SCHEMA = "dataanalysis";
    private static final String FORMAL = "ship_behavior_world_sea";
    private static final String META = "ship_behavior_world_sea_meta";
    private static final String AUDIT = "ship_behavior_world_sea_audit";
    private static final String RELEASE = "ship_behavior_world_sea_release";
    private static final String REPAIR_AUDIT = "ship_behavior_world_sea_repair_audit";
    private static final String DATASET_VERSION = "WORLD_SEA_102_V1R1";
    private static final String PARENT_DATASET_VERSION = "WORLD_SEA_102_V1";
    private static final String ORIGINAL_STAGE_FINGERPRINT = "81c810a9db7d319fdfbf9a17805a61493d3beda23b09154958ce2a2b3a31fd1e";
    private static final File APPROVAL_FILE = new File("/data/zhengyi/import/world-sea-mask-v1r1/repair-approval.json");
    private static final File AUTHORIZATION_FILE = new File("/data/zhengyi/import/world-sea-mask-v1r1/release-authorization.json");
    private static final Pattern STAGE = Pattern.compile("ship_behavior_world_sea_stage_\\d{8}_\\d{6}");
    private static final Pattern SAFE_RELEASE_TABLE = Pattern.compile(
            "ship_behavior_world_sea_(?:stage|backup|failed)_\\d{8}_\\d{6}");
    private static final Pattern INSERT = Pattern.compile(
            "(?s).*?VALUES\\s*\\((\\d+),\\s*(NULL|'(?:''|[^'])*'),\\s*"
                    + "(?:NULL|'(?:''|[^'])*'),\\s*(?:NULL|-?\\d+),\\s*"
                    + "GeomFromWKB\\(0x([0-9a-fA-F]+),101\\),\\s*(NULL|'(?:''|[^'])*')\\)\\s*;?");
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    public static void main(String[] args) throws Exception {
        if (args.length != 2 || !STAGE.matcher(args[1]).matches()) usage();
        Command command;
        try { command = Command.valueOf(args[0]); }
        catch (IllegalArgumentException ex) { usage(); return; }
        if (command == Command.APPLY) {
            throw new IllegalStateException("APPLY is deprecated and disabled; use explicit release-state commands");
        }
        if (command == Command.DRY_RUN || command == Command.IMPORT_STAGE || command == Command.VALIDATE_STAGE) {
            throw new IllegalStateException(command + " is disabled for V1R1; use WorldSeaGeometryRepairTool and REGISTER_STAGE");
        }
        try (Connection connection = connection()) {
            if (command == Command.STATE) state(connection, args[1]);
            else if (command == Command.REGISTER_STAGE) registerStage(connection, args[1]);
            else if (command == Command.PUBLISH_STAGE) publishStage(connection, args[1]);
            else if (command == Command.ACTIVATE_DATASET) activateDataset(connection, args[1]);
            else if (command == Command.RESUME_RELEASE) resumeRelease(connection, args[1]);
            else if (command == Command.ROLLBACK_RELEASE) rollbackRelease(connection, args[1]);
        }
    }

    private static void usage() {
        throw new IllegalArgumentException("usage: WorldSeaImportTool "
                + "DRY_RUN|STATE|IMPORT_STAGE|VALIDATE_STAGE|REGISTER_STAGE|PUBLISH_STAGE|"
                + "ACTIVATE_DATASET|RESUME_RELEASE|ROLLBACK_RELEASE stage_table");
    }

    private static Connection connection() throws Exception {
        Properties properties = new Properties();
        try (FileInputStream input = new FileInputStream(CONFIG_FILE)) { properties.load(input); }
        Class.forName(properties.getProperty("spring.datasource.newdataanalysis.driver-class-name", "com.mysql.jdbc.Driver"));
        Connection connection = DriverManager.getConnection(
                required(properties, "spring.datasource.newdataanalysis.url"),
                required(properties, "spring.datasource.newdataanalysis.username"),
                required(properties, "spring.datasource.newdataanalysis.password"));
        if (!SCHEMA.equals(connection.getCatalog())) connection.setCatalog(SCHEMA);
        return connection;
    }

    private static void state(Connection connection, String stage) throws Exception {
        boolean releaseTableExists = tableExists(connection, RELEASE);
        ReleaseRow release = releaseTableExists ? release(connection, stage) : null;
        Validation stageValue = tableExists(connection, stage) ? inspectTable(connection, stage, false) : null;
        Validation formalValue = tableExists(connection, FORMAL) ? inspectTable(connection, FORMAL, false) : null;
        Active active = active(connection);
        int activeCount = activeCount(connection);
        RepairAuditState repairAudit = stageValue != null && tableExists(connection, REPAIR_AUDIT)
                ? repairAuditState(connection, stage, stageValue.fingerprint) : null;
        boolean stageExists = stageValue != null;
        boolean formalExists = formalValue != null;
        boolean backupExists = release != null && release.backupTable != null
                && tableExists(connection, release.backupTable);
        boolean formalMatches = release != null && formalValue != null
                && release.fingerprint.equals(formalValue.fingerprint);
        boolean formalMismatch = release != null && formalValue != null && !formalMatches;
        boolean activeMatches = release != null && active != null
                && release.fingerprint.equals(active.fingerprint);
        WorldSeaReleaseStateMachine.RecoveryDecision decision = release != null
                ? WorldSeaReleaseStateMachine.recover(new WorldSeaReleaseStateMachine.ReleaseReality(
                release == null ? null : release.status, stageExists, backupExists, formalExists,
                formalMatches, formalMismatch, activeMatches))
                : null;
        System.out.println("WORLD_SEA_IMPORT STATE"
                + " releaseId=" + value(release == null ? null : release.releaseId)
                + " releaseStatus=" + value(release == null ? null : release.status)
                + " lastCompletedStep=" + value(release == null ? null : release.lastCompletedStep)
                + " stageExists=" + stageExists
                + " stageRows=" + (stageValue == null ? 0 : stageValue.rows.size())
                + " stageFingerprint=" + value(stageValue == null ? null : stageValue.fingerprint)
                + " formalExists=" + formalExists
                + " formalRows=" + (formalValue == null ? 0 : formalValue.rows.size())
                + " formalFingerprint=" + value(formalValue == null ? null : formalValue.fingerprint)
                + " backupExists=" + backupExists
                + " publishMode=" + value(release == null ? null : release.publishMode)
                + " activeDatasetVersion=" + value(active == null ? null : active.datasetVersion)
                + " activeFingerprint=" + value(active == null ? null : active.fingerprint)
                + " activeCount=" + activeCount
                + " repairReleaseId=" + value(repairAudit == null ? null : repairAudit.repairReleaseId)
                + " repairReviewStatus=" + value(repairAudit == null ? null : repairAudit.reviewStatus)
                + " authorizationMode=" + value(release == null ? null : release.authorizationMode)
                + " authorizationRef=" + value(release == null ? null : release.authorizationRef)
                + " authorizedAt=" + value(release == null ? null : release.authorizedAt)
                + " repairAuditRows=" + (repairAudit == null ? 0 : repairAudit.rows)
                + " metaExists=" + tableExists(connection, META)
                + " auditExists=" + tableExists(connection, AUDIT)
                + " repairAuditExists=" + tableExists(connection, REPAIR_AUDIT)
                + " releaseTableExists=" + releaseTableExists
                + " recoveryNeeded=" + (decision != null && decision.isRecoveryNeeded())
                + " nextAllowedAction=" + (release == null ? nextBeforeRegistration(repairAudit)
                : decision.getNextAllowedAction())
                + " databaseWrites=0");
    }

    private static void importStage(Connection connection, String stage, ImportPayload payload) throws Exception {
        requireMigration(connection);
        ReleaseRow registered = release(connection, stage);
        if (registered != null) {
            WorldSeaReleaseStateMachine.require(registered.status,
                    WorldSeaReleaseStateMachine.Status.NEW,
                    WorldSeaReleaseStateMachine.Status.STAGE_IMPORTED,
                    WorldSeaReleaseStateMachine.Status.FAILED);
        }
        long packet = maxAllowedPacket(connection);
        if (maxBytes(payload.statements) >= packet) {
            throw new IllegalStateException("controlled INSERT exceeds max_allowed_packet; no global setting changed");
        }
        if (!tableExists(connection, stage)) {
            try (Statement statement = connection.createStatement()) { statement.execute(payload.statements.get(0)); }
        }
        Validation existing = inspectTable(connection, stage, false);
        Map<Integer, WorldSeaMaskLoader.Row> existingRows = rowsById(existing.rows);
        for (WorldSeaMaskLoader.Row row : existing.rows) {
            WorldSeaMaskLoader.Row expected = payload.rows.get(row.getId());
            if (expected == null || !same(expected, row)) {
                throw new IllegalStateException("existing stage differs from controlled import at ID=" + row.getId());
            }
        }
        int inserted = 0;
        try (Statement statement = connection.createStatement()) {
            for (Map.Entry<Integer, String> entry : payload.insertById.entrySet()) {
                if (!existingRows.containsKey(entry.getKey())) {
                    statement.execute(entry.getValue()); inserted++;
                }
            }
        }
        Validation imported = inspectTable(connection, stage, false);
        if (imported.rows.size() != 102 || !payload.validation.fingerprint.equals(imported.fingerprint)) {
            throw new IllegalStateException("stage import is incomplete or fingerprint differs from controlled source");
        }
        String releaseId = releaseId(imported.fingerprint, stage);
        ReleaseRow current = release(connection, stage);
        if (current != null && !current.releaseId.equals(releaseId)) {
            throw new IllegalStateException("stage is already registered to a different release");
        }
        if (current != null && !current.sourceSha.equals(payload.sourceSha)) {
            throw new IllegalStateException("registered source SHA differs from controlled import");
        }
        upsertImportedRelease(connection, releaseId, stage, payload.sourceSha, imported);
        ReleaseRow stored = requiredRelease(connection, stage);
        System.out.println("WORLD_SEA_IMPORT IMPORT_STAGE PASS releaseId=" + releaseId
                + " stage=" + stage + " inserted=" + inserted + " rows=" + imported.rows.size()
                + " geometryFingerprint=" + imported.fingerprint + " status=" + stored.status);
    }

    private static void validateStage(Connection connection, String stage) throws Exception {
        requireMigration(connection);
        ReleaseRow release = requiredRelease(connection, stage);
        WorldSeaReleaseStateMachine.require(release.status,
                WorldSeaReleaseStateMachine.Status.STAGE_IMPORTED,
                WorldSeaReleaseStateMachine.Status.STAGE_VALIDATED,
                WorldSeaReleaseStateMachine.Status.FAILED);
        try {
            Validation value = inspectTable(connection, stage, true);
            if (!release.fingerprint.equals(value.fingerprint)) {
                throw new IllegalStateException("validated stage fingerprint differs from registered release");
            }
            updateStatus(connection, release.releaseId, WorldSeaReleaseStateMachine.Status.STAGE_VALIDATED,
                    "VALIDATE_STAGE", null);
            System.out.println("WORLD_SEA_IMPORT VALIDATE_STAGE PASS releaseId=" + release.releaseId
                    + " rows=" + value.rows.size() + " polygons=" + value.polygons
                    + " multiPolygons=" + value.multiPolygons + " vertices=" + value.vertices
                    + " geometryFingerprint=" + value.fingerprint + " status=STAGE_VALIDATED");
        } catch (Exception ex) {
            updateStatus(connection, release.releaseId, WorldSeaReleaseStateMachine.Status.FAILED,
                    "IMPORT_STAGE", abbreviate(ex.getMessage()));
            throw ex;
        }
    }

    private static void registerStage(Connection connection, String stage) throws Exception {
        requireMigration(connection);
        Validation value = inspectTable(connection, stage, true);
        RepairAuditIdentity repair = repairAudit(connection, stage, value.fingerprint);
        WorldSeaReleaseAuthorization.Authorization authorization = authorization(value.fingerprint);
        ReleaseRow release = release(connection, stage);
        if (release != null) {
            WorldSeaReleaseStateMachine.require(release.status, WorldSeaReleaseStateMachine.Status.STAGED);
            if (!release.fingerprint.equals(value.fingerprint)) throw new IllegalStateException("stage fingerprint changed");
            requireAuthorization(release);
            System.out.println("WORLD_SEA_IMPORT REGISTER_STAGE PASS releaseId=" + release.releaseId
                    + " status=STAGED reviewStatus=" + authorization.auditStatus() + " idempotent=true");
            return;
        }
        String releaseId = releaseId(value.fingerprint, stage);
        connection.setAutoCommit(false);
        try {
            authorizeRepairAudit(connection, repair.repairReleaseId, authorization);
            insertStagedRelease(connection, releaseId, stage, value, repair, authorization);
            connection.commit();
        } catch (Exception ex) {
            connection.rollback(); throw ex;
        } finally { connection.setAutoCommit(true); }
        System.out.println("WORLD_SEA_IMPORT REGISTER_STAGE PASS releaseId=" + releaseId
                + " status=STAGED reviewStatus=" + authorization.auditStatus()
                + " repairedGeometryFingerprint=" + value.fingerprint);
    }

    private static void publishStage(Connection connection, String stage) throws Exception {
        requireMigration(connection);
        ReleaseRow release = requiredRelease(connection, stage);
        WorldSeaReleaseStateMachine.require(release.status,
                WorldSeaReleaseStateMachine.Status.STAGED, WorldSeaReleaseStateMachine.Status.PUBLISHING);
        requireAuthorization(release);
        if (countOtherPublishing(connection, release.releaseId) > 0) {
            throw new IllegalStateException("another WORLD_SEA release is PUBLISHING");
        }
        Validation stageValue = inspectTable(connection, stage, true);
        if (!release.fingerprint.equals(stageValue.fingerprint)) throw new IllegalStateException("stage fingerprint changed");
        if (tableExists(connection, FORMAL)) {
            Validation formalValue = inspectTable(connection, FORMAL, true);
            if (formalValue.fingerprint.equals(release.fingerprint)) {
                markNoopIdentical(connection, release.releaseId, active(connection));
                System.out.println("WORLD_SEA_IMPORT PUBLISH_STAGE PASS releaseId=" + release.releaseId
                        + " status=NOOP_IDENTICAL formalFingerprint=" + formalValue.fingerprint);
                return;
            }
        }
        String backup = release.backupTable;
        if (backup == null && tableExists(connection, FORMAL)) backup = FORMAL + "_backup_" + nowStamp();
        if (backup != null && tableExists(connection, backup)) {
            throw new IllegalStateException("planned backup table already exists: " + backup);
        }
        Active previous = active(connection);
        markPublishing(connection, release.releaseId, backup, previous);
        if (tableExists(connection, FORMAL)) {
            rename(connection, "RENAME TABLE `" + SCHEMA + "`.`" + FORMAL + "` TO `" + SCHEMA + "`.`" + backup
                    + "`, `" + SCHEMA + "`.`" + stage + "` TO `" + SCHEMA + "`.`" + FORMAL + "`");
        } else {
            rename(connection, "RENAME TABLE `" + SCHEMA + "`.`" + stage + "` TO `" + SCHEMA + "`.`" + FORMAL + "`");
        }
        updatePublishedInactive(connection, release.releaseId);
        System.out.println("WORLD_SEA_IMPORT PUBLISH_STAGE PASS releaseId=" + release.releaseId
                + " status=PUBLISHED_INACTIVE backup=" + value(backup));
    }

    private static void activateDataset(Connection connection, String stage) throws Exception {
        requireMigration(connection);
        ReleaseRow release = requiredRelease(connection, stage);
        WorldSeaReleaseStateMachine.require(release.status,
                WorldSeaReleaseStateMachine.Status.PUBLISHED_INACTIVE,
                WorldSeaReleaseStateMachine.Status.NOOP_IDENTICAL,
                WorldSeaReleaseStateMachine.Status.ACTIVE);
        requireAuthorization(release);
        Validation formalValue = inspectTable(connection, FORMAL, true);
        if (formalValue.rows.size() != 102 || !release.fingerprint.equals(formalValue.fingerprint)) {
            throw new IllegalStateException("formal table does not match inactive release");
        }
        int activeCount = activeCount(connection);
        Active currentActive = activeCount == 1 ? active(connection) : null;
        if (activationAlreadyComplete(release.status, activeCount,
                currentActive != null && DATASET_VERSION.equals(currentActive.datasetVersion)
                        && release.fingerprint.equals(currentActive.fingerprint))) {
            System.out.println("WORLD_SEA_IMPORT ACTIVATE_DATASET PASS releaseId=" + release.releaseId
                    + " datasetVersion=" + DATASET_VERSION + " status=ACTIVE idempotent=true");
            return;
        }
        connection.setAutoCommit(false);
        try {
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("UPDATE `" + SCHEMA + "`.`" + META + "` SET status='RETIRED' WHERE status='ACTIVE'");
                statement.executeUpdate("UPDATE `" + SCHEMA + "`.`" + RELEASE + "` SET status='PUBLISHED_INACTIVE' "
                        + "WHERE status='ACTIVE' AND release_id<>'" + release.releaseId + "'");
            }
            upsertMeta(connection, release, formalValue);
            try (PreparedStatement statement = connection.prepareStatement("UPDATE `" + SCHEMA + "`.`" + RELEASE
                    + "` SET status='ACTIVE',last_completed_step='ACTIVATE_DATASET',activated_at=NOW(),"
                    + "updated_at=NOW(),error_message=NULL WHERE release_id=?")) {
                statement.setString(1, release.releaseId); statement.executeUpdate();
            }
            try (Statement statement = connection.createStatement(); ResultSet rs = statement.executeQuery(
                    "SELECT COUNT(*) FROM `" + SCHEMA + "`.`" + META + "` WHERE status='ACTIVE'")) {
                rs.next();
                if (rs.getInt(1) != 1) throw new IllegalStateException("ACTIVE metadata uniqueness check failed");
            }
            connection.commit();
        } catch (Exception ex) {
            connection.rollback(); throw ex;
        } finally { connection.setAutoCommit(true); }
        System.out.println("WORLD_SEA_IMPORT ACTIVATE_DATASET PASS releaseId=" + release.releaseId
                + " datasetVersion=" + DATASET_VERSION + " status=ACTIVE");
    }

    static boolean activationAlreadyComplete(WorldSeaReleaseStateMachine.Status status,
                                             int activeCount, boolean activeMatchesRelease) {
        if (activeCount > 1) throw new IllegalStateException("multiple ACTIVE WORLD_SEA datasets");
        if (status != WorldSeaReleaseStateMachine.Status.ACTIVE) return false;
        if (activeCount != 1 || !activeMatchesRelease) {
            throw new IllegalStateException("ACTIVE release metadata differs from formal table");
        }
        return true;
    }

    private static void resumeRelease(Connection connection, String stage) throws Exception {
        requireMigration(connection);
        ReleaseRow release = requiredRelease(connection, stage);
        Validation formal = tableExists(connection, FORMAL) ? inspectTable(connection, FORMAL, false) : null;
        Active active = active(connection);
        boolean backupExists = release.backupTable != null && tableExists(connection, release.backupTable);
        WorldSeaReleaseStateMachine.RecoveryDecision decision = WorldSeaReleaseStateMachine.recover(
                new WorldSeaReleaseStateMachine.ReleaseReality(release.status, tableExists(connection, stage),
                        backupExists, formal != null, formal != null && release.fingerprint.equals(formal.fingerprint),
                        formal != null && !release.fingerprint.equals(formal.fingerprint),
                        active != null && release.fingerprint.equals(active.fingerprint)));
        String action = decision.getNextAllowedAction();
        if ("MARK_PUBLISHED_INACTIVE".equals(action)) updatePublishedInactive(connection, release.releaseId);
        else if ("VALIDATE_STAGE".equals(action)) validateStage(connection, stage);
        else if ("REGISTER_STAGE".equals(action)) registerStage(connection, stage);
        else if ("PUBLISH_STAGE".equals(action)) publishStage(connection, stage);
        else if ("ACTIVATE_DATASET".equals(action)) activateDataset(connection, stage);
        else if ("ROLLBACK_RELEASE".equals(action) || "ROLLBACK_REQUIRED".equals(action)) {
            updateStatus(connection, release.releaseId, WorldSeaReleaseStateMachine.Status.ROLLBACK_REQUIRED,
                    release.lastCompletedStep, "database reality requires rollback");
        } else if (!"NONE".equals(action)) {
            throw new IllegalStateException("manual inspection required, nextAllowedAction=" + action);
        }
        System.out.println("WORLD_SEA_IMPORT RESUME_RELEASE PASS releaseId=" + release.releaseId
                + " completedAction=" + action);
    }

    private static void rollbackRelease(Connection connection, String stage) throws Exception {
        requireMigration(connection);
        ReleaseRow release = requiredRelease(connection, stage);
        WorldSeaReleaseStateMachine.require(release.status,
                WorldSeaReleaseStateMachine.Status.PUBLISHED_INACTIVE,
                WorldSeaReleaseStateMachine.Status.ACTIVE,
                WorldSeaReleaseStateMachine.Status.ROLLBACK_REQUIRED);
        if ("NOOP_IDENTICAL".equals(release.publishMode)) {
            completeRollbackMetadata(connection, release);
            System.out.println("WORLD_SEA_IMPORT ROLLBACK_RELEASE PASS releaseId=" + release.releaseId
                    + " status=ROLLED_BACK publishMode=NOOP_IDENTICAL tableRename=false");
            return;
        }
        String failed = release.failedTable == null ? FORMAL + "_failed_" + nowStamp() : release.failedTable;
        if (!SAFE_RELEASE_TABLE.matcher(failed).matches()) throw new IllegalStateException("unsafe failed table name");
        if (tableExists(connection, failed)) {
            boolean restored = release.backupTable == null
                    ? !tableExists(connection, FORMAL)
                    : tableExists(connection, FORMAL) && !tableExists(connection, release.backupTable);
            if (!restored) throw new IllegalStateException("failed table exists but rollback reality is inconsistent");
            completeRollbackMetadata(connection, release);
            System.out.println("WORLD_SEA_IMPORT ROLLBACK_RELEASE PASS releaseId=" + release.releaseId
                    + " failedTable=" + failed + " status=ROLLED_BACK resumed=true");
            return;
        }
        if (!tableExists(connection, FORMAL)) throw new IllegalStateException("formal table missing; manual recovery required");
        setFailedTable(connection, release.releaseId, failed);
        if (release.backupTable != null && tableExists(connection, release.backupTable)) {
            rename(connection, "RENAME TABLE `" + SCHEMA + "`.`" + FORMAL + "` TO `" + SCHEMA + "`.`" + failed
                    + "`, `" + SCHEMA + "`.`" + release.backupTable + "` TO `" + SCHEMA + "`.`" + FORMAL + "`");
        } else {
            rename(connection, "RENAME TABLE `" + SCHEMA + "`.`" + FORMAL + "` TO `" + SCHEMA + "`.`" + failed + "`");
        }
        completeRollbackMetadata(connection, release);
        System.out.println("WORLD_SEA_IMPORT ROLLBACK_RELEASE PASS releaseId=" + release.releaseId
                + " failedTable=" + failed + " status=ROLLED_BACK");
    }

    private static void completeRollbackMetadata(Connection connection, ReleaseRow release) throws Exception {
        connection.setAutoCommit(false);
        try {
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("UPDATE `" + SCHEMA + "`.`" + META + "` SET status='RETIRED' WHERE status='ACTIVE'");
            }
            if (release.previousActiveDataset != null) {
                try (PreparedStatement statement = connection.prepareStatement("UPDATE `" + SCHEMA + "`.`" + META
                        + "` SET status='ACTIVE' WHERE dataset_version=? AND geometry_fingerprint=?")) {
                    statement.setString(1, release.previousActiveDataset);
                    statement.setString(2, release.previousActiveFingerprint);
                    if (statement.executeUpdate() != 1) throw new IllegalStateException("previous ACTIVE metadata is unavailable");
                }
                try (PreparedStatement statement = connection.prepareStatement("UPDATE `" + SCHEMA + "`.`" + RELEASE
                        + "` SET status='ACTIVE',updated_at=NOW() WHERE dataset_version=? AND geometry_fingerprint=?")) {
                    statement.setString(1, release.previousActiveDataset);
                    statement.setString(2, release.previousActiveFingerprint);
                    statement.executeUpdate();
                }
            }
            try (PreparedStatement statement = connection.prepareStatement("UPDATE `" + SCHEMA + "`.`" + RELEASE
                    + "` SET status='ROLLED_BACK',last_completed_step='ROLLBACK_RELEASE',rolled_back_at=NOW(),"
                    + "updated_at=NOW() WHERE release_id=?")) {
                statement.setString(1, release.releaseId); statement.executeUpdate();
            }
            connection.commit();
        } catch (Exception ex) { connection.rollback(); throw ex; }
        finally { connection.setAutoCommit(true); }
    }

    private static ImportPayload payload(String stage) throws Exception {
        File file = new File(IMPORT_FILE);
        if (!file.isFile()) throw new IllegalStateException("fixed import file is missing");
        List<String> statements = statements(file);
        validateStatements(statements, stage);
        Map<Integer, WorldSeaMaskLoader.Row> rows = new LinkedHashMap<Integer, WorldSeaMaskLoader.Row>();
        Map<Integer, String> insertById = new LinkedHashMap<Integer, String>();
        for (int i = 1; i < statements.size(); i++) {
            Matcher matcher = INSERT.matcher(statements.get(i));
            if (!matcher.matches()) throw new IllegalStateException("controlled INSERT format is unsupported at statement=" + i);
            int id = Integer.parseInt(matcher.group(1));
            WorldSeaMaskLoader.Row row = new WorldSeaMaskLoader.Row(id, sqlString(matcher.group(2)),
                    sqlString(matcher.group(4)), hex(matcher.group(3)));
            if (rows.put(id, row) != null) throw new IllegalStateException("duplicate ID in controlled import=" + id);
            insertById.put(id, statements.get(i));
        }
        Validation validation = validation(new ArrayList<WorldSeaMaskLoader.Row>(rows.values()), false);
        if (rows.size() != 102) throw new IllegalStateException("controlled import feature count must be 102");
        return new ImportPayload(statements, rows, insertById, sourceSha(file), validation);
    }

    private static Validation inspectTable(Connection connection, String table, boolean strict) throws Exception {
        if (!FORMAL.equals(table) && !STAGE.matcher(table).matches() && !SAFE_RELEASE_TABLE.matcher(table).matches()) {
            throw new IllegalStateException("unsafe WORLD_SEA table name");
        }
        List<WorldSeaMaskLoader.Row> rows = new ArrayList<WorldSeaMaskLoader.Row>();
        String sql = "SELECT ID,NAME,CNNAME,AsBinary(the_geom) geom_wkb FROM `" + SCHEMA + "`.`" + table + "` ORDER BY ID";
        try (Statement statement = connection.createStatement(); ResultSet rs = statement.executeQuery(sql)) {
            while (rs.next()) rows.add(new WorldSeaMaskLoader.Row(rs.getInt("ID"), rs.getString("NAME"),
                    rs.getString("CNNAME"), rs.getBytes("geom_wkb")));
        }
        return validation(rows, strict);
    }

    private static Validation validation(List<WorldSeaMaskLoader.Row> rows, boolean strict) throws Exception {
        List<String> invalid = new ArrayList<String>();
        Map<Integer, Boolean> ids = new HashMap<Integer, Boolean>();
        int polygons = 0, multiPolygons = 0; long vertices = 0;
        WKBReader reader = new WKBReader();
        for (WorldSeaMaskLoader.Row row : rows) {
            if (ids.put(row.getId(), Boolean.TRUE) != null) throw new IllegalStateException("duplicate ID=" + row.getId());
            Geometry geometry = reader.read(row.getWkb());
            org.locationtech.jts.operation.valid.IsValidOp validity =
                    new org.locationtech.jts.operation.valid.IsValidOp(geometry);
            if (geometry.isEmpty() || !validity.isValid()) invalid.add("ID=" + row.getId()
                    + " reason=" + validity.getValidationError() + " wkbSha256=" + sha256(row.getWkb())
                    + " vertices=" + geometry.getNumPoints());
            if ("Polygon".equals(geometry.getGeometryType())) polygons++;
            else if ("MultiPolygon".equals(geometry.getGeometryType())) multiPolygons++;
            else throw new IllegalStateException("unsupported geometry type=" + geometry.getGeometryType());
            for (org.locationtech.jts.geom.Coordinate coordinate : geometry.getCoordinates()) {
                if (!Double.isFinite(coordinate.x) || !Double.isFinite(coordinate.y)
                        || coordinate.x < -180 || coordinate.x > 180 || coordinate.y < -90 || coordinate.y > 90) {
                    throw new IllegalStateException("coordinate out of range ID=" + row.getId());
                }
            }
            vertices += geometry.getNumPoints();
        }
        String fingerprint = WorldSeaMaskLoader.fingerprint(rows);
        if (strict && (!invalid.isEmpty() || rows.size() != 102)) {
            throw new IllegalStateException("stage validation failed rows=" + rows.size()
                    + " geometryFingerprint=" + fingerprint + " invalidGeometries=" + invalid);
        }
        return new Validation(rows, polygons, multiPolygons, vertices, fingerprint, invalid);
    }

    private static void upsertImportedRelease(Connection connection, String releaseId, String stage,
                                              String sourceSha, Validation value) throws Exception {
        String sql = "INSERT INTO `" + SCHEMA + "`.`" + RELEASE + "` "
                + "(release_id,dataset_version,source_file_sha256,geometry_fingerprint,stage_table_name,formal_table_name,"
                + "feature_count,polygon_count,multipolygon_count,vertex_count,status,last_completed_step,created_at,updated_at) "
                + "VALUES (?,?,?,?,?,?,?,?,?,?,'STAGE_IMPORTED','IMPORT_STAGE',NOW(),NOW()) "
                + "ON DUPLICATE KEY UPDATE updated_at=NOW(),error_message=NULL";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, releaseId); statement.setString(2, DATASET_VERSION);
            statement.setString(3, sourceSha); statement.setString(4, value.fingerprint);
            statement.setString(5, stage); statement.setString(6, FORMAL);
            statement.setInt(7, value.rows.size()); statement.setInt(8, value.polygons);
            statement.setInt(9, value.multiPolygons); statement.setLong(10, value.vertices);
            statement.executeUpdate();
        }
    }

    private static RepairAuditIdentity repairAudit(Connection connection, String stage, String fingerprint) throws Exception {
        String sql = "SELECT repair_release_id,repair_tool_git_sha,COUNT(*),"
                + "COUNT(DISTINCT feature_id),MIN(original_stage_fingerprint) FROM `" + SCHEMA + "`.`" + REPAIR_AUDIT + "` "
                + "WHERE dataset_version_after=? AND repaired_geometry_fingerprint=? AND repaired_stage_table=? "
                + "GROUP BY repair_release_id,repair_tool_git_sha";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, DATASET_VERSION); statement.setString(2, fingerprint);
            statement.setString(3, stage);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) throw new IllegalStateException("matching repair audit is missing");
                RepairAuditIdentity value = new RepairAuditIdentity(rs.getString(1), rs.getString(2));
                if (rs.getInt(3) != 7 || rs.getInt(4) != 7
                        || !ORIGINAL_STAGE_FINGERPRINT.equals(rs.getString(5))) {
                    throw new IllegalStateException("repair audit identity/count mismatch");
                }
                if (rs.next()) throw new IllegalStateException("multiple repair audit releases match fingerprint");
                requireRepairIds(connection, value.repairReleaseId);
                return value;
            }
        }
    }

    private static RepairAuditState repairAuditState(Connection connection, String stage,
                                                     String fingerprint) throws Exception {
        String sql = "SELECT repair_release_id,MIN(review_status),MAX(review_status),COUNT(*),"
                + "COUNT(DISTINCT feature_id) FROM `" + SCHEMA + "`.`" + REPAIR_AUDIT + "` "
                + "WHERE repaired_stage_table=? AND repaired_geometry_fingerprint=? GROUP BY repair_release_id";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, stage); statement.setString(2, fingerprint);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) return null;
                String minimumStatus = rs.getString(2), maximumStatus = rs.getString(3);
                RepairAuditState value = new RepairAuditState(rs.getString(1), minimumStatus, rs.getInt(4));
                if (!equal(minimumStatus, maximumStatus) || value.rows != 7 || rs.getInt(5) != 7) {
                    throw new IllegalStateException("repair audit state/count is inconsistent");
                }
                if (rs.next()) throw new IllegalStateException("multiple repair audits match repaired stage");
                return value;
            }
        }
    }

    private static String nextBeforeRegistration(RepairAuditState repairAudit) {
        if (repairAudit == null) return "CREATE_REPAIRED_STAGE";
        return ("APPROVED".equals(repairAudit.reviewStatus)
                || "OPERATOR_AUTHORIZED".equals(repairAudit.reviewStatus))
                ? "REGISTER_STAGE" : "AWAIT_APPROVAL_THEN_REGISTER_STAGE";
    }

    private static WorldSeaReleaseAuthorization.Authorization authorization(String fingerprint) throws Exception {
        return WorldSeaReleaseAuthorization.read(APPROVAL_FILE, AUTHORIZATION_FILE, fingerprint);
    }

    private static void requireAuthorization(ReleaseRow release) throws Exception {
        WorldSeaReleaseAuthorization.Authorization value = authorization(release.fingerprint);
        if (!value.auditStatus().equals(release.reviewStatus)
                || !value.authorizationMode.equals(release.authorizationMode)
                || !value.authorizationRef.equals(release.authorizationRef)
                || release.authorizedAt == null
                || !java.time.OffsetDateTime.parse(value.authorizedAt).toLocalDateTime()
                .equals(release.authorizedAt.toLocalDateTime())) {
            throw new IllegalStateException("current authorization differs from registered release");
        }
    }

    private static void requireRepairIds(Connection connection, String repairReleaseId) throws Exception {
        Set<Integer> ids = new java.util.LinkedHashSet<Integer>();
        try (PreparedStatement statement = connection.prepareStatement("SELECT feature_id FROM `" + SCHEMA + "`.`"
                + REPAIR_AUDIT + "` WHERE repair_release_id=? ORDER BY feature_id")) {
            statement.setString(1, repairReleaseId);
            try (ResultSet rs = statement.executeQuery()) { while (rs.next()) ids.add(rs.getInt(1)); }
        }
        if (!ids.equals(WorldSeaGeometryRepair.REPAIR_IDS)) {
            throw new IllegalStateException("repair audit IDs differ from fixed allowlist");
        }
    }

    private static void authorizeRepairAudit(Connection connection, String repairReleaseId,
                                             WorldSeaReleaseAuthorization.Authorization authorization) throws Exception {
        java.sql.Timestamp authorizedAt = java.sql.Timestamp.from(
                java.time.OffsetDateTime.parse(authorization.authorizedAt).toInstant());
        try (PreparedStatement statement = connection.prepareStatement("UPDATE `" + SCHEMA + "`.`" + REPAIR_AUDIT
                + "` SET review_status=?,review_comment=?,reviewed_at=?,reviewed_by=?,"
                + "authorization_mode=?,authorization_ref=?,authorized_at=? "
                + "WHERE repair_release_id=? AND review_status IN ('PENDING_REVIEW','MANUAL_REVIEW_REQUIRED')")) {
            statement.setString(1, authorization.auditStatus()); statement.setString(2, authorization.comments);
            if ("HUMAN_REVIEW".equals(authorization.authorizationMode)) {
                statement.setTimestamp(3, authorizedAt); statement.setString(4, authorization.reviewer);
            } else { statement.setTimestamp(3, null); statement.setString(4, null); }
            statement.setString(5, authorization.authorizationMode);
            statement.setString(6, authorization.authorizationRef); statement.setTimestamp(7, authorizedAt);
            statement.setString(8, repairReleaseId);
            int updated = statement.executeUpdate();
            if (updated != 7) throw new IllegalStateException("repair audit authorization update count is not 7");
        }
    }

    private static void insertStagedRelease(Connection connection, String releaseId, String stage,
                                            Validation value, RepairAuditIdentity repair,
                                            WorldSeaReleaseAuthorization.Authorization authorization) throws Exception {
        String sql = "INSERT INTO `" + SCHEMA + "`.`" + RELEASE + "` (release_id,dataset_version,parent_dataset_version,"
                + "source_file_sha256,original_stage_fingerprint,geometry_fingerprint,repair_tool_git_sha,repair_release_id,"
                + "review_status,authorization_mode,authorization_ref,authorized_at,stage_table_name,formal_table_name,"
                + "feature_count,polygon_count,multipolygon_count,vertex_count,status,last_completed_step,created_at,updated_at) "
                + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,'STAGED','REGISTER_STAGE',NOW(),NOW())";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, releaseId); statement.setString(2, DATASET_VERSION);
            statement.setString(3, PARENT_DATASET_VERSION);
            statement.setString(4, "de7d39f8e652721796d64ce8697bb036da30280500940de8ece70ece4914266b");
            statement.setString(5, ORIGINAL_STAGE_FINGERPRINT); statement.setString(6, value.fingerprint);
            statement.setString(7, repair.repairToolGitSha); statement.setString(8, repair.repairReleaseId);
            statement.setString(9, authorization.auditStatus()); statement.setString(10, authorization.authorizationMode);
            statement.setString(11, authorization.authorizationRef);
            statement.setTimestamp(12, java.sql.Timestamp.from(java.time.OffsetDateTime.parse(authorization.authorizedAt).toInstant()));
            statement.setString(13, stage); statement.setString(14, FORMAL);
            statement.setInt(15, value.rows.size()); statement.setInt(16, value.polygons);
            statement.setInt(17, value.multiPolygons); statement.setLong(18, value.vertices);
            if (statement.executeUpdate() != 1) throw new IllegalStateException("STAGED release insert failed");
        }
    }

    private static void updateStatus(Connection connection, String releaseId,
                                     WorldSeaReleaseStateMachine.Status status,
                                     String lastStep, String error) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement("UPDATE `" + SCHEMA + "`.`" + RELEASE
                + "` SET status=?,last_completed_step=?,error_message=?,updated_at=NOW() WHERE release_id=?")) {
            statement.setString(1, status.name()); statement.setString(2, lastStep);
            statement.setString(3, error); statement.setString(4, releaseId);
            if (statement.executeUpdate() != 1) throw new IllegalStateException("release status update failed");
        }
    }

    private static void markPublishing(Connection connection, String releaseId, String backup,
                                       Active previous) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement("UPDATE `" + SCHEMA + "`.`" + RELEASE
                + "` SET status='PUBLISHING',publish_mode='RENAMED',backup_table_name=?,previous_active_dataset_version=?,"
                + "previous_active_fingerprint=?,last_completed_step='REGISTER_STAGE',updated_at=NOW() WHERE release_id=?")) {
            statement.setString(1, backup);
            statement.setString(2, previous == null ? null : previous.datasetVersion);
            statement.setString(3, previous == null ? null : previous.fingerprint);
            statement.setString(4, releaseId); statement.executeUpdate();
        }
    }

    private static void markNoopIdentical(Connection connection, String releaseId,
                                          Active previous) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement("UPDATE `" + SCHEMA + "`.`" + RELEASE
                + "` SET status='NOOP_IDENTICAL',publish_mode='NOOP_IDENTICAL',"
                + "previous_active_dataset_version=?,previous_active_fingerprint=?,"
                + "last_completed_step='PUBLISH_STAGE',published_at=NOW(),updated_at=NOW(),error_message=NULL "
                + "WHERE release_id=?")) {
            statement.setString(1, previous == null ? null : previous.datasetVersion);
            statement.setString(2, previous == null ? null : previous.fingerprint);
            statement.setString(3, releaseId); statement.executeUpdate();
        }
    }

    private static void updatePublishedInactive(Connection connection, String releaseId) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement("UPDATE `" + SCHEMA + "`.`" + RELEASE
                + "` SET status='PUBLISHED_INACTIVE',last_completed_step='PUBLISH_STAGE',published_at=NOW(),"
                + "updated_at=NOW(),error_message=NULL WHERE release_id=?")) {
            statement.setString(1, releaseId); statement.executeUpdate();
        }
    }

    private static void setFailedTable(Connection connection, String releaseId, String failed) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement("UPDATE `" + SCHEMA + "`.`" + RELEASE
                + "` SET failed_table_name=?,updated_at=NOW() WHERE release_id=?")) {
            statement.setString(1, failed); statement.setString(2, releaseId); statement.executeUpdate();
        }
    }

    private static void upsertMeta(Connection connection, ReleaseRow release, Validation value) throws Exception {
        String sql = "INSERT INTO `" + SCHEMA + "`.`" + META + "` "
                + "(dataset_version,source_file_name,source_file_sha256,geometry_fingerprint,feature_count,polygon_count,"
                + "multipolygon_count,vertex_count,coordinate_assumption,imported_at,imported_by,status) "
                + "VALUES (?,?,?,?,?,?,?,?,?,NOW(),?,'ACTIVE') ON DUPLICATE KEY UPDATE "
                + "source_file_sha256=VALUES(source_file_sha256),geometry_fingerprint=VALUES(geometry_fingerprint),"
                + "feature_count=VALUES(feature_count),polygon_count=VALUES(polygon_count),"
                + "multipolygon_count=VALUES(multipolygon_count),vertex_count=VALUES(vertex_count),"
                + "coordinate_assumption=VALUES(coordinate_assumption),imported_at=NOW(),"
                + "imported_by=VALUES(imported_by),status='ACTIVE'";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, DATASET_VERSION); statement.setString(2, "world_sea.sql");
            statement.setString(3, release.sourceSha); statement.setString(4, release.fingerprint);
            statement.setInt(5, value.rows.size()); statement.setInt(6, value.polygons);
            statement.setInt(7, value.multiPolygons); statement.setLong(8, value.vertices);
            statement.setString(9, WorldSeaMaskLoader.COORDINATE_ASSUMPTION);
            statement.setString(10, System.getProperty("user.name", "world-sea-import-tool"));
            statement.executeUpdate();
        }
    }

    private static ReleaseRow release(Connection connection, String stage) throws Exception {
        if (!tableExists(connection, RELEASE)) return null;
        String sql = "SELECT release_id,source_file_sha256,geometry_fingerprint,status,last_completed_step,"
                + "backup_table_name,failed_table_name,publish_mode,previous_active_dataset_version,previous_active_fingerprint,"
                + "review_status,authorization_mode,authorization_ref,authorized_at "
                + "FROM `" + SCHEMA + "`.`" + RELEASE + "` WHERE stage_table_name=? ORDER BY created_at DESC LIMIT 1";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, stage);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) return null;
                return new ReleaseRow(rs.getString(1), rs.getString(2), rs.getString(3),
                        WorldSeaReleaseStateMachine.Status.valueOf(rs.getString(4)), rs.getString(5),
                        safeOptionalTable(rs.getString(6)), safeOptionalTable(rs.getString(7)), rs.getString(8),
                        rs.getString(9), rs.getString(10), rs.getString(11), rs.getString(12),
                        rs.getString(13), rs.getTimestamp(14));
            }
        }
    }

    private static ReleaseRow requiredRelease(Connection connection, String stage) throws Exception {
        ReleaseRow value = release(connection, stage);
        if (value == null) throw new IllegalStateException("release is not registered; run IMPORT_STAGE");
        return value;
    }

    private static Active active(Connection connection) throws Exception {
        if (!tableExists(connection, META)) return null;
        try (Statement statement = connection.createStatement(); ResultSet rs = statement.executeQuery(
                "SELECT dataset_version,geometry_fingerprint FROM `" + SCHEMA + "`.`" + META
                        + "` WHERE status='ACTIVE' ORDER BY imported_at DESC")) {
            if (!rs.next()) return null;
            Active value = new Active(rs.getString(1), rs.getString(2));
            if (rs.next()) throw new IllegalStateException("multiple ACTIVE WORLD_SEA datasets");
            return value;
        }
    }

    private static void requireMigration(Connection connection) throws Exception {
        if (!tableExists(connection, RELEASE) || !tableExists(connection, META)
                || !tableExists(connection, AUDIT) || !tableExists(connection, REPAIR_AUDIT)) {
            throw new IllegalStateException("migration.sql must be applied independently before release commands");
        }
    }

    private static int activeCount(Connection connection) throws Exception {
        if (!tableExists(connection, META)) return 0;
        try (Statement statement = connection.createStatement(); ResultSet rs = statement.executeQuery(
                "SELECT COUNT(*) FROM `" + SCHEMA + "`.`" + META + "` WHERE status='ACTIVE'")) {
            rs.next(); return rs.getInt(1);
        }
    }

    private static int countOtherPublishing(Connection connection, String releaseId) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement("SELECT COUNT(*) FROM `" + SCHEMA + "`.`" + RELEASE
                + "` WHERE status='PUBLISHING' AND release_id<>?")) {
            statement.setString(1, releaseId); try (ResultSet rs = statement.executeQuery()) { rs.next(); return rs.getInt(1); }
        }
    }

    private static List<String> statements(File file) throws Exception {
        List<String> result = new ArrayList<String>(); StringBuilder current = new StringBuilder();
        try (BufferedReader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
            String line; while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty() || line.startsWith("--")) continue;
                current.append(line).append('\n');
                if (line.trim().endsWith(";")) { result.add(current.toString().trim()); current.setLength(0); }
            }
        }
        if (current.length() != 0) throw new IllegalStateException("unterminated controlled SQL statement");
        return result;
    }

    private static void validateStatements(List<String> statements, String stage) {
        if (statements.size() != 103) throw new IllegalStateException("controlled SQL must contain one CREATE and 102 INSERT statements");
        String target = "`dataanalysis`.`" + stage + "`";
        for (int i = 0; i < statements.size(); i++) {
            String upper = statements.get(i).toUpperCase();
            if (upper.contains("CREATE DATABASE") || upper.contains("DROP TABLE") || upper.matches("(?s).*\\bUSE\\s+.*"))
                throw new IllegalStateException("forbidden SQL in controlled import");
            if (!statements.get(i).contains(target)) throw new IllegalStateException("non-allowlisted import target");
            if ((i == 0 && !upper.startsWith("CREATE TABLE")) || (i > 0 && !upper.startsWith("INSERT INTO")))
                throw new IllegalStateException("unexpected controlled SQL statement");
        }
    }

    private static String sourceSha(File file) throws Exception {
        try (BufferedReader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("-- source_sha256=")) return line.substring("-- source_sha256=".length()).trim();
                if (!line.startsWith("--")) break;
            }
        }
        throw new IllegalStateException("source SHA header missing");
    }

    private static boolean same(WorldSeaMaskLoader.Row a, WorldSeaMaskLoader.Row b) {
        return a.getId() == b.getId() && equal(a.getName(), b.getName())
                && equal(a.getCnName(), b.getCnName()) && java.util.Arrays.equals(a.getWkb(), b.getWkb());
    }
    private static boolean equal(Object a, Object b) { return a == null ? b == null : a.equals(b); }
    private static Map<Integer, WorldSeaMaskLoader.Row> rowsById(List<WorldSeaMaskLoader.Row> rows) { Map<Integer, WorldSeaMaskLoader.Row> result = new HashMap<Integer, WorldSeaMaskLoader.Row>(); for (WorldSeaMaskLoader.Row row : rows) result.put(row.getId(), row); return result; }
    private static String sqlString(String value) {
        if ("NULL".equals(value)) return null;
        String source = value.substring(1, value.length() - 1);
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < source.length(); i++) {
            char current = source.charAt(i);
            if (current == '\'' && i + 1 < source.length() && source.charAt(i + 1) == '\'') {
                result.append('\''); i++;
            } else if (current == '\\' && i + 1 < source.length() && source.charAt(i + 1) == '\\') {
                result.append('\\'); i++;
            } else {
                result.append(current);
            }
        }
        return result.toString();
    }
    private static byte[] hex(String value) { byte[] bytes = new byte[value.length() / 2]; for (int i = 0; i < bytes.length; i++) bytes[i] = (byte) Integer.parseInt(value.substring(i * 2, i * 2 + 2), 16); return bytes; }
    private static long maxBytes(List<String> values) { long max = 0L; for (String value : values) max = Math.max(max, value.getBytes(StandardCharsets.UTF_8).length); return max; }
    private static String releaseId(String fingerprint, String stage) {
        return DATASET_VERSION + "_" + fingerprint.substring(0, 12)
                + "_" + stage.substring("ship_behavior_world_sea_stage_".length());
    }
    private static String nowStamp() { return LocalDateTime.now().format(STAMP); }
    private static String value(Object value) { return value == null ? "NONE" : String.valueOf(value); }
    private static String abbreviate(String value) { if (value == null) return null; return value.length() <= 1000 ? value : value.substring(0, 1000); }
    private static String sha256(byte[] value) throws Exception { byte[] digest = java.security.MessageDigest.getInstance("SHA-256").digest(value); StringBuilder result = new StringBuilder(); for (byte b : digest) result.append(String.format("%02x", b & 0xff)); return result.toString(); }
    private static String required(Properties properties, String key) { String value = properties.getProperty(key); if (value == null || value.trim().isEmpty()) throw new IllegalStateException("required datasource property missing: " + key); return value.trim(); }
    private static long maxAllowedPacket(Connection connection) throws Exception { try (Statement statement = connection.createStatement(); ResultSet rs = statement.executeQuery("SHOW VARIABLES LIKE 'max_allowed_packet'")) { if (!rs.next()) throw new IllegalStateException("max_allowed_packet unavailable"); return rs.getLong(2); } }
    private static boolean tableExists(Connection connection, String table) throws Exception { try (PreparedStatement statement = connection.prepareStatement("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=? AND table_name=?")) { statement.setString(1, SCHEMA); statement.setString(2, table); try (ResultSet rs = statement.executeQuery()) { rs.next(); return rs.getInt(1) == 1; } } }
    private static void rename(Connection connection, String sql) throws Exception { try (Statement statement = connection.createStatement()) { statement.execute(sql); } }
    private static String safeOptionalTable(String value) { if (value == null) return null; if (!SAFE_RELEASE_TABLE.matcher(value).matches()) throw new IllegalStateException("unsafe stored release table name"); return value; }

    enum Command { DRY_RUN, STATE, IMPORT_STAGE, VALIDATE_STAGE, REGISTER_STAGE, PUBLISH_STAGE, ACTIVATE_DATASET, RESUME_RELEASE, ROLLBACK_RELEASE, APPLY }

    private static final class ImportPayload {
        final List<String> statements; final Map<Integer, WorldSeaMaskLoader.Row> rows;
        final Map<Integer, String> insertById; final String sourceSha; final Validation validation;
        ImportPayload(List<String> statements, Map<Integer, WorldSeaMaskLoader.Row> rows,
                      Map<Integer, String> insertById, String sourceSha, Validation validation) {
            this.statements = statements; this.rows = rows; this.insertById = insertById;
            this.sourceSha = sourceSha; this.validation = validation;
        }
    }
    private static final class Validation {
        final List<WorldSeaMaskLoader.Row> rows; final int polygons, multiPolygons;
        final long vertices; final String fingerprint; final List<String> invalid;
        Validation(List<WorldSeaMaskLoader.Row> rows, int polygons, int multiPolygons, long vertices,
                   String fingerprint, List<String> invalid) {
            this.rows = rows; this.polygons = polygons; this.multiPolygons = multiPolygons;
            this.vertices = vertices; this.fingerprint = fingerprint; this.invalid = invalid;
        }
    }
    private static final class ReleaseRow {
        final String releaseId, sourceSha, fingerprint, lastCompletedStep, backupTable, failedTable, publishMode;
        final WorldSeaReleaseStateMachine.Status status;
        final String previousActiveDataset, previousActiveFingerprint, reviewStatus;
        final String authorizationMode, authorizationRef;
        final java.sql.Timestamp authorizedAt;
        ReleaseRow(String releaseId, String sourceSha, String fingerprint,
                   WorldSeaReleaseStateMachine.Status status, String lastCompletedStep,
                   String backupTable, String failedTable, String publishMode, String previousActiveDataset,
                   String previousActiveFingerprint, String reviewStatus, String authorizationMode,
                   String authorizationRef, java.sql.Timestamp authorizedAt) {
            this.releaseId = releaseId; this.sourceSha = sourceSha; this.fingerprint = fingerprint;
            this.status = status; this.lastCompletedStep = lastCompletedStep;
            this.backupTable = backupTable; this.failedTable = failedTable;
            this.publishMode = publishMode;
            this.previousActiveDataset = previousActiveDataset;
            this.previousActiveFingerprint = previousActiveFingerprint;
            this.reviewStatus = reviewStatus; this.authorizationMode = authorizationMode;
            this.authorizationRef = authorizationRef; this.authorizedAt = authorizedAt;
        }
    }
    private static final class Active {
        final String datasetVersion, fingerprint;
        Active(String datasetVersion, String fingerprint) { this.datasetVersion = datasetVersion; this.fingerprint = fingerprint; }
    }
    private static final class RepairAuditIdentity {
        final String repairReleaseId, repairToolGitSha;
        RepairAuditIdentity(String repairReleaseId, String repairToolGitSha) {
            this.repairReleaseId = repairReleaseId; this.repairToolGitSha = repairToolGitSha;
        }
    }
    private static final class RepairAuditState {
        final String repairReleaseId, reviewStatus;
        final int rows;
        RepairAuditState(String repairReleaseId, String reviewStatus, int rows) {
            this.repairReleaseId = repairReleaseId; this.reviewStatus = reviewStatus; this.rows = rows;
        }
    }
    private WorldSeaImportTool() {}
}
