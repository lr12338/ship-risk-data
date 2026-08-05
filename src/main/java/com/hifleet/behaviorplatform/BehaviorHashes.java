package com.hifleet.behaviorplatform;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import java.util.TreeMap;

final class BehaviorHashes {
    private BehaviorHashes() { }

    static String canonicalJson(Object value) {
        return JSON.toJSONString(sort(value));
    }

    private static Object sort(Object value) {
        if (value instanceof Map) {
            TreeMap<String, Object> sorted = new TreeMap<String, Object>();
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
                sorted.put(String.valueOf(entry.getKey()), sort(entry.getValue()));
            }
            return sorted;
        }
        if (value instanceof Iterable) {
            JSONArray result = new JSONArray();
            for (Object item : (Iterable<?>) value) result.add(sort(item));
            return result;
        }
        if (value != null && !(value instanceof String) && !(value instanceof Number)
                && !(value instanceof Boolean) && !value.getClass().isEnum()) {
            return sort((JSONObject) JSON.toJSON(value));
        }
        return value == null || !value.getClass().isEnum() ? value : String.valueOf(value);
    }

    static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(64);
            for (byte item : digest) result.append(String.format("%02x", item & 0xff));
            return result.toString();
        } catch (Exception ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }
}
