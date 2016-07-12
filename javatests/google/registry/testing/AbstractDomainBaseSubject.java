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

package google.registry.testing;

import com.google.common.collect.ImmutableSet;
import com.google.common.truth.FailureStrategy;
import google.registry.model.domain.DomainBase;
import google.registry.model.domain.DomainResource;
import google.registry.model.domain.launch.LaunchNotice;
import google.registry.model.domain.secdns.DelegationSignerData;
import google.registry.model.eppcommon.AuthInfo;
import google.registry.testing.TruthChainer.And;
import java.util.Set;

/** Truth subject for asserting things about {@link DomainResource} instances. */
public abstract class AbstractDomainBaseSubject
    <T extends DomainBase, S extends AbstractDomainBaseSubject<T, S>>
    extends AbstractEppResourceSubject<T, S> {

  public AbstractDomainBaseSubject(FailureStrategy strategy, T subject) {
    super(strategy, subject);
  }

  public And<S> hasFullyQualifiedDomainName(String fullyQualifiedDomainName) {
    return hasValue(
        fullyQualifiedDomainName,
        getSubject().getFullyQualifiedDomainName(),
        "has fullyQualifiedDomainName");
  }

  public And<S> hasExactlyDsData(DelegationSignerData... dsData) {
    return hasExactlyDsData(ImmutableSet.copyOf(dsData));
  }

  public And<S> hasExactlyDsData(Set<DelegationSignerData> dsData) {
    return hasValue(dsData, getSubject().getDsData(), "has dsData");
  }

  public And<S> hasNumDsData(int num) {
    return hasValue(num, getSubject().getDsData().size(), "has num dsData");
  }

  public And<S> hasLaunchNotice(LaunchNotice launchNotice) {
    return hasValue(launchNotice, getSubject().getLaunchNotice(), "has launchNotice");
  }

  public And<S> hasAuthInfoPwd(String pw) {
    AuthInfo authInfo = getSubject().getAuthInfo();
    return hasValue(pw, authInfo == null ? null : authInfo.getPw().getValue(), "has auth info pw");
  }
}
