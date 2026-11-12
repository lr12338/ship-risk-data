package com.hifleet.worldsea;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;

public class WorldSeaMigrationToolTest {
    @Test
    public void splitsStatementsWithoutSplittingQuotedDynamicSql() {
        List<String> values = WorldSeaMigrationTool.statements("-- comment\nSET @x='ALTER X; Y'; PREPARE s FROM @x; EXECUTE s;");
        assertEquals(3, values.size());
        assertEquals("SET @x='ALTER X; Y'", values.get(0));
    }

    @Test(expected = IllegalStateException.class)
    public void rejectsUnterminatedStatement() {
        WorldSeaMigrationTool.statements("SELECT 1");
    }
}
