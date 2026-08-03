package com.hifleet.maritime;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class MaritimeSceneAuditRepositoryTest {
    @Test
    public void auditKeyIsDeterministicAndChangesWithSnapshot() {
        MaritimeSceneAuditRepository repository = new MaritimeSceneAuditRepository(null, false);
        String first = repository.auditKey("batch", "LOITERING", "123", null,
                "2026-01-01", "2026-01-02", "a");
        String second = repository.auditKey("batch", "LOITERING", "123", null,
                "2026-01-01", "2026-01-02", "a");
        String changed = repository.auditKey("batch", "LOITERING", "123", null,
                "2026-01-01", "2026-01-02", "b");
        assertEquals(first, second);
        if (first.equals(changed)) throw new AssertionError("snapshot must affect audit key");
    }
}
