package com.hifleet.job.services;

import com.hifleet.repository.TargetShipRepository;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class TargetShipListServiceTest {
    @Test
    public void approvedSourceModeCopiesOnlyFixed5000Batch() {
        TargetShipListService service = new TargetShipListService();
        TargetShipRepository repository = mock(TargetShipRepository.class);
        ReflectionTestUtils.setField(service, "targetRepository", repository);
        String result = service.create("{\"sourceMode\":\"EXISTING_5000_TABLE\","
                + "\"sourceListBatchNo\":\"liquid-container-5000-2025h2-2026h1-v1\","
                + "\"listBatchNo\":\"liquid-container-5000-2025h2-2026h1-v1\","
                + "\"targetCount\":5000}");
        verify(repository).copyExisting5000Immutable(
                "liquid-container-5000-2025h2-2026h1-v1",
                "liquid-container-5000-2025h2-2026h1-v1", 5000);
        assertTrue(result.contains("count=5000"));
    }
}
