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

package google.registry.testing;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import com.google.common.base.Optional;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import org.junit.rules.ExternalResource;

/**
 * JUnit Rule for overriding the values Java system properties during tests.
 */
public final class SystemPropertyRule extends ExternalResource {

  /** Class representing a system property key value pair. */
  private static class Property {
    final String key;
    final Optional<String> value;

    Property(String key, Optional<String> value) {
      this.key = checkNotNull(key, "key");
      this.value = checkNotNull(value, "value");
    }

    void set() {
      if (value.isPresent()) {
        System.setProperty(key, value.get());
      } else {
        System.clearProperty(key);
      }
    }
  }

  private boolean isRunning = false;
  private final List<Property> pendings = new ArrayList<>();
  private final Map<String, Property> originals = new HashMap<>();

  /**
   * Change the value of a system property which is restored to its original value after the test.
   *
   * <p>It's safe to call this method multiple times with the same {@code key} within a single
   * test. Only the truly original property value will be restored at the end.
   *
   * <p>This method can be called fluently when declaring the Rule field, or within a Test method.
   *
   * @see java.lang.System#setProperty(String, String)
   */
  public SystemPropertyRule override(String key, @Nullable String value) {
    if (!originals.containsKey(key)) {
      originals.put(key, new Property(key, Optional.fromNullable(System.getProperty(key))));
    }
    Property property = new Property(key, Optional.fromNullable(value));
    if (isRunning) {
      property.set();
    } else {
      pendings.add(property);
    }
    return this;
  }

  @Override
  protected void before() throws Exception {
    checkState(!isRunning);
    for (Property pending : pendings) {
      pending.set();
    }
    isRunning = true;
  }

  @Override
  protected void after() {
    for (Property original : originals.values()) {
      original.set();
    }
  }
}
