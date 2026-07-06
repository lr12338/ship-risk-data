package com.hifleet.analysis.portcall.config;

import org.junit.Assert;
import org.junit.Test;

import java.util.Properties;

public class ProgramArgumentsParserTest {

    @Test
    public void shouldParseSpaceSeparatedArguments() {
        Properties parsed = PortCallConfig.parseProgramArguments(new String[] {
            "--kafka.group.id", "test-group",
            "--alongside.enabled", "true",
            "--alongside.db-sink.batch-size", "1",
            "--alongside.candidate.max-speed", "6.0"
        });

        Assert.assertEquals("test-group", parsed.getProperty("kafka.group.id"));
        Assert.assertEquals("true", parsed.getProperty("alongside.enabled"));
        Assert.assertEquals("1", parsed.getProperty("alongside.db-sink.batch-size"));
        Assert.assertEquals("6.0", parsed.getProperty("alongside.candidate.max-speed"));
    }

    @Test
    public void shouldParseEqualsArguments() {
        Properties parsed = PortCallConfig.parseProgramArguments(new String[] {
            "--kafka.group.id=test-group",
            "--alongside.enabled=true"
        });

        Assert.assertEquals("test-group", parsed.getProperty("kafka.group.id"));
        Assert.assertEquals("true", parsed.getProperty("alongside.enabled"));
    }

    @Test
    public void shouldLetProgramArgumentsOverrideApplicationProperties() {
        PortCallConfig config = PortCallConfig.defaultConfig(new String[] {
            "--kafka.group.id", "test-group",
            "--alongside.enabled", "true",
            "--alongside.db-sink.batch-size", "1",
            "--alongside.candidate.max-speed", "6.0"
        });

        Assert.assertEquals("test-group", config.getKafkaGroupId());
        Assert.assertTrue(config.getAlongsideConfig().isEnabled());
        Assert.assertEquals(1, config.getAlongsideConfig().getDbSinkConfig().getBatchSize());
        Assert.assertEquals(6.0d, config.getAlongsideConfig().getCandidateMaxSpeedKnots(), 0.001d);
    }

    @Test
    public void shouldLetEqualsProgramArgumentsOverrideApplicationProperties() {
        PortCallConfig config = PortCallConfig.defaultConfig(new String[] {
            "--kafka.group.id=test-group-equals",
            "--alongside.enabled=true"
        });

        Assert.assertEquals("test-group-equals", config.getKafkaGroupId());
        Assert.assertTrue(config.getAlongsideConfig().isEnabled());
    }

    @Test
    public void shouldUseApplicationPropertiesWhenNoArgumentsProvided() {
        PortCallConfig config = PortCallConfig.defaultConfig(new String[0]);

        Assert.assertEquals("portcall-group", config.getKafkaGroupId());
        Assert.assertFalse(config.getAlongsideConfig().isEnabled());
    }

    @Test
    public void shouldRedactPasswordForSafeConfigOutput() {
        Assert.assertEquals("<redacted>",
            PortCallConfig.safeConfigValue("alongside.db-sink.password", "secret"));
        Assert.assertEquals("value", PortCallConfig.safeConfigValue("kafka.group.id", "value"));
    }
}
