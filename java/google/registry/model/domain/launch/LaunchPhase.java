// Copyright 2016 The Domain Registry Authors. All Rights Reserved.
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

package google.registry.model.domain.launch;

import static com.google.common.base.CaseFormat.LOWER_CAMEL;
import static com.google.common.base.CaseFormat.UPPER_UNDERSCORE;
import static google.registry.util.TypeUtils.getTypesafeEnumMapping;
import static java.util.Objects.hash;

import com.google.common.collect.ImmutableMap;
import com.googlecode.objectify.annotation.Embed;
import google.registry.model.ImmutableObject;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlValue;

/**
 * The launch phase of the TLD being addressed by this command.
 *
 * <p>The launch phase refers to the various stages that a TLD goes through before entering general
 * availability. The various phases are described below (in order that they usually occur).
 */
@Embed
public class LaunchPhase extends ImmutableObject {

  /**
   * The phase during which trademark holders can submit registrations or applications with
   * trademark information that can be validated by the server.
   */
  public static final LaunchPhase SUNRISE = create("sunrise", null);

  /**
   * A post-Sunrise phase when non-trademark holders are allowed to register domain names with steps
   * taken to address a large volume of initial registrations.
   */
  public static final LaunchPhase LANDRUSH = create("landrush", null);

  /** A combined sunrise/landrush phase. */
  public static final LaunchPhase SUNRUSH = create("sunrise", "landrush");

  /**
   * The Trademark Claims phase, as defined in the TMCH Functional Specification, in which a Claims
   * Notice must be displayed to a prospective registrant of a domain name that matches trademarks.
   */
  public static final LaunchPhase CLAIMS = create("claims", null);

  /** A post-launch phase that is also referred to as "steady state". */
  public static final LaunchPhase OPEN = create("open", null);

  /** A custom server launch phase that is defined using the "name" attribute. */
  public static final LaunchPhase CUSTOM = create("custom", null);

  private static final Map<String, LaunchPhase> LAUNCH_PHASES = initEnumMapping();

  /**
   * Returns a map of the static final fields to their values, case-converted.
   */
  private static final ImmutableMap<String, LaunchPhase> initEnumMapping() {
    ImmutableMap.Builder<String, LaunchPhase> builder = new ImmutableMap.Builder<>();
    for (Entry<String, LaunchPhase> entry : getTypesafeEnumMapping(LaunchPhase.class).entrySet()) {
      builder.put(UPPER_UNDERSCORE.to(LOWER_CAMEL, entry.getKey()), entry.getValue());
    }
    return builder.build();
  }

  /** Private create function for the typesafe enum pattern. */
  public static LaunchPhase create(String phase, String subphase) {
    LaunchPhase instance = new LaunchPhase();
    instance.phase = phase;
    instance.subphase = subphase;
    return instance;
  }

  @XmlValue
  String phase;

  /**
   * Holds the name of a custom phase if the main phase is "custom", or a sub-phase for all other
   * values.
   */
  @XmlAttribute(name = "name")
  String subphase;

  public String getPhase() {
    return phase;
  }

  public String getSubphase() {
    return subphase;
  }

  public static LaunchPhase fromValue(String value) {
    return LAUNCH_PHASES.get(value);
  }

  /** A special equals implementation that only considers the string value. */
  @Override
  public boolean equals(Object other) {
    return other instanceof LaunchPhase
        && Objects.equals(phase, ((LaunchPhase) other).phase)
        && Objects.equals(subphase, ((LaunchPhase) other).subphase);
  }

  /** A special hashCode implementation that only considers the string value. */
  @Override
  public int hashCode() {
    return hash(phase, subphase);
  }
}
