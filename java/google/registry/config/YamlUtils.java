// Copyright 2016 The Nomulus Authors. All Rights Reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package google.registry.config;

import static google.registry.config.RegistryEnvironment.UNITTEST;
import static google.registry.util.FormattingLogger.getLoggerForCallerClass;
import static google.registry.util.ResourceUtils.readResourceUtf8;

import com.google.common.io.CharStreams;
import google.registry.util.FormattingLogger;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Map;
import org.yaml.snakeyaml.Yaml;

/** Utility methods for dealing with YAML. */
public final class YamlUtils {

  private static final FormattingLogger logger = getLoggerForCallerClass();

  private static final String CUSTOM_CONFIG_PATH = "WEB-INF/nomulus-config.yaml";
  private static final String YAML_CONFIG_PROD =
      readResourceUtf8(RegistryConfig.class, "default-config.yaml");
  private static final String YAML_CONFIG_UNITTEST =
      readResourceUtf8(RegistryConfig.class, "unittest-config.yaml");

  /**
   * Loads the {@link RegistryConfigSettings} POJO from the YAML configuration file(s).
   *
   * <p>The {@code default-config.yaml} file in this directory is loaded first, and a fatal error is
   * thrown if it cannot be found or if there is an error parsing it. Separately, the custom config
   * file located in {@code WEB-INF/nomulus-config.yaml} is also loaded and those values merged into
   * the POJO. If the custom config file does not exist then an info notice is logged, but if it
   * does exist and is invalid then a fatal error is thrown.
   *
   * <p>Unit tests load the {@code unittest-config.yaml} file for custom config.
   */
  static RegistryConfigSettings getConfigSettings() {
    String yaml = YAML_CONFIG_PROD;
    if (RegistryEnvironment.get() == UNITTEST) {
      yaml = mergeYaml(yaml, YAML_CONFIG_UNITTEST);
    } else {
      try {
        // We have to load the file this way because App Engine does not allow loading files in the
        // WEB-INF directory using a class loader.
        FileInputStream fin = new FileInputStream(new File(CUSTOM_CONFIG_PATH));
        String customYaml = CharStreams.toString(new InputStreamReader(fin, "UTF-8"));
        yaml = mergeYaml(yaml, customYaml);
      } catch (IOException e) {
        logger.warningfmt(
            "There was no custom configuration file to load at %s", CUSTOM_CONFIG_PATH);
      }
    }
    try {
      return new Yaml().loadAs(yaml, RegistryConfigSettings.class);
    } catch (Exception e) {
      throw new IllegalStateException("Fatal error: Custom YAML configuration file is invalid", e);
    }
  }

  /**
   * Recursively merges two YAML documents together.
   *
   * <p>Any fields that are specified in customYaml will override fields of the same path in
   * defaultYaml. Additional fields in customYaml that aren't specified in defaultYaml will be
   * ignored. The schemas of all fields that are present must be identical, e.g. it is an error to
   * override a field that has a Map value in the default YAML with a field of any other type in the
   * custom YAML.
   *
   * <p>Only maps are handled recursively; lists are simply overridden in place as-is.
   */
  static String mergeYaml(String defaultYaml, String customYaml) {
    try {
      Yaml yaml = new Yaml();
      Map<String, Object> defaultObj = loadAsMap(yaml, defaultYaml);
      Map<String, Object> customObj = loadAsMap(yaml, customYaml);
      Object mergedObj = mergeMaps(defaultObj, customObj);
      logger.infofmt("Successfully loaded custom YAML configuration file.");
      return yaml.dump(mergedObj);
    } catch (Exception e) {
      throw new IllegalStateException("Fatal error: Custom YAML configuration file is invalid", e);
    }
  }

  private static Object mergeMaps(Map<String, Object> defaultMap, Map<String, Object> customMap) {
    for (String key : defaultMap.keySet()) {
      if (!customMap.containsKey(key)) {
        continue;
      }
      Object defaultValue = defaultMap.get(key);
      @SuppressWarnings("unchecked")
      Object newValue =
          (defaultValue instanceof Map)
              ? mergeMaps(
                  (Map<String, Object>) defaultValue, (Map<String, Object>) customMap.get(key))
              : customMap.get(key);
      defaultMap.put(key, newValue);
    }
    return defaultMap;
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> loadAsMap(Yaml yaml, String yamlString) {
    return (Map<String, Object>) yaml.load(yamlString);
  }

  private YamlUtils() {}
}
