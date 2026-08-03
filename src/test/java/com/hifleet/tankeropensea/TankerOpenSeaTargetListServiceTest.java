package com.hifleet.tankeropensea;

import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class TankerOpenSeaTargetListServiceTest {
    @Test
    @SuppressWarnings("unchecked")
    public void createsBalancedFiveThousandShipSnapshot() {
        TankerOpenSeaTargetShipRepository repository =
                mock(TankerOpenSeaTargetShipRepository.class);
        when(repository.count(anyString())).thenReturn(0);
        List<ShipMetadata> source = new java.util.ArrayList<ShipMetadata>();
        for (int i = 1; i <= 5000; i++) {
            source.add(metadata(String.valueOf(100000000 + i),
                    i % 2 == 1 ? "油轮" : "集装箱船"));
        }
        when(repository.selectSourceCandidatesPage(
                anyString(), anyInt(), anyInt())).thenReturn(source);
        TankerOpenSeaTargetListService service = service(repository);

        service.create(params(2500, 2500));

        ArgumentCaptor<List> captor = ArgumentCaptor.forClass(List.class);
        verify(repository).insertImmutable(
                org.mockito.Matchers.eq("liquid-container-test-v1"),
                captor.capture());
        List<ListedTanker> rows = (List<ListedTanker>) captor.getValue();
        assertEquals(5000, rows.size());
        assertEquals(1, rows.get(0).getSeqNo());
        assertEquals(5000, rows.get(4999).getSeqNo());
        assertEquals(StandardShipType.TANKER_OIL,
                rows.get(0).getEligibility().getStandardShipType());
        assertEquals(StandardShipType.CONTAINER,
                rows.get(4999).getEligibility().getStandardShipType());
        verify(repository).validateDistribution(
                "liquid-container-test-v1", 2500, 2500);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void requestedTypesAreInterleavedInImmutableList() {
        TankerOpenSeaTargetShipRepository repository =
                mock(TankerOpenSeaTargetShipRepository.class);
        when(repository.count(anyString())).thenReturn(0);
        when(repository.selectSourceCandidatesPage(
                anyString(), anyInt(), anyInt())).thenReturn(Arrays.asList(
                metadata("111111111", "油轮"),
                metadata("222222222", "集装箱船"),
                metadata("333333333", "LNG"),
                metadata("444444444", "container")));
        TankerOpenSeaTargetListService service =
                new TankerOpenSeaTargetListService();
        ReflectionTestUtils.setField(service, "repository", repository);

        service.create("{\"mode\":\"liquid_container_exclusion_list\","
                + "\"listBatchNo\":\"liquid-container-test-v1\","
                + "\"sourceUpdatedAfter\":\"2026-01-01 00:00:00\","
                + "\"liquidCargoCount\":2,\"containerCount\":2}");

        ArgumentCaptor<List> captor = ArgumentCaptor.forClass(List.class);
        verify(repository).insertImmutable(
                org.mockito.Matchers.eq("liquid-container-test-v1"),
                captor.capture());
        List<ListedTanker> rows = (List<ListedTanker>) captor.getValue();
        assertEquals(StandardShipType.TANKER_OIL,
                rows.get(0).getEligibility().getStandardShipType());
        assertEquals(StandardShipType.CONTAINER,
                rows.get(1).getEligibility().getStandardShipType());
        assertEquals(StandardShipType.TANKER_GAS,
                rows.get(2).getEligibility().getStandardShipType());
        assertEquals(StandardShipType.CONTAINER,
                rows.get(3).getEligibility().getStandardShipType());
        assertEquals(1, rows.get(0).getSeqNo());
        assertEquals(4, rows.get(3).getSeqNo());
        verify(repository).validate(anyString(), anyInt());
        verify(repository).validateDistribution(
                "liquid-container-test-v1", 2, 2);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void pagesByMmsiUntilBothTypeQuotasAreFilled() {
        TankerOpenSeaTargetShipRepository repository =
                mock(TankerOpenSeaTargetShipRepository.class);
        when(repository.count(anyString())).thenReturn(0);
        when(repository.selectSourceCandidatesPage(
                anyString(), anyInt(), anyInt()))
                .thenReturn(Arrays.asList(
                        metadata("111111111", "未知"),
                        metadata("222222222", "油轮")))
                .thenReturn(Arrays.asList(
                        metadata("333333333", "集装箱船"),
                        metadata("444444444", "LNG")))
                .thenReturn(Collections.emptyList());
        TankerOpenSeaTargetListService service = service(repository);

        service.create(params(2, 1));

        verify(repository).selectSourceCandidatesPage(
                "2026-01-01 00:00:00", 0, 5000);
        verify(repository).selectSourceCandidatesPage(
                "2026-01-01 00:00:00", 222222222, 5000);
        verify(repository, times(2)).selectSourceCandidatesPage(
                anyString(), anyInt(), anyInt());
        ArgumentCaptor<List> captor = ArgumentCaptor.forClass(List.class);
        verify(repository).insertImmutable(
                org.mockito.Matchers.eq("liquid-container-test-v1"),
                captor.capture());
        assertEquals(3, captor.getValue().size());
    }

    @Test
    public void duplicateMmsiDoesNotFillTwoQuotaSlots() {
        TankerOpenSeaTargetShipRepository repository =
                mock(TankerOpenSeaTargetShipRepository.class);
        when(repository.count(anyString())).thenReturn(0);
        when(repository.selectSourceCandidatesPage(
                anyString(), anyInt(), anyInt()))
                .thenReturn(Arrays.asList(
                        metadata("111111111", "油轮"),
                        metadata("111111111", "油轮"),
                        metadata("222222222", "集装箱船")))
                .thenReturn(Collections.emptyList());
        TankerOpenSeaTargetListService service = service(repository);

        try {
            service.create(params(2, 1));
            fail("expected insufficient eligible rows");
        } catch (IllegalStateException expected) {
            assertEquals(true, expected.getMessage().contains(
                    "eligible source rows are insufficient"));
        }
        verify(repository, never()).insertImmutable(
                anyString(), org.mockito.Matchers.anyList());
    }

    @Test
    public void stopsInsideFirstPageAsSoonAsBothQuotasAreFilled() {
        TankerOpenSeaTargetShipRepository repository =
                mock(TankerOpenSeaTargetShipRepository.class);
        when(repository.count(anyString())).thenReturn(0);
        when(repository.selectSourceCandidatesPage(
                anyString(), anyInt(), anyInt())).thenReturn(Arrays.asList(
                metadata("111111111", "油轮"),
                metadata("222222222", "集装箱船"),
                metadata("333333333", "LNG")));
        TankerOpenSeaTargetListService service = service(repository);

        service.create(params(1, 1));

        verify(repository, times(1)).selectSourceCandidatesPage(
                anyString(), anyInt(), anyInt());
    }

    private TankerOpenSeaTargetListService service(
            TankerOpenSeaTargetShipRepository repository) {
        TankerOpenSeaTargetListService service =
                new TankerOpenSeaTargetListService();
        ReflectionTestUtils.setField(service, "repository", repository);
        return service;
    }

    private String params(int liquid, int container) {
        return "{\"mode\":\"liquid_container_exclusion_list\","
                + "\"listBatchNo\":\"liquid-container-test-v1\","
                + "\"sourceUpdatedAfter\":\"2026-01-01 00:00:00\","
                + "\"liquidCargoCount\":" + liquid
                + ",\"containerCount\":" + container + "}";
    }

    private ShipMetadata metadata(String mmsi, String type) {
        return new ShipMetadata(mmsi, null, "TEST", type, type,
                null, 200.0, 50000L,
                LocalDateTime.of(2026, 1, 1, 0, 0));
    }
}
