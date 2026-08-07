package com.hifleet.controller;

import com.hifleet.service.EventTrajectoryReplayService;
import com.hifleet.service.EventTrajectoryReplayService.ReplayException;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
public class EventTrajectoryReplayController {

    @Autowired
    private EventTrajectoryReplayService replayService;

    @Value("${ship.behavior.trace.replay.enabled:false}")
    private boolean enabled;

    @Value("${ship.behavior.trace.replay.token:}")
    private String token;

    public ResponseEntity<?> replay(long eventId, String requestToken) {
        return replay(eventId, requestToken, "FULL");
    }

    @PostMapping("/internal/ship-behavior/events/{eventId}/trajectory")
    @SuppressWarnings("unused")
    public ResponseEntity<?> replay(
            @PathVariable("eventId") long eventId,
            @RequestHeader(value = "X-Trace-Token", required = false) String requestToken,
            @RequestParam(value = "projection", defaultValue = "FULL") String projection) {
        if (!enabled || StringUtils.isBlank(token)) {
            return error(503, "REPLAY_DISABLED", "trajectory replay is disabled");
        }
        if (!constantTimeEquals(token, requestToken)) {
            return error(401, "UNAUTHORIZED", "invalid trace token");
        }
        try {
            if ("FULL".equalsIgnoreCase(projection)) {
                return ResponseEntity.ok(replayService.replay(eventId));
            }
            return ResponseEntity.ok(replayService.replay(eventId, projection));
        } catch (ReplayException ex) {
            return error(ex.getHttpStatus(), ex.getCode(), ex.getMessage());
        } catch (Exception ex) {
            return error(503, "QUERY_FAILED", "trajectory replay failed");
        }
    }

    private ResponseEntity<Map<String, Object>> error(int status, String code, String message) {
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("status", code);
        body.put("message", message);
        return ResponseEntity.status(status).body(body);
    }

    private boolean constantTimeEquals(String expected, String actual) {
        byte[] left = expected == null ? new byte[0]
                : expected.getBytes(StandardCharsets.UTF_8);
        byte[] right = actual == null ? new byte[0]
                : actual.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(left, right);
    }
}
