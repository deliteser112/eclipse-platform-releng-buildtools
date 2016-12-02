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

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import javax.annotation.Nullable;

  /**
   * AutoValue class describing an RDAP Notice object.
   *
   * <p>This is used for injecting RDAP help pages.
   */
  @AutoValue
  public abstract class RdapNoticeDescriptor {
    public static Builder builder() {
      return new AutoValue_RdapNoticeDescriptor.Builder();
    }

    @Nullable public abstract String getTitle();
    public abstract ImmutableList<String> getDescription();
    @Nullable public abstract String getTypeString();
    @Nullable public abstract String getLinkValueSuffix();
    @Nullable public abstract String getLinkHrefUrlString();

    /** Builder class for {@link RdapNoticeDescriptor}. */
    @AutoValue.Builder
    public abstract static class Builder {
      public abstract Builder setTitle(@Nullable String title);
      public abstract Builder setDescription(Iterable<String> description);
      public abstract Builder setTypeString(@Nullable String typeString);
      public abstract Builder setLinkValueSuffix(@Nullable String linkValueSuffix);
      public abstract Builder setLinkHrefUrlString(@Nullable String linkHrefUrlString);

      public abstract RdapNoticeDescriptor build();
    }
  }

