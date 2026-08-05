package com.hifleet.behaviorplatform;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public final class RuleSetLoader {
    public RuleSetRegistry loadClasspath(ClassLoader loader, String indexResource) {
        InputStream index = loader.getResourceAsStream(indexResource);
        if (index == null) throw new IllegalStateException("missing rule set index: " + indexResource);
        RuleSetRegistry result = new RuleSetRegistry();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(index, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String name = line.trim();
                if (name.isEmpty() || name.startsWith("#")) continue;
                String resource = indexResource.substring(0, indexResource.lastIndexOf('/') + 1) + name;
                InputStream stream = loader.getResourceAsStream(resource);
                if (stream == null) throw new IllegalStateException("missing rule set: " + resource);
                result.register(parse(JSON.parseObject(new String(readAll(stream), StandardCharsets.UTF_8))));
            }
        } catch (IOException ex) {
            throw new IllegalStateException("cannot load rule sets", ex);
        }
        return result;
    }

    private RuleSetDefinition parse(JSONObject value) {
        List<RuleDefinition> rules = new ArrayList<RuleDefinition>();
        JSONArray array = value.getJSONArray("rules");
        if (array != null) for (int i = 0; i < array.size(); i++) {
            JSONObject rule = array.getJSONObject(i);
            rules.add(new RuleDefinition(rule.getString("ruleId"), rule.getString("ruleVersion"),
                    rule.getDoubleValue("threshold"), rule.getString("unit")));
        }
        return new RuleSetDefinition(value.getString("ruleSetId"), value.getIntValue("ruleSetVersion"),
                value.getBooleanValue("productionApproved"), rules);
    }

    private byte[] readAll(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int count;
        while ((count = input.read(buffer)) >= 0) output.write(buffer, 0, count);
        return output.toByteArray();
    }
}
