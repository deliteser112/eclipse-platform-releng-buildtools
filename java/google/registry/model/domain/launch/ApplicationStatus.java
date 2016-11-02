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

package google.registry.model.domain.launch;

import static com.google.common.base.CaseFormat.LOWER_CAMEL;
import static com.google.common.base.CaseFormat.UPPER_UNDERSCORE;

import google.registry.model.translators.EnumToAttributeAdapter;
import google.registry.model.translators.EnumToAttributeAdapter.EppEnum;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

/**
 * Represents the EPP application status.
 *
 * <p>These values are never read from a command and only used in responses, so, we don't need to
 * model anything we don't output. We don't model the CUSTOM status because we don't use it. This
 * allows us to also avoid modeling the "name" attribute which is only used with CUSTOM. We don't
 * model the "lang" attribute because we only support English and that's the default.
 *
 * <p>Given all of this, we can use {@link EnumToAttributeAdapter} to make this code very simple.
 *
 * @see <a href="http://tools.ietf.org/html/draft-tan-epp-launchphase-11#section-2.3">
 *     Launch Phase Mapping for EPP - Status Values</a>
 */
@XmlJavaTypeAdapter(EnumToAttributeAdapter.class)
public enum ApplicationStatus implements EppEnum {
  ALLOCATED,
  INVALID,
  PENDING_ALLOCATION,
  PENDING_VALIDATION,
  REJECTED,
  VALIDATED;

  @Override
  public String getXmlName() {
    return UPPER_UNDERSCORE.to(LOWER_CAMEL, name());
  }

  /**
   * Returns true if this status is a final status - that is, it should not transition to any other
   * application status after this one.
   */
  public boolean isFinalStatus() {
    return ALLOCATED.equals(this) || REJECTED.equals(this);
  }
}
