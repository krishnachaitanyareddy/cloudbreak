package com.sequenceiq.cloudbreak.service.cluster.flow;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.Assert;
import org.junit.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sequenceiq.cloudbreak.util.FileReaderUtils;

public class JacksonBlueprintProcessorTest {

    private JacksonBlueprintProcessor underTest = new JacksonBlueprintProcessor();

    @Test
    public void testAddConfigEntriesAddsRootConfigurationsNodeIfMissing() throws Exception {
        String testBlueprint = FileReaderUtils.readFileFromClasspath("blueprints/test-bp-without-config-block.bp");
        List<BlueprintConfigurationEntry> configurationEntries = new ArrayList<>();
        String result = underTest.addConfigEntries(testBlueprint, configurationEntries);

        JsonNode configNode = new ObjectMapper().readTree(result).path("configurations");
        Assert.assertFalse(configNode.isMissingNode());
    }

    @Test
    public void testAddConfigEntriesAddsConfigFileEntryIfMissing() throws Exception {
        String testBlueprint = FileReaderUtils.readFileFromClasspath("blueprints/test-bp-with-empty-config-block.bp");
        List<BlueprintConfigurationEntry> configurationEntries = new ArrayList<>();
        configurationEntries.add(new BlueprintConfigurationEntry("core-site", "fs.AbstractFileSystem.wasb.impl", "org.apache.hadoop.fs.azure.Wasb"));
        String result = underTest.addConfigEntries(testBlueprint, configurationEntries);

        JsonNode coreSiteNode = new ObjectMapper().readTree(result).findPath("core-site");
        Assert.assertFalse(coreSiteNode.isMissingNode());
    }

    @Test
    public void testAddConfigEntriesAddsConfigEntriesToCorrectConfigBlockWithCorrectValues() throws Exception {
        String testBlueprint = FileReaderUtils.readFileFromClasspath("blueprints/test-bp-with-empty-config-block.bp");
        List<BlueprintConfigurationEntry> configurationEntries = new ArrayList<>();
        configurationEntries.add(new BlueprintConfigurationEntry("core-site", "fs.AbstractFileSystem.wasb.impl", "org.apache.hadoop.fs.azure.Wasb"));
        configurationEntries.add(new BlueprintConfigurationEntry("hdfs-site", "dfs.blocksize", "134217728"));
        String result = underTest.addConfigEntries(testBlueprint, configurationEntries);

        String configValue1 = new ObjectMapper().readTree(result).findPath("core-site").findPath("fs.AbstractFileSystem.wasb.impl").textValue();
        Assert.assertEquals("org.apache.hadoop.fs.azure.Wasb", configValue1);

        String configValue2 = new ObjectMapper().readTree(result).findPath("hdfs-site").findPath("dfs.blocksize").textValue();
        Assert.assertEquals("134217728", configValue2);
    }

    @Test
    public void testAddConfigEntriesAddsConfigEntriesToExistingConfigBlockAndKeepsExistingEntries() throws Exception {
        String testBlueprint = FileReaderUtils.readFileFromClasspath("blueprints/test-bp-with-core-site.bp");
        List<BlueprintConfigurationEntry> configurationEntries = new ArrayList<>();
        configurationEntries.add(new BlueprintConfigurationEntry("core-site", "fs.AbstractFileSystem.wasb.impl", "org.apache.hadoop.fs.azure.Wasb"));
        configurationEntries.add(new BlueprintConfigurationEntry("core-site", "io.serializations", "org.apache.hadoop.io.serializer.WritableSerialization"));
        String result = underTest.addConfigEntries(testBlueprint, configurationEntries);

        String configValue1 = new ObjectMapper().readTree(result).findPath("core-site").findPath("fs.AbstractFileSystem.wasb.impl").textValue();
        Assert.assertEquals("org.apache.hadoop.fs.azure.Wasb", configValue1);

        String configValue2 = new ObjectMapper().readTree(result).findPath("core-site").findPath("io.serializations").textValue();
        Assert.assertEquals("org.apache.hadoop.io.serializer.WritableSerialization", configValue2);

        String configValue3 = new ObjectMapper().readTree(result).findPath("core-site").findPath("fs.trash.interval").textValue();
        Assert.assertEquals("360", configValue3);

        String configValue4 = new ObjectMapper().readTree(result).findPath("core-site").findPath("io.file.buffer.size").textValue();
        Assert.assertEquals("131072", configValue4);
    }

    @Test(expected = BlueprintProcessingException.class)
    public void testAddConfigEntriesThrowsExceptionIfBlueprintCannotBeParsed() throws Exception {
        String testBlueprint = FileReaderUtils.readFileFromClasspath("blueprints/test-bp-invalid.bp");
        List<BlueprintConfigurationEntry> configurationEntries = new ArrayList<>();
        underTest.addConfigEntries(testBlueprint, configurationEntries);
    }

    @Test
    public void testAddDefaultFsAddsCorrectEntry() throws Exception {
        String testBlueprint = FileReaderUtils.readFileFromClasspath("blueprints/test-bp-without-config-block.bp");
        String result = underTest.addDefaultFs(testBlueprint, "wasb://cloudbreak@dduihoab6jt1jl.cloudapp.net");

        String defaultFsValue = new ObjectMapper().readTree(result).findPath("core-site").findPath("fs.defaultFS").textValue();
        Assert.assertEquals("wasb://cloudbreak@dduihoab6jt1jl.cloudapp.net", defaultFsValue);
    }

    @Test
    public void testGetServicesInHostgroup() throws Exception {
        String testBlueprint = FileReaderUtils.readFileFromClasspath("blueprints/test-bp-without-config-block.bp");
        Set<String> result = underTest.getServicesInHostgroup(testBlueprint, "slave_1");

        Set<String> expected = new HashSet<>();
        expected.add("DATANODE");
        expected.add("HDFS_CLIENT");
        expected.add("NODEMANAGER");
        expected.add("YARN_CLIENT");
        expected.add("MAPREDUCE2_CLIENT");
        expected.add("ZOOKEEPER_CLIENT");

        Assert.assertEquals(expected, result);
    }

    @Test(expected = BlueprintProcessingException.class)
    public void testGetServicesInHostgroupThrowsExceptionIfBlueprintCannotBeParsed() throws Exception {
        String testBlueprint = FileReaderUtils.readFileFromClasspath("blueprints/test-bp-invalid.bp");
        underTest.getServicesInHostgroup(testBlueprint, "slave_1");
    }

}