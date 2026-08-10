package com.hifleet.job.services;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class BehaviorInsuranceRulesJobParamsTest {

    @Test
    public void deploymentParamsAreSingleLineUniqueAndMatchWritePolicy() throws Exception {
        List<String> lines = Files.readAllLines(Paths.get("deploy/behavior-insurance-rules-v1/xxl-job-params.json"),
                StandardCharsets.UTF_8);
        for (String line : lines) {
            if (line.contains("\"params\"")) {
                assertTrue("each XXL params object must be one physical line", line.trim().endsWith("},") || line.trim().endsWith("}"));
            }
        }

        JSONArray entries = JSON.parseArray(new String(Files.readAllBytes(
                Paths.get("deploy/behavior-insurance-rules-v1/xxl-job-params.json")), StandardCharsets.UTF_8));
        assertEquals(8, entries.size());
        Set<String> batchNos = new HashSet<String>();
        Set<String> canonicalParams = new LinkedHashSet<String>();
        for (Object entryValue : entries) {
            JSONObject entry = (JSONObject) entryValue;
            String handler = entry.getString("handler");
            ProductionTestParams params = ProductionTestParams.parse(entry.getJSONObject("params").toJSONString());
            canonicalParams.add(entry.getJSONObject("params").toJSONString());
            assertTrue(batchNos.add(params.getBatchNo()));
            assertTrue(params.getBatchNo().length() <= 64);
            assertEquals(5000, params.getExpectedShipCount());
            assertEquals(1, params.getParallelism());
            assertTrue(params.isResume());
            if (handler.equals("shipLoiteringProductionTestJobHandler")) {
                params.validateLoitering();
            } else {
                assertEquals("shipAlongsideProductionTestJobHandler", handler);
                params.validateAlongside();
            }
            if (params.getShipLimit() == 5000) {
                assertTrue(params.isEnableInsert());
            } else {
                assertTrue(params.getShipLimit() == 20 || params.getShipLimit() == 100 || params.getShipLimit() == 500);
                assertFalse(params.isEnableInsert());
            }
        }

        Set<String> documentedParams = new LinkedHashSet<String>();
        for (String line : Files.readAllLines(Paths.get("deploy/behavior-insurance-rules-v1/xxl-job-guide.md"),
                StandardCharsets.UTF_8)) {
            String value = line.trim();
            if (value.startsWith("{\"mode\":\"production_test\"")) {
                documentedParams.add(JSON.parseObject(value).toJSONString());
            }
        }
        assertEquals("guide and canonical parameter file must contain the same eight payloads",
                canonicalParams, documentedParams);
    }
}
