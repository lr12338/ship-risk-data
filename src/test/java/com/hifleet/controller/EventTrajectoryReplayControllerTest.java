package com.hifleet.controller;

import com.hifleet.service.EventTrajectoryReplayService;
import org.junit.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class EventTrajectoryReplayControllerTest {

    @Test
    public void disabledEndpointReturns503WithoutQueryingEvent() {
        Fixture fixture = fixture(false);

        ResponseEntity<?> response = fixture.controller.replay(1L, "secret");

        assertEquals(503, response.getStatusCodeValue());
        verify(fixture.service, never()).replay(1L);
    }

    @Test
    public void invalidTokenReturns401() {
        Fixture fixture = fixture(true);

        ResponseEntity<?> response = fixture.controller.replay(1L, "wrong");

        assertEquals(401, response.getStatusCodeValue());
        verify(fixture.service, never()).replay(1L);
    }

    @Test
    public void replayDomainErrorKeepsItsHttpStatus() {
        Fixture fixture = fixture(true);
        when(fixture.service.replay(1L)).thenThrow(
                new EventTrajectoryReplayService.ReplayException(
                        422, "NO_DATA", "no data"));

        ResponseEntity<?> response = fixture.controller.replay(1L, "secret");

        assertEquals(422, response.getStatusCodeValue());
    }

    @Test
    public void validTokenReturnsReplayPayload() {
        Fixture fixture = fixture(true);
        when(fixture.service.replay(1L)).thenReturn(
                Collections.<String, Object>singletonMap("status", "MATCH"));

        ResponseEntity<?> response = fixture.controller.replay(1L, "secret");

        assertEquals(200, response.getStatusCodeValue());
    }

    @Test
    public void previewProjectionUsesPreviewServiceMethod() {
        Fixture fixture = fixture(true);
        when(fixture.service.replay(1L, "PREVIEW")).thenReturn(
                Collections.<String, Object>singletonMap("status", "MATCH"));

        ResponseEntity<?> response = fixture.controller.replay(1L, "secret", "PREVIEW");

        assertEquals(200, response.getStatusCodeValue());
        verify(fixture.service).replay(1L, "PREVIEW");
    }

    private Fixture fixture(boolean enabled) {
        Fixture fixture = new Fixture();
        fixture.controller = new EventTrajectoryReplayController();
        fixture.service = mock(EventTrajectoryReplayService.class);
        ReflectionTestUtils.setField(fixture.controller, "replayService", fixture.service);
        ReflectionTestUtils.setField(fixture.controller, "enabled", enabled);
        ReflectionTestUtils.setField(fixture.controller, "token", "secret");
        return fixture;
    }

    private static class Fixture {
        private EventTrajectoryReplayController controller;
        private EventTrajectoryReplayService service;
    }
}
