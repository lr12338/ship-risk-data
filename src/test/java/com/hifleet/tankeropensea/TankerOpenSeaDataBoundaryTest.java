package com.hifleet.tankeropensea;

import org.junit.Test;
import org.springframework.beans.factory.annotation.Qualifier;

import java.lang.reflect.Field;

import static org.junit.Assert.assertEquals;

public class TankerOpenSeaDataBoundaryTest {

    @Test
    public void aisMetadataRepositoryUsesExplicitReadOnlyConnection() throws Exception {
        Field field = TankerOpenSeaTargetShipRepository.class
                .getDeclaredField("aisReadOnlyJdbcTemplate");

        assertEquals("aid41ReadOnlyJdbcTemplate",
                field.getAnnotation(Qualifier.class).value());
    }

    @Test
    public void writableRepositoriesUseOnlyEventDatabaseConnection() throws Exception {
        Field template = TankerOpenSeaTargetShipRepository.class
                .getDeclaredField("eventJdbcTemplate");
        Field dataSource = TankerOpenSeaTargetShipRepository.class
                .getDeclaredField("eventDataSource");

        assertEquals("newDataanalysisJdbcTemplate",
                template.getAnnotation(Qualifier.class).value());
        assertEquals("newDataanalysisDataSource",
                dataSource.getAnnotation(Qualifier.class).value());
    }

    @Test
    public void exclusionLoaderUsesExistingReadOnlySources() throws Exception {
        Field event = PortAnchorBerthExclusionService.class
                .getDeclaredField("eventJdbcTemplate");
        Field ais = PortAnchorBerthExclusionService.class
                .getDeclaredField("aisJdbcTemplate");

        assertEquals("newDataanalysisJdbcTemplate",
                event.getAnnotation(Qualifier.class).value());
        assertEquals("aid41ReadOnlyJdbcTemplate",
                ais.getAnnotation(Qualifier.class).value());
    }

    @Test
    public void degradedCandidateRepositoryWritesOnlyEventDatabase() throws Exception {
        Field template = DegradedCandidateRepository.class
                .getDeclaredField("jdbcTemplate");
        Field dataSource = DegradedCandidateRepository.class
                .getDeclaredField("dataSource");

        assertEquals("newDataanalysisJdbcTemplate",
                template.getAnnotation(Qualifier.class).value());
        assertEquals("newDataanalysisDataSource",
                dataSource.getAnnotation(Qualifier.class).value());
    }
}
