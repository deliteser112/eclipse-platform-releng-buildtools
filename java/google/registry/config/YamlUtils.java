// Copyright 2017 The Nomulus Authors. All Rights Reserved.
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

import static com.google.common.base.Ascii.toLowerCase;
import static google.registry.util.FormattingLogger.getLoggerForCallerClass;
import static google.registry.util.ResourceUtils.readResourceUtf8;

import com.google.common.base.Optional;
import google.registry.util.FormattingLogger;
import java.util.Map;
import org.yaml.snakeyaml.Yaml;

/**
 * Utility methods for dealing with YAML.
 *
 * <p>There are always two YAML configuration files that are used: the {@code default-config.yaml}
 * file, which contains default configuration for all environments, and the environment-specific
 * {@code nomulus-config-ENVIRONMENT.yaml} file, which contains overrides for the default values for
 * environment-specific settings such as the App Engine project ID. The environment-specific
 * configuration can be blank, but it must exist.
 */
public final class YamlUtils {

  private static final FormattingLogger logger = getLoggerForCallerClass();

  private static final String ENVIRONMENT_CONFIG_FORMAT = "files/nomulus-config-%s.yaml";
  private static final String YAML_CONFIG_PROD =
      readResourceUtf8(RegistryConfig.class, "files/default-config.yaml");

  /**
   * Loads the {@link RegistryConfigSettings} POJO from the YAML configuration files.
   *
   * <p>The {@code default-config.yaml} file in this directory is loaded first, and a fatal error is
   * thrown if it cannot be found or if there is an error parsing it. Separately, the
   * environment-specific config file named {@code nomulus-config-ENVIRONMENT.yaml} is also loaded
   * and those values merged into the POJO.
   *
   * @throws IllegalStateException if the configuration files don't exist or are invalid
   */
  static RegistryConfigSettings getConfigSettings() {
    String configFilePath =
        String.format(ENVIRONMENT_CONFIG_FORMAT, toLowerCase(RegistryEnvironment.get().name()));
    String customYaml = readResourceUtf8(RegistryConfig.class, configFilePath);
    try {
      String mergedYaml = mergeYaml(YAML_CONFIG_PROD, customYaml);
      return new Yaml().loadAs(mergedYaml, RegistryConfigSettings.class);
    } catch (Exception e) {
      throw new IllegalStateException(
          "Fatal error: Environment configuration YAML file is invalid", e);
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
   * <p>Only maps are handled recursively; lists are simply overridden in place as-is, as are maps
   * whose name is suffixed with "Map" -- this allows entire maps to be overridden, rather than
   * merged.
   */
  static String mergeYaml(String defaultYaml, String customYaml) {
    Yaml yaml = new Yaml();
    Map<String, Object> yamlMap = loadAsMap(yaml, defaultYaml).get();
    Optional<Map<String, Object>> customMap = loadAsMap(yaml, customYaml);
    if (customMap.isPresent()) {
      yamlMap = mergeMaps(yamlMap, customMap.get());
      logger.infofmt("Successfully loaded environment configuration YAML file.");
    } else {
      logger.infofmt("Ignoring empty environment configuration YAML file.");
    }
    return yaml.dump(yamlMap);
  }

  /**
   * Recursively merges a custom map into a default map, and returns the merged result.
   *
   * <p>All keys in the default map that are also specified in the custom map are overridden with
   * the custom map's value. This runs recursively on all contained maps.
   */
  @SuppressWarnings("unchecked")
  private static Map<String, Object> mergeMaps(
      Map<String, Object> defaultMap, Map<String, Object> customMap) {
    for (String key : defaultMap.keySet()) {
      if (!customMap.containsKey(key)) {
        continue;
      }
      Object newValue;
      if (defaultMap.get(key) instanceof Map && !key.endsWith("Map")) {
        newValue =
            mergeMaps(
                (Map<String, Object>) defaultMap.get(key),
                (Map<String, Object>) customMap.get(key));
      } else {
        newValue = customMap.get(key);
      }
      defaultMap.put(key, newValue);
    }
    return defaultMap;
  }

  /**
   * Returns a structured map loaded from a YAML config string.
   *
   * <p>If the YAML string is empty or does not contain any data (e.g. it's only comments), then
   * absent is returned.
   */
  @SuppressWarnings("unchecked")
  private static Optional<Map<String, Object>> loadAsMap(Yaml yaml, String yamlString) {
    return Optional.fromNullable((Map<String, Object>) yaml.load(yamlString));
  }

  private YamlUtils() {}
}
