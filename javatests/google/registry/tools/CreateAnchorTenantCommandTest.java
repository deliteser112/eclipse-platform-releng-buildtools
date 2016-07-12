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

package google.registry.tools;

import static google.registry.testing.DatastoreHelper.createTld;
import static google.registry.testing.DatastoreHelper.persistPremiumList;
import static google.registry.testing.DatastoreHelper.persistResource;

import com.beust.jcommander.ParameterException;
import google.registry.model.registry.Registry;
import org.junit.Before;
import org.junit.Test;

/** Unit tests for {@link CreateAnchorTenantCommand}. */
public class CreateAnchorTenantCommandTest
    extends EppToolCommandTestCase<CreateAnchorTenantCommand> {

  @Before
  public void initCommand() {
    command.passwordGenerator = new FakePasswordGenerator("abcdefghijklmnopqrstuvwxyz");
  }

  @Test
  public void testSuccess() throws Exception {
    runCommandForced("--client=NewRegistrar", "--superuser",
        "--reason=anchor-tenant-test", "--contact=jd1234", "--domain_name=example.tld");
    eppVerifier().asSuperuser().verifySent("domain_create_anchor_tenant.xml");
  }

  @Test
  public void testSuccess_suppliedPassword() throws Exception {
    runCommandForced("--client=NewRegistrar", "--superuser", "--password=foo",
        "--reason=anchor-tenant-test", "--contact=jd1234", "--domain_name=example.tld");
    eppVerifier().asSuperuser().verifySent("domain_create_anchor_tenant_password.xml");
  }

  @Test
  public void testSuccess_multipleWordReason() throws Exception {
    runCommandForced("--client=NewRegistrar", "--superuser",
        "--reason=\"anchor tenant test\"", "--contact=jd1234", "--domain_name=example.tld");
    eppVerifier().asSuperuser().verifySent("domain_create_anchor_tenant_multiple_word_reason.xml");
  }

  @Test
  public void testSuccess_noReason() throws Exception {
    runCommandForced("--client=NewRegistrar", "--superuser",
        "--contact=jd1234", "--domain_name=example.tld");
    eppVerifier().asSuperuser().verifySent("domain_create_anchor_tenant_no_reason.xml");
  }

  @Test
  public void testSuccess_feeStandard() throws Exception {
    runCommandForced("--client=NewRegistrar", "--superuser", "--fee",
        "--reason=anchor-tenant-test", "--contact=jd1234", "--domain_name=example.tld");
    eppVerifier().asSuperuser().verifySent("domain_create_anchor_tenant_fee_standard.xml");
  }

  @Test
  public void testSuccess_feePremium() throws Exception {
    createTld("tld");
    persistResource(
        Registry.get("tld")
            .asBuilder()
            .setPremiumList(persistPremiumList("tld", "premium,JPY 20000"))
            .build());
    runCommandForced("--client=NewRegistrar", "--superuser", "--fee",
        "--reason=anchor-tenant-test", "--contact=jd1234", "--domain_name=premium.tld");
    eppVerifier().asSuperuser().verifySent("domain_create_anchor_tenant_fee_premium.xml");
  }

  @Test
  public void testFailure_mainParameter() throws Exception {
    thrown.expect(ParameterException.class);
    runCommandForced("--tld=tld", "--client=NewRegistrar", "--superuser",
        "--reason=anchor-tenant-test", "--contact=jd1234", "--domain_name=example.tld", "foo");
  }

  @Test
  public void testFailure_missingClientId() throws Exception {
    thrown.expect(ParameterException.class);
    runCommandForced("--superuser",
        "--reason=anchor-tenant-test", "--contact=jd1234", "--domain_name=example.tld");
  }

  @Test
  public void testFailure_unknownFlag() throws Exception {
    thrown.expect(ParameterException.class);
    runCommandForced("--foo=bar", "--client=NewRegistrar", "--superuser",
        "--reason=anchor-tenant-test", "--contact=jd1234", "--domain_name=example.tld");
  }

  @Test
  public void testFailure_missingDomainName() throws Exception {
    thrown.expect(ParameterException.class);
    runCommandForced("--client=NewRegistrar", "--superuser",
        "--reason=anchor-tenant-test", "--contact=jd1234", "foo");
  }

  @Test
  public void testFailure_missingContact() throws Exception {
    thrown.expect(ParameterException.class);
    runCommandForced("--client=NewRegistrar", "--superuser",
        "--reason=anchor-tenant-test", "--domain_name=example.tld", "foo");
  }

  @Test
  public void testFailure_notAsSuperuser() throws Exception {
    thrown.expect(IllegalArgumentException.class);
    runCommandForced("--client=NewRegistrar",
        "--reason=anchor-tenant-test", "--contact=jd1234", "--domain_name=example.tld");
  }
}
