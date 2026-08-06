package com.hifleet.worldsea;

import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.PrintWriter;
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
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Pattern;

/** Audited, fixed-scope WORLD_SEA V1R1 repair tool. It never publishes or activates data. */
public final class WorldSeaGeometryRepairTool {
    private static final String CONFIG_FILE = "/data/zhengyi/application.properties";
    private static final String SCHEMA = "dataanalysis";
    private static final String ORIGINAL_STAGE = "ship_behavior_world_sea_stage_20260806_155556";
    private static final String FORMAL = "ship_behavior_world_sea";
    private static final String META = "ship_behavior_world_sea_meta";
    private static final String RELEASE = "ship_behavior_world_sea_release";
    private static final String REPAIR_AUDIT = "ship_behavior_world_sea_repair_audit";
    private static final String SOURCE_SHA = "de7d39f8e652721796d64ce8697bb036da30280500940de8ece70ece4914266b";
    private static final String ORIGINAL_FINGERPRINT = "81c810a9db7d319fdfbf9a17805a61493d3beda23b09154958ce2a2b3a31fd1e";
    private static final Pattern REPAIRED_STAGE = Pattern.compile("ship_behavior_world_sea_stage_\\d{8}_\\d{6}");
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
    private static final ObjectMapper JSON = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    public static void main(String[] args) throws Exception {
        if (args.length < 1 || args.length > 2) usage();
        Command command;
        try { command = Command.valueOf(args[0]); }
        catch (IllegalArgumentException ex) { usage(); return; }
        if ((command == Command.VALIDATE_REPAIRED_STAGE || command == Command.STATE) && args.length != 2) usage();
        if ((command == Command.ANALYZE || command == Command.GENERATE_REPORT || command == Command.CREATE_REPAIRED_STAGE)
                && args.length != 1) usage();
        try (Connection connection = connection()) {
            if (command == Command.ANALYZE) analyze(connection);
            else if (command == Command.GENERATE_REPORT) generateReport(connection);
            else if (command == Command.CREATE_REPAIRED_STAGE) createRepairedStage(connection);
            else if (command == Command.VALIDATE_REPAIRED_STAGE) validateRepairedStage(connection, args[1]);
            else if (command == Command.STATE) state(connection, args[1]);
        }
    }

    private static void usage() {
        throw new IllegalArgumentException("usage: WorldSeaGeometryRepairTool "
                + "ANALYZE|GENERATE_REPORT|CREATE_REPAIRED_STAGE|VALIDATE_REPAIRED_STAGE|STATE [repaired_stage]");
    }

    private static void analyze(Connection connection) throws Exception {
        WorldSeaGeometryRepair.DatasetRepair repair = repairOriginal(connection);
        Map<String, Object> report = report(repair, "ANALYZE");
        report.put("databaseWrites", 0);
        System.out.println(JSON.writeValueAsString(report));
    }

    private static void generateReport(Connection connection) throws Exception {
        WorldSeaGeometryRepair.DatasetRepair repair = repairOriginal(connection);
        File output = outputDirectory();
        if (!output.exists() && !output.mkdirs()) throw new IllegalStateException("cannot create report directory=" + output);
        Map<String, Object> value = report(repair, "GENERATE_REPORT");
        JSON.writeValue(new File(output, "repair-report.json"), value);
        writeCsv(new File(output, "repair-report.csv"), repair.features);
        writeMarkdown(new File(output, "repair-report.md"), repair);
        writeGeoJson(new File(output, "original-invalid.geojson"), repair.features, GeometryChoice.ORIGINAL);
        writeGeoJson(new File(output, "repaired-preview.geojson"), repair.features, GeometryChoice.REPAIRED);
        writeGeoJson(new File(output, "repair-difference.geojson"), repair.features, GeometryChoice.DIFFERENCE);
        writeAtlas(new File(output, "repair-atlas.html"), repair.features);
        JSON.writeValue(new File(output, "repair-approval.template.json"), approvalTemplate(repair.repairedFingerprint));
        System.out.println("WORLD_SEA_REPAIR GENERATE_REPORT PASS output=" + output.getAbsolutePath()
                + " repairedGeometryFingerprint=" + repair.repairedFingerprint + " databaseWrites=0");
    }

    private static void createRepairedStage(Connection connection) throws Exception {
        requireTable(connection, REPAIR_AUDIT, "run migration.sql before CREATE_REPAIRED_STAGE");
        String toolSha = requiredToolSha();
        WorldSeaGeometryRepair.DatasetRepair repair = repairOriginal(connection);
        requireFullDataset(repair);
        String stage = "ship_behavior_world_sea_stage_" + LocalDateTime.now().format(STAMP);
        if (ORIGINAL_STAGE.equals(stage) || tableExists(connection, stage)) {
            throw new IllegalStateException("new repaired stage name already exists=" + stage);
        }
        try (Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE `" + SCHEMA + "`.`" + stage + "` LIKE `" + SCHEMA + "`.`" + ORIGINAL_STAGE + "`");
        }
        try {
            try (PreparedStatement statement = connection.prepareStatement("INSERT INTO `" + SCHEMA + "`.`" + stage
                    + "` SELECT * FROM `" + SCHEMA + "`.`" + ORIGINAL_STAGE + "`")) {
                if (statement.executeUpdate() != 102) throw new IllegalStateException("original stage copy count is not 102");
            }
            try (PreparedStatement statement = connection.prepareStatement("UPDATE `" + SCHEMA + "`.`" + stage
                    + "` SET the_geom=GeomFromWKB(?,101) WHERE ID=?")) {
                for (WorldSeaGeometryRepair.FeatureRepair feature : repair.features) {
                    statement.setBytes(1, feature.repairedWkb);
                    statement.setInt(2, feature.id);
                    if (statement.executeUpdate() != 1) throw new IllegalStateException("repair update count differs ID=" + feature.id);
                }
            }
            StageValidation validation = validateAgainstOriginal(connection, stage, true);
            if (!repair.repairedFingerprint.equals(validation.fingerprint)) {
                throw new IllegalStateException("database repaired fingerprint differs from deterministic preview");
            }
            String releaseId = WorldSeaGeometryRepair.DATASET_AFTER + "_" + validation.fingerprint.substring(0, 12)
                    + "_" + stage.substring("ship_behavior_world_sea_stage_".length());
            insertAudit(connection, releaseId, stage, repair, toolSha);
            System.out.println("WORLD_SEA_REPAIR CREATE_REPAIRED_STAGE PASS repairedStage=" + stage
                    + " repairReleaseId=" + releaseId + " rows=102 unchangedWkb=95 changedWkb=7 changedIds="
                    + repair.changedIds + " repairedGeometryFingerprint=" + validation.fingerprint
                    + " reviewStatus=PENDING_REVIEW");
        } catch (Exception ex) {
            System.err.println("WORLD_SEA_REPAIR CREATE_REPAIRED_STAGE FAILED retainedForInspection=" + stage);
            throw ex;
        }
    }

    private static void validateRepairedStage(Connection connection, String stage) throws Exception {
        requireRepairedStage(stage);
        StageValidation value = validateAgainstOriginal(connection, stage, true);
        System.out.println("WORLD_SEA_REPAIR VALIDATE_REPAIRED_STAGE PASS stage=" + stage
                + " rows=" + value.rows + " uniqueIds=" + value.uniqueIds + " emptyGeometry=0 validGeometry=102"
                + " polygonal=102 nonPolygonal=0 outOfRange=0 nonFinite=0 srid101=102"
                + " unchangedWkb=" + value.unchanged + " changedWkb=" + value.changed
                + " changedIds=" + value.changedIds + " repairedGeometryFingerprint=" + value.fingerprint
                + " databaseWrites=0");
    }

    private static void state(Connection connection, String stage) throws Exception {
        requireRepairedStage(stage);
        StageValidation stageValue = tableExists(connection, stage) ? validateAgainstOriginal(connection, stage, false) : null;
        String releaseId = null, releaseStatus = null, lastStep = null;
        if (tableExists(connection, RELEASE)) {
            try (PreparedStatement statement = connection.prepareStatement("SELECT release_id,status,last_completed_step FROM `"
                    + SCHEMA + "`.`" + RELEASE + "` WHERE stage_table_name=? ORDER BY created_at DESC LIMIT 1")) {
                statement.setString(1, stage);
                try (ResultSet rs = statement.executeQuery()) {
                    if (rs.next()) { releaseId = rs.getString(1); releaseStatus = rs.getString(2); lastStep = rs.getString(3); }
                }
            }
        }
        TableIdentity formal = tableIdentity(connection, FORMAL);
        ActiveIdentity active = activeIdentity(connection);
        String next = releaseStatus == null ? "AWAIT_APPROVAL_THEN_REGISTER_STAGE"
                : WorldSeaReleaseStateMachine.recover(new WorldSeaReleaseStateMachine.ReleaseReality(
                WorldSeaReleaseStateMachine.Status.valueOf(releaseStatus), stageValue != null, false,
                formal != null, formal != null && formal.fingerprint.equals(stageValue == null ? "" : stageValue.fingerprint),
                formal != null && !formal.fingerprint.equals(stageValue == null ? "" : stageValue.fingerprint),
                active != null && active.fingerprint.equals(stageValue == null ? "" : stageValue.fingerprint))).getNextAllowedAction();
        System.out.println("WORLD_SEA_REPAIR STATE releaseId=" + value(releaseId) + " releaseStatus=" + value(releaseStatus)
                + " lastCompletedStep=" + value(lastStep) + " stageExists=" + (stageValue != null)
                + " stageRows=" + (stageValue == null ? 0 : stageValue.rows)
                + " stageFingerprint=" + value(stageValue == null ? null : stageValue.fingerprint)
                + " formalExists=" + (formal != null) + " formalRows=" + (formal == null ? 0 : formal.rows)
                + " formalFingerprint=" + value(formal == null ? null : formal.fingerprint)
                + " activeDatasetVersion=" + value(active == null ? null : active.datasetVersion)
                + " activeFingerprint=" + value(active == null ? null : active.fingerprint)
                + " activeCount=" + (active == null ? 0 : active.count)
                + " backupExists=false recoveryNeeded=" + (releaseStatus != null && !"ACTIVE".equals(releaseStatus))
                + " nextAllowedAction=" + next + " databaseWrites=0");
    }

    private static WorldSeaGeometryRepair.DatasetRepair repairOriginal(Connection connection) throws Exception {
        requireTable(connection, ORIGINAL_STAGE, "fixed original stage is missing");
        List<WorldSeaGeometryRepair.SourceRow> rows = load(connection, ORIGINAL_STAGE);
        String fingerprint = fingerprint(rows);
        if (rows.size() != 102 || !ORIGINAL_FINGERPRINT.equals(fingerprint)) {
            throw new IllegalStateException("original stage identity mismatch rows=" + rows.size() + " fingerprint=" + fingerprint);
        }
        WorldSeaGeometryRepair.DatasetRepair repair = WorldSeaGeometryRepair.repair(rows);
        requireFullDataset(repair);
        return repair;
    }

    private static void requireFullDataset(WorldSeaGeometryRepair.DatasetRepair repair) {
        if (repair.rows.size() != 102 || repair.unchangedCount != 95 || repair.changedCount != 7
                || !repair.changedIds.equals(WorldSeaGeometryRepair.REPAIR_IDS)) {
            throw new IllegalStateException("repair scope invariant failed");
        }
    }

    private static StageValidation validateAgainstOriginal(Connection connection, String stage, boolean strict) throws Exception {
        requireRepairedStage(stage);
        List<WorldSeaGeometryRepair.SourceRow> original = load(connection, ORIGINAL_STAGE);
        List<WorldSeaGeometryRepair.SourceRow> repaired = load(connection, stage);
        Map<Integer, WorldSeaGeometryRepair.SourceRow> originals = byId(original);
        Set<Integer> ids = new LinkedHashSet<Integer>();
        Set<Integer> changedIds = new LinkedHashSet<Integer>();
        int unchanged = 0, changed = 0, polygons = 0, multiPolygons = 0;
        long vertices = 0;
        for (WorldSeaGeometryRepair.SourceRow row : repaired) {
            if (!ids.add(row.id)) throw new IllegalStateException("duplicate repaired ID=" + row.id);
            WorldSeaGeometryRepair.SourceRow before = originals.get(row.id);
            if (before == null) throw new IllegalStateException("unexpected repaired ID=" + row.id);
            if (Arrays.equals(before.wkb, row.wkb)) unchanged++;
            else { changed++; changedIds.add(row.id); }
            Geometry geometry = new org.locationtech.jts.io.WKBReader().read(row.wkb);
            geometry.setSRID(row.srid);
            if (geometry.isEmpty() || !geometry.isValid()) throw new IllegalStateException("invalid repaired Geometry ID=" + row.id);
            if ("Polygon".equals(geometry.getGeometryType())) polygons++;
            else if ("MultiPolygon".equals(geometry.getGeometryType())) multiPolygons++;
            else throw new IllegalStateException("non-Polygonal repaired Geometry ID=" + row.id);
            if (row.srid != 101) throw new IllegalStateException("repaired SRID mismatch ID=" + row.id);
            for (Coordinate coordinate : geometry.getCoordinates()) {
                if (!Double.isFinite(coordinate.x) || !Double.isFinite(coordinate.y)
                        || coordinate.x < -180 || coordinate.x > 180 || coordinate.y < -90 || coordinate.y > 90) {
                    throw new IllegalStateException("repaired coordinate out of range ID=" + row.id);
                }
            }
            vertices += geometry.getNumPoints();
        }
        String fingerprint = fingerprint(repaired);
        StageValidation value = new StageValidation(repaired.size(), ids.size(), unchanged, changed, changedIds,
                polygons, multiPolygons, vertices, fingerprint);
        if (strict && (value.rows != 102 || value.uniqueIds != 102 || value.unchanged != 95 || value.changed != 7
                || !value.changedIds.equals(WorldSeaGeometryRepair.REPAIR_IDS)
                || value.polygons + value.multiPolygons != 102)) {
            throw new IllegalStateException("repaired stage strict validation failed");
        }
        return value;
    }

    private static List<WorldSeaGeometryRepair.SourceRow> load(Connection connection, String table) throws Exception {
        if (!ORIGINAL_STAGE.equals(table) && !FORMAL.equals(table) && !REPAIRED_STAGE.matcher(table).matches()) {
            throw new IllegalStateException("unsafe WORLD_SEA table name");
        }
        List<WorldSeaGeometryRepair.SourceRow> result = new ArrayList<WorldSeaGeometryRepair.SourceRow>();
        String sql = "SELECT ID,NAME,CNNAME,AsBinary(the_geom),SRID(the_geom) FROM `" + SCHEMA + "`.`" + table + "` ORDER BY ID";
        try (Statement statement = connection.createStatement(); ResultSet rs = statement.executeQuery(sql)) {
            while (rs.next()) result.add(new WorldSeaGeometryRepair.SourceRow(rs.getInt(1), rs.getString(2),
                    rs.getString(3), rs.getBytes(4), rs.getInt(5)));
        }
        return result;
    }

    private static void insertAudit(Connection connection, String releaseId, String stage,
                                    WorldSeaGeometryRepair.DatasetRepair repair, String toolSha) throws Exception {
        String sql = "INSERT INTO `" + SCHEMA + "`.`" + REPAIR_AUDIT + "` (repair_release_id,dataset_version_before,"
                + "dataset_version_after,original_stage_fingerprint,repaired_geometry_fingerprint,repaired_stage_table,feature_id,feature_name,feature_cn_name,original_geometry_type,repaired_geometry_type,"
                + "original_validity_error,repaired_valid,original_wkb_sha256,repaired_wkb_sha256,original_vertex_count,"
                + "repaired_vertex_count,original_component_count,repaired_component_count,original_area_degree2,"
                + "repaired_area_degree2,relative_area_delta,original_envelope,repaired_envelope,collapsed_component_count,"
                + "collapsed_component_type,collapsed_component_length,collapsed_component_decision,polygon_component_areas,"
                + "minimum_polygon_area,main_polygon_area,minimum_polygon_ratio,repair_method,repair_tool_git_sha,review_status,created_at) "
                + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,'PENDING_REVIEW',NOW())";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (WorldSeaGeometryRepair.FeatureRepair feature : repair.features) {
                int i = 1;
                statement.setString(i++, releaseId); statement.setString(i++, WorldSeaGeometryRepair.DATASET_BEFORE);
                statement.setString(i++, WorldSeaGeometryRepair.DATASET_AFTER);
                statement.setString(i++, ORIGINAL_FINGERPRINT); statement.setString(i++, repair.repairedFingerprint);
                statement.setString(i++, stage);
                statement.setInt(i++, feature.id);
                statement.setString(i++, feature.name); statement.setString(i++, feature.cnName);
                statement.setString(i++, feature.originalGeometry.getGeometryType());
                statement.setString(i++, feature.repairedGeometry.getGeometryType());
                statement.setString(i++, feature.originalValidityError); statement.setBoolean(i++, true);
                statement.setString(i++, feature.originalWkbSha256); statement.setString(i++, feature.repairedWkbSha256);
                statement.setInt(i++, feature.originalGeometry.getNumPoints()); statement.setInt(i++, feature.repairedGeometry.getNumPoints());
                statement.setInt(i++, feature.originalGeometry.getNumGeometries()); statement.setInt(i++, feature.repairedGeometry.getNumGeometries());
                statement.setDouble(i++, feature.originalArea); statement.setDouble(i++, feature.repairedArea);
                statement.setDouble(i++, feature.relativeAreaDelta); statement.setString(i++, envelope(feature.originalGeometry.getEnvelopeInternal()));
                statement.setString(i++, envelope(feature.repairedGeometry.getEnvelopeInternal()));
                statement.setInt(i++, feature.collapsedComponentTypes.size());
                statement.setString(i++, join(feature.collapsedComponentTypes)); statement.setDouble(i++, feature.collapsedComponentLength);
                statement.setString(i++, "DROP_NON_POLYGONAL_COLLAPSE");
                statement.setString(i++, JSON.writeValueAsString(feature.polygonComponentAreas));
                statement.setDouble(i++, feature.minimumPolygonArea); statement.setDouble(i++, feature.mainPolygonArea);
                statement.setDouble(i++, feature.minimumPolygonRatio); statement.setString(i++, WorldSeaGeometryRepair.METHOD);
                statement.setString(i++, toolSha);
                statement.addBatch();
            }
            int[] counts = statement.executeBatch();
            if (counts.length != 7) throw new IllegalStateException("repair audit insert count is not 7");
        }
    }

    private static Map<String, Object> report(WorldSeaGeometryRepair.DatasetRepair repair, String command) {
        Map<String, Object> root = new LinkedHashMap<String, Object>();
        root.put("command", command); root.put("datasetVersionBefore", WorldSeaGeometryRepair.DATASET_BEFORE);
        root.put("datasetVersion", WorldSeaGeometryRepair.DATASET_AFTER); root.put("parentDatasetVersion", WorldSeaGeometryRepair.DATASET_BEFORE);
        root.put("sourceFileSha256", SOURCE_SHA); root.put("originalStage", ORIGINAL_STAGE);
        root.put("originalStageFingerprint", ORIGINAL_FINGERPRINT); root.put("repairedGeometryFingerprint", repair.repairedFingerprint);
        root.put("repairMethod", WorldSeaGeometryRepair.METHOD); root.put("repairToolGitSha", toolSha());
        root.put("unchangedFeatureCount", repair.unchangedCount); root.put("repairedFeatureCount", repair.changedCount);
        root.put("changedFeatureIds", repair.changedIds); root.put("features", featureReports(repair.features));
        root.put("overallReviewStatus", overallReviewStatus(repair.features)); root.put("databaseWrites", 0);
        return root;
    }

    private static List<Map<String, Object>> featureReports(List<WorldSeaGeometryRepair.FeatureRepair> features) {
        List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
        for (WorldSeaGeometryRepair.FeatureRepair feature : features) {
            Map<String, Object> value = new LinkedHashMap<String, Object>();
            value.put("featureId", feature.id); value.put("featureName", feature.name); value.put("featureCnName", feature.cnName);
            value.put("originalValidityError", feature.originalValidityError);
            value.put("errorCoordinate", feature.errorCoordinate == null ? null
                    : Arrays.asList(feature.errorCoordinate.x, feature.errorCoordinate.y));
            value.put("originalGeometryType", feature.originalGeometry.getGeometryType());
            value.put("repairedGeometryType", feature.repairedGeometry.getGeometryType());
            value.put("originalComponentCount", feature.originalGeometry.getNumGeometries());
            value.put("repairedComponentCount", feature.repairedGeometry.getNumGeometries());
            value.put("originalVertexCount", feature.originalGeometry.getNumPoints());
            value.put("repairedVertexCount", feature.repairedGeometry.getNumPoints());
            value.put("originalEnvelope", envelope(feature.originalGeometry.getEnvelopeInternal()));
            value.put("repairedEnvelope", envelope(feature.repairedGeometry.getEnvelopeInternal()));
            value.put("originalWkbSha256", feature.originalWkbSha256); value.put("repairedWkbSha256", feature.repairedWkbSha256);
            value.put("originalAreaDegree2", feature.originalArea); value.put("repairedAreaDegree2", feature.repairedArea);
            value.put("relativeAreaDelta", feature.relativeAreaDelta); value.put("envelopeDeltaDegree", feature.envelopeDelta);
            value.put("collapsedComponentCount", feature.collapsedComponentTypes.size());
            value.put("collapsedComponentType", join(feature.collapsedComponentTypes));
            value.put("collapsedComponentLength", feature.collapsedComponentLength);
            value.put("collapsedComponentDecision", "DROP_NON_POLYGONAL_COLLAPSE");
            value.put("polygonComponentAreas", feature.polygonComponentAreas); value.put("minimumPolygonArea", feature.minimumPolygonArea);
            value.put("mainPolygonArea", feature.mainPolygonArea); value.put("minimumPolygonRatio", feature.minimumPolygonRatio);
            value.put("boHaiTinyComponentDecision", feature.id == 24556 ? "RETAIN" : "NOT_APPLICABLE");
            value.put("idempotent", feature.idempotent); value.put("reviewStatus", feature.reviewStatus);
            result.add(value);
        }
        return result;
    }

    private static Map<String, Object> approvalTemplate(String fingerprint) {
        Map<String, Object> value = new LinkedHashMap<String, Object>();
        value.put("datasetVersion", WorldSeaGeometryRepair.DATASET_AFTER);
        value.put("repairedGeometryFingerprint", fingerprint); value.put("reviewStatus", "PENDING_REVIEW");
        value.put("reviewedFeatureIds", WorldSeaGeometryRepair.REPAIR_IDS);
        value.put("baffinCollapsedLineDecision", "DROP_NON_POLYGONAL_COLLAPSE");
        value.put("boHaiTinyComponentDecision", "RETAIN"); value.put("reviewer", null);
        value.put("reviewedAt", null); value.put("comments", null);
        return value;
    }

    private static void writeCsv(File file, List<WorldSeaGeometryRepair.FeatureRepair> features) throws Exception {
        try (PrintWriter out = new PrintWriter(file, "UTF-8")) {
            out.println("feature_id,feature_name,feature_cn_name,original_type,repaired_type,original_vertices,repaired_vertices,original_components,repaired_components,relative_area_delta,envelope_delta,collapsed_count,collapsed_type,collapsed_length,min_polygon_area,main_polygon_area,min_polygon_ratio,review_status");
            for (WorldSeaGeometryRepair.FeatureRepair f : features) out.println(f.id + "," + csv(f.name) + "," + csv(f.cnName)
                    + "," + f.originalGeometry.getGeometryType() + "," + f.repairedGeometry.getGeometryType()
                    + "," + f.originalGeometry.getNumPoints() + "," + f.repairedGeometry.getNumPoints()
                    + "," + f.originalGeometry.getNumGeometries() + "," + f.repairedGeometry.getNumGeometries()
                    + "," + f.relativeAreaDelta + "," + f.envelopeDelta + "," + f.collapsedComponentTypes.size()
                    + "," + csv(join(f.collapsedComponentTypes)) + "," + f.collapsedComponentLength
                    + "," + f.minimumPolygonArea + "," + f.mainPolygonArea + "," + f.minimumPolygonRatio + "," + f.reviewStatus);
        }
    }

    private static void writeMarkdown(File file, WorldSeaGeometryRepair.DatasetRepair repair) throws Exception {
        try (PrintWriter out = new PrintWriter(file, "UTF-8")) {
            out.println("# WORLD_SEA_102_V1R1 geometry repair report\n");
            out.println("- Semantic scope: `WORLD_SEA_NAMED_AREA` (global named marine-water mask only)");
            out.println("- Method: `" + WorldSeaGeometryRepair.METHOD + "`");
            out.println("- Original stage fingerprint: `" + ORIGINAL_FINGERPRINT + "`");
            out.println("- Repaired geometry fingerprint: `" + repair.repairedFingerprint + "`");
            out.println("- Unchanged WKB: 95; changed WKB: 7; review: **PENDING_REVIEW**\n");
            out.println("| ID | Name | Type before → after | Vertices before → after | Components before → after | Relative area delta | Envelope delta | Review |");
            out.println("|---:|---|---|---:|---:|---:|---:|---|");
            for (WorldSeaGeometryRepair.FeatureRepair f : repair.features) out.println("| " + f.id + " | " + f.name + " / "
                    + value(f.cnName) + " | " + f.originalGeometry.getGeometryType() + " → " + f.repairedGeometry.getGeometryType()
                    + " | " + f.originalGeometry.getNumPoints() + " → " + f.repairedGeometry.getNumPoints()
                    + " | " + f.originalGeometry.getNumGeometries() + " → " + f.repairedGeometry.getNumGeometries()
                    + " | " + f.relativeAreaDelta + " | " + f.envelopeDelta + " | " + f.reviewStatus + " |");
            out.println("\nBaffin Bay non-polygonal collapses are dropped (`DROP_NON_POLYGONAL_COLLAPSE`); the Polygonal body is retained.");
            out.println("Bo Hai retains every Polygonal component. No small-polygon deletion threshold is implemented.");
        }
    }

    private static void writeGeoJson(File file, List<WorldSeaGeometryRepair.FeatureRepair> features,
                                     GeometryChoice choice) throws Exception {
        JsonGenerator out = JSON.getFactory().createGenerator(file, JsonEncoding.UTF8);
        out.writeStartObject(); out.writeStringField("type", "FeatureCollection"); out.writeArrayFieldStart("features");
        for (WorldSeaGeometryRepair.FeatureRepair feature : features) {
            Geometry geometry = choice == GeometryChoice.ORIGINAL ? feature.originalGeometry
                    : choice == GeometryChoice.REPAIRED ? feature.repairedGeometry : feature.differenceGeometry;
            out.writeStartObject(); out.writeStringField("type", "Feature"); out.writeObjectFieldStart("properties");
            out.writeNumberField("id", feature.id); out.writeStringField("name", feature.name);
            if (feature.cnName != null) out.writeStringField("cnName", feature.cnName);
            out.writeStringField("layer", choice.name());
            if (feature.errorCoordinate != null) {
                out.writeArrayFieldStart("errorCoordinate"); out.writeNumber(feature.errorCoordinate.x);
                out.writeNumber(feature.errorCoordinate.y); out.writeEndArray();
            }
            out.writeEndObject(); out.writeFieldName("geometry"); writeGeometry(out, geometry); out.writeEndObject();
        }
        out.writeEndArray(); out.writeEndObject(); out.close();
    }

    private static void writeGeometry(JsonGenerator out, Geometry geometry) throws Exception {
        String type = geometry.getGeometryType();
        out.writeStartObject(); out.writeStringField("type", type);
        if ("GeometryCollection".equals(type)) {
            out.writeArrayFieldStart("geometries");
            for (int i = 0; i < geometry.getNumGeometries(); i++) writeGeometry(out, geometry.getGeometryN(i));
            out.writeEndArray(); out.writeEndObject(); return;
        }
        out.writeFieldName("coordinates");
        if ("Polygon".equals(type)) writePolygonCoordinates(out, (org.locationtech.jts.geom.Polygon) geometry);
        else if ("MultiPolygon".equals(type)) {
            out.writeStartArray();
            for (int i = 0; i < geometry.getNumGeometries(); i++)
                writePolygonCoordinates(out, (org.locationtech.jts.geom.Polygon) geometry.getGeometryN(i));
            out.writeEndArray();
        } else if ("LineString".equals(type)) writeCoordinates(out, geometry.getCoordinates());
        else if ("MultiLineString".equals(type)) {
            out.writeStartArray(); for (int i = 0; i < geometry.getNumGeometries(); i++)
                writeCoordinates(out, geometry.getGeometryN(i).getCoordinates()); out.writeEndArray();
        } else if ("Point".equals(type)) writeCoordinate(out, geometry.getCoordinate());
        else { out.writeStartArray(); out.writeEndArray(); }
        out.writeEndObject();
    }

    private static void writePolygonCoordinates(JsonGenerator out, org.locationtech.jts.geom.Polygon polygon) throws Exception {
        out.writeStartArray(); writeCoordinates(out, polygon.getExteriorRing().getCoordinates());
        for (int i = 0; i < polygon.getNumInteriorRing(); i++) writeCoordinates(out, polygon.getInteriorRingN(i).getCoordinates());
        out.writeEndArray();
    }
    private static void writeCoordinates(JsonGenerator out, Coordinate[] coordinates) throws Exception {
        out.writeStartArray(); for (Coordinate coordinate : coordinates) writeCoordinate(out, coordinate); out.writeEndArray();
    }
    private static void writeCoordinate(JsonGenerator out, Coordinate coordinate) throws Exception {
        out.writeStartArray(); out.writeNumber(coordinate.x); out.writeNumber(coordinate.y); out.writeEndArray();
    }

    private static void writeAtlas(File file, List<WorldSeaGeometryRepair.FeatureRepair> features) throws Exception {
        String points = errorPoints(features);
        String html = "<!doctype html><html><head><meta charset=\"utf-8\"><title>WORLD_SEA V1R1 repair atlas</title>"
                + "<meta name=\"viewport\" content=\"width=device-width\"><link rel=\"stylesheet\" href=\"https://unpkg.com/leaflet@1.9.4/dist/leaflet.css\">"
                + "<style>body{margin:0;font:14px sans-serif}#map{height:78vh}header{padding:10px}code{background:#eee}</style></head><body>"
                + "<header><b>WORLD_SEA_102_V1R1 repair atlas</b> — red=original, blue=repaired, orange=difference, black=self-intersection. "
                + "Serve this directory over HTTP so the three real GeoJSON files can be loaded. <code>python3 -m http.server</code></header><div id=\"map\"></div>"
                + "<script src=\"https://unpkg.com/leaflet@1.9.4/dist/leaflet.js\"></script><script>const map=L.map('map').setView([20,20],2);"
                + "L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png',{maxZoom:18,attribution:'OpenStreetMap'}).addTo(map);"
                + "const layers=[];function add(url,color){fetch(url).then(r=>r.json()).then(j=>{let x=L.geoJSON(j,{style:{color:color,weight:2,fillOpacity:.12},onEachFeature:(f,l)=>l.bindPopup(f.properties.id+' '+f.properties.name+' / '+(f.properties.cnName||'')+'<br>'+f.properties.layer)}).addTo(map);layers.push(x);if(layers.length===3){let g=L.featureGroup(layers);map.fitBounds(g.getBounds())}})}"
                + "add('original-invalid.geojson','#d62728');add('repaired-preview.geojson','#1f77b4');add('repair-difference.geojson','#ff7f0e');"
                + "const pts=" + points + ";pts.forEach(p=>L.circleMarker([p.lat,p.lon],{radius:5,color:'#000',fillOpacity:1}).bindPopup(p.id+' '+p.name+' self-intersection').addTo(map));"
                + "L.control.layers(null,{}).addTo(map);</script></body></html>";
        Files.write(file.toPath(), html.getBytes(StandardCharsets.UTF_8));
    }

    private static String errorPoints(List<WorldSeaGeometryRepair.FeatureRepair> features) throws Exception {
        List<Map<String, Object>> values = new ArrayList<Map<String, Object>>();
        for (WorldSeaGeometryRepair.FeatureRepair feature : features) if (feature.errorCoordinate != null) {
            Map<String, Object> point = new LinkedHashMap<String, Object>(); point.put("id", feature.id);
            point.put("name", feature.name); point.put("lon", feature.errorCoordinate.x); point.put("lat", feature.errorCoordinate.y);
            values.add(point);
        }
        return JSON.writeValueAsString(values).replace("</", "<\\/");
    }

    private static Map<Integer, WorldSeaGeometryRepair.SourceRow> byId(List<WorldSeaGeometryRepair.SourceRow> rows) {
        Map<Integer, WorldSeaGeometryRepair.SourceRow> value = new LinkedHashMap<Integer, WorldSeaGeometryRepair.SourceRow>();
        for (WorldSeaGeometryRepair.SourceRow row : rows) value.put(row.id, row); return value;
    }
    private static String fingerprint(List<WorldSeaGeometryRepair.SourceRow> rows) {
        List<WorldSeaMaskLoader.Row> values = new ArrayList<WorldSeaMaskLoader.Row>();
        for (WorldSeaGeometryRepair.SourceRow row : rows) values.add(new WorldSeaMaskLoader.Row(row.id, row.name, row.cnName, row.wkb));
        return WorldSeaMaskLoader.fingerprint(values);
    }
    private static String overallReviewStatus(List<WorldSeaGeometryRepair.FeatureRepair> features) {
        for (WorldSeaGeometryRepair.FeatureRepair feature : features)
            if ("MANUAL_REVIEW_REQUIRED".equals(feature.reviewStatus)) return "MANUAL_REVIEW_REQUIRED";
        return "PENDING_REVIEW";
    }
    private static String envelope(Envelope value) { return "[" + value.getMinX() + "," + value.getMinY() + "," + value.getMaxX() + "," + value.getMaxY() + "]"; }
    private static String csv(String value) { return value == null ? "" : "\"" + value.replace("\"", "\"\"") + "\""; }
    private static String join(List<String> values) { return values == null || values.isEmpty() ? "NONE" : String.join("|", values); }
    private static String value(Object value) { return value == null ? "NONE" : String.valueOf(value); }
    private static String toolSha() { return System.getProperty("worldSea.repair.gitSha", "UNCOMMITTED"); }
    private static String requiredToolSha() { String value = toolSha(); if (!value.matches("[0-9a-f]{40}")) throw new IllegalStateException("-DworldSea.repair.gitSha=<40-hex committed SHA> is required"); return value; }
    private static File outputDirectory() { return new File(System.getProperty("worldSea.repair.outputDir", "dist/world-sea-data-repair-v1")); }
    private static void requireRepairedStage(String table) { if (!REPAIRED_STAGE.matcher(table).matches() || ORIGINAL_STAGE.equals(table)) throw new IllegalStateException("repaired stage name is not allowlisted"); }
    private static void requireTable(Connection connection, String table, String message) throws Exception { if (!tableExists(connection, table)) throw new IllegalStateException(message); }
    private static boolean tableExists(Connection connection, String table) throws Exception { try (PreparedStatement statement = connection.prepareStatement("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=? AND table_name=?")) { statement.setString(1, SCHEMA); statement.setString(2, table); try (ResultSet rs = statement.executeQuery()) { rs.next(); return rs.getInt(1) == 1; } } }

    private static TableIdentity tableIdentity(Connection connection, String table) throws Exception {
        if (!tableExists(connection, table)) return null; List<WorldSeaGeometryRepair.SourceRow> rows = load(connection, table);
        return new TableIdentity(rows.size(), fingerprint(rows));
    }
    private static ActiveIdentity activeIdentity(Connection connection) throws Exception {
        if (!tableExists(connection, META)) return null;
        try (Statement statement = connection.createStatement(); ResultSet rs = statement.executeQuery("SELECT dataset_version,geometry_fingerprint FROM `" + SCHEMA + "`.`" + META + "` WHERE status='ACTIVE' ORDER BY imported_at DESC")) {
            if (!rs.next()) return null; ActiveIdentity value = new ActiveIdentity(rs.getString(1), rs.getString(2), 1);
            if (rs.next()) throw new IllegalStateException("multiple ACTIVE WORLD_SEA datasets"); return value;
        }
    }
    private static Connection connection() throws Exception {
        Properties properties = new Properties();
        try (FileInputStream input = new FileInputStream(CONFIG_FILE)) { properties.load(input); }
        Class.forName(properties.getProperty("spring.datasource.newdataanalysis.driver-class-name", "com.mysql.jdbc.Driver"));
        Connection connection = DriverManager.getConnection(required(properties, "spring.datasource.newdataanalysis.url"),
                required(properties, "spring.datasource.newdataanalysis.username"), required(properties, "spring.datasource.newdataanalysis.password"));
        if (!SCHEMA.equals(connection.getCatalog())) connection.setCatalog(SCHEMA); return connection;
    }
    private static String required(Properties properties, String key) { String value = properties.getProperty(key); if (value == null || value.trim().isEmpty()) throw new IllegalStateException("required datasource property missing: " + key); return value.trim(); }

    enum Command { ANALYZE, GENERATE_REPORT, CREATE_REPAIRED_STAGE, VALIDATE_REPAIRED_STAGE, STATE }
    enum GeometryChoice { ORIGINAL, REPAIRED, DIFFERENCE }
    private static final class StageValidation {
        final int rows, uniqueIds, unchanged, changed, polygons, multiPolygons; final long vertices;
        final Set<Integer> changedIds; final String fingerprint;
        StageValidation(int rows, int uniqueIds, int unchanged, int changed, Set<Integer> changedIds,
                        int polygons, int multiPolygons, long vertices, String fingerprint) {
            this.rows=rows; this.uniqueIds=uniqueIds; this.unchanged=unchanged; this.changed=changed;
            this.changedIds=changedIds; this.polygons=polygons; this.multiPolygons=multiPolygons;
            this.vertices=vertices; this.fingerprint=fingerprint;
        }
    }
    private static final class TableIdentity { final int rows; final String fingerprint; TableIdentity(int rows, String fingerprint) { this.rows=rows; this.fingerprint=fingerprint; } }
    private static final class ActiveIdentity { final String datasetVersion, fingerprint; final int count; ActiveIdentity(String datasetVersion, String fingerprint, int count) { this.datasetVersion=datasetVersion; this.fingerprint=fingerprint; this.count=count; } }
    private WorldSeaGeometryRepairTool() {}
}
