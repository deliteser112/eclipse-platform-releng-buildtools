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

package google.registry.tools;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.api.services.dns.Dns;
import com.google.api.services.dns.model.ManagedZone;
import java.io.IOException;
import java.security.GeneralSecurityException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class CreateCdnsTldTest extends CommandTestCase<CreateCdnsTld> {

  @Mock Dns dnsService;
  @Mock Dns.ManagedZones managedZones;
  @Mock Dns.ManagedZones.Create request;
  @Captor ArgumentCaptor<String> projectId;
  @Captor ArgumentCaptor<ManagedZone> requestBody;

  @Before
  public void setUp() throws Exception {
    when(dnsService.managedZones()).thenReturn(managedZones);
    when(managedZones.create(projectId.capture(), requestBody.capture())).thenReturn(request);
    command = new CreateCdnsTldForTest();
    command.projectId = "test-project";
  }

  /** Fake the command class so we can override createDnsService() */
  class CreateCdnsTldForTest extends CreateCdnsTld {
    @Override
    Dns createDnsService() throws IOException, GeneralSecurityException {
      return dnsService;
    }
  }

  @Test
  public void testBasicFunctionality() throws Exception {
    runCommand("--dns_name=tld.", "--name=tld", "--description=test run", "--force");
    verify(request).execute();
    assertThat(projectId.getValue()).isEqualTo("test-project");
    ManagedZone zone = requestBody.getValue();
    assertThat(zone.getNameServerSet()).isEqualTo("cloud-dns-registry-test");
    assertThat(zone.getDnsName()).isEqualTo("tld.");
    assertThat(zone.getName()).isEqualTo("tld");
  }

  @Test
  public void testNameDefault() throws Exception {
    runCommand("--dns_name=tld.", "--description=test run", "--force");
    ManagedZone zone = requestBody.getValue();
    assertThat(zone.getNameServerSet()).isEqualTo("cloud-dns-registry-test");
    assertThat(zone.getDnsName()).isEqualTo("tld.");
    assertThat(zone.getName()).isEqualTo("tld.");
  }
}
