package com.amazon.situp;

import com.amazon.situp.model.configuration.PluginSetting;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class TestDataProvider {
    public static final String TEST_PLUGIN_NAME_1 = "test-plugin-1";
    public static final String TEST_PLUGIN_NAME_2 = "test-plugin-2";
    public static final Integer TEST_WORKERS = 5;
    public static final Integer TEST_DELAY = 3_000;
    public static final String VALID_MULTIPLE_PIPELINE_CONFIG_FILE = "src/test/resources/valid_multiple_pipeline_configuration.yml";
    public static final String CYCLE_MULTIPLE_PIPELINE_CONFIG_FILE = "src/test/resources/cyclic_multiple_pipeline_configuration.yml";
    public static final String INCORRECT_SOURCE_MULTIPLE_PIPELINE_CONFIG_FILE = "src/test/resources/incorrect_source_multiple_pipeline_configuration.yml";
    public static final String MISSING_NAME_MULTIPLE_PIPELINE_CONFIG_FILE = "src/test/resources/missing_name_multiple_pipeline_configuration.yml";
    public static final String MISSING_PIPELINE_MULTIPLE_PIPELINE_CONFIG_FILE = "src/test/resources/missing_pipeline_multiple_pipeline_configuration.yml";
    public static Set<String> VALID_MULTIPLE_PIPELINE_NAMES = new HashSet<>(Arrays.asList("test-pipeline-1",
            "test-pipeline-2", "test-pipeline-3"));
    public static PluginSetting VALID_PLUGIN_SETTING_1 = new PluginSetting(TEST_PLUGIN_NAME_1, validSettingsForPlugin());
    public static PluginSetting VALID_PLUGIN_SETTING_2 = new PluginSetting(TEST_PLUGIN_NAME_2, validSettingsForPlugin());

    private static Map<String, Object> validSettingsForPlugin() {
        final Map<String, Object> settingsMap = new HashMap<>();
        settingsMap.put("someProperty", "someValue");
        return settingsMap;
    }

    public static Map<String, Map<String, Object>> validSingleConfiguration() {
        final Map<String, Map<String, Object>> singleConfiguration = new HashMap<>();
        singleConfiguration.put(TEST_PLUGIN_NAME_1, validSettingsForPlugin());
        return singleConfiguration;
    }

    public static Map<String, Map<String, Object>> validMultipleConfiguration() {
        final Map<String, Map<String, Object>> multipleConfiguration = new HashMap<>();
        multipleConfiguration.put(TEST_PLUGIN_NAME_1, validSettingsForPlugin());
        multipleConfiguration.put(TEST_PLUGIN_NAME_2, validSettingsForPlugin());
        return multipleConfiguration;
    }

}