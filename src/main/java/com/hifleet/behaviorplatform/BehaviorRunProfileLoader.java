package com.hifleet.behaviorplatform;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.io.BufferedReader;
import java.io.InputStreamReader;

public final class BehaviorRunProfileLoader {
    public BehaviorRunProfileRegistry load(Path directory) {
        if (directory == null || !Files.isDirectory(directory))
            throw new IllegalArgumentException("behavior profile directory is missing: " + directory);
        Map<String, JSONObject> raw = new LinkedHashMap<String, JSONObject>();
        List<Path> paths = new ArrayList<Path>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory, "*.json")) {
            for (Path path : stream) paths.add(path);
        } catch (IOException ex) {
            throw new IllegalStateException("cannot list behavior profiles", ex);
        }
        Collections.sort(paths);
        for (Path path : paths) {
            try {
                JSONObject value = JSON.parseObject(new String(Files.readAllBytes(path), StandardCharsets.UTF_8));
                String id = value == null ? null : value.getString("profileId");
                if (id == null) throw new IllegalArgumentException("profileId missing in " + path.getFileName());
                if (raw.put(id, value) != null) throw new IllegalArgumentException("duplicate profileId: " + id);
            } catch (IOException ex) {
                throw new IllegalStateException("cannot read behavior profile " + path, ex);
            }
        }
        return build(raw);
    }

    public BehaviorRunProfileRegistry loadClasspathAndExternal(
            ClassLoader loader, String indexResource, Path externalDirectory) {
        Map<String, JSONObject> raw = new LinkedHashMap<String, JSONObject>();
        InputStream index = loader.getResourceAsStream(indexResource);
        if (index == null) throw new IllegalStateException("missing classpath profile index: " + indexResource);
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(index, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String name = line.trim();
                if (name.isEmpty() || name.startsWith("#")) continue;
                String resource = indexResource.substring(0, indexResource.lastIndexOf('/') + 1) + name;
                InputStream stream = loader.getResourceAsStream(resource);
                if (stream == null) throw new IllegalStateException("missing classpath profile: " + resource);
                JSONObject value = JSON.parseObject(new String(readAll(stream), StandardCharsets.UTF_8));
                add(raw, value, resource);
            }
        } catch (IOException ex) {
            throw new IllegalStateException("cannot read classpath profiles", ex);
        }
        if (externalDirectory != null && Files.exists(externalDirectory)) {
            BehaviorRunProfileRegistry external = load(externalDirectory);
            for (BehaviorRunProfile profile : external.all()) {
                JSONObject value = JSON.parseObject(profile.getResolvedSnapshot());
                add(raw, value, externalDirectory.toString());
            }
        }
        return build(raw);
    }

    private BehaviorRunProfileRegistry build(Map<String, JSONObject> raw) {
        BehaviorRunProfileRegistry result = new BehaviorRunProfileRegistry();
        for (String id : raw.keySet()) result.register(BehaviorRunProfile.from(resolve(id, raw,
                new ArrayList<String>())));
        return result;
    }

    private void add(Map<String, JSONObject> raw, JSONObject value, String source) {
        String id = value == null ? null : value.getString("profileId");
        if (id == null) throw new IllegalArgumentException("profileId missing in " + source);
        if (raw.put(id, value) != null) throw new IllegalArgumentException("duplicate profileId: " + id);
    }

    private byte[] readAll(InputStream input) throws IOException {
        java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int count;
        while ((count = input.read(buffer)) >= 0) output.write(buffer, 0, count);
        return output.toByteArray();
    }

    private JSONObject resolve(String id, Map<String, JSONObject> raw, List<String> stack) {
        JSONObject value = raw.get(id);
        if (value == null) throw new IllegalArgumentException("unknown parent profile: " + id);
        if (stack.contains(id)) throw new IllegalArgumentException("profile inheritance cycle: " + stack);
        stack.add(id);
        JSONObject result = new JSONObject(true);
        String parent = value.getString("extendsProfileId");
        if (parent != null) result.putAll(resolve(parent, raw, stack));
        result.putAll(value);
        result.remove("extendsProfileId");
        stack.remove(stack.size() - 1);
        return result;
    }
}
