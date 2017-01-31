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

import static google.registry.model.registrar.Registrar.State.ACTIVE;
import static google.registry.testing.DatastoreHelper.createTld;
import static google.registry.testing.DatastoreHelper.persistResource;

import com.beust.jcommander.ParameterException;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import google.registry.flows.EppException;
import google.registry.flows.TransportCredentials.BadRegistrarPasswordException;
import google.registry.model.registrar.Registrar;
import google.registry.testing.CertificateSamples;
import google.registry.util.CidrAddressBlock;
import org.junit.Before;
import org.junit.Test;

/** Unit tests for {@link ValidateLoginCredentialsCommand}. */
public class ValidateLoginCredentialsCommandTest
    extends CommandTestCase<ValidateLoginCredentialsCommand> {

  private static final String PASSWORD = "foo-BAR2";
  private static final String CERT_HASH = CertificateSamples.SAMPLE_CERT_HASH;
  private static final String CLIENT_IP = "1.2.3.4";

  @Before
  public void init() {
    createTld("tld");
    persistResource(Registrar.loadByClientId("NewRegistrar").asBuilder()
        .setPassword(PASSWORD)
        .setClientCertificateHash(CERT_HASH)
        .setIpAddressWhitelist(ImmutableList.of(new CidrAddressBlock(CLIENT_IP)))
        .setState(ACTIVE)
        .setAllowedTlds(ImmutableSet.of("tld"))
        .build());
  }

  @Test
  public void testSuccess() throws Exception {
    runCommand(
        "--client=NewRegistrar",
        "--password=" + PASSWORD,
        "--cert_hash=" + CERT_HASH,
        "--ip_address=" + CLIENT_IP);
  }

  @Test
  public void testFailure_loginWithBadPassword() throws Exception {
    thrown.expect(BadRegistrarPasswordException.class);
    runCommand(
        "--client=NewRegistrar",
        "--password=" + new StringBuffer(PASSWORD).reverse(),
        "--cert_hash=" + CERT_HASH,
        "--ip_address=" + CLIENT_IP);
  }

  @Test
  public void testFailure_loginWithBadCertificateHash() throws Exception {
    thrown.expect(EppException.class);
    runCommand(
        "--client=NewRegistrar",
        "--password=" + PASSWORD,
        "--cert_hash=" + new StringBuffer(CERT_HASH).reverse(),
        "--ip_address=" + CLIENT_IP);
  }

  @Test
  public void testFailure_loginWithBadIp() throws Exception {
    thrown.expect(EppException.class);
    runCommand(
        "--client=NewRegistrar",
        "--password=" + PASSWORD,
        "--cert_hash=" + CERT_HASH,
        "--ip_address=" + new StringBuffer(CLIENT_IP).reverse());
  }

  @Test
  public void testFailure_missingClientId() throws Exception {
    thrown.expect(ParameterException.class);
    runCommand(
        "--password=" + PASSWORD,
        "--cert_hash=" + CERT_HASH,
        "--ip_address=" + CLIENT_IP);
  }

  @Test
  public void testFailure_missingPassword() throws Exception {
    thrown.expect(ParameterException.class);
    runCommand(
        "--client=NewRegistrar",
        "--cert_hash=" + CERT_HASH,
        "--ip_address=" + CLIENT_IP);
  }

  @Test
  public void testFailure_unknownFlag() throws Exception {
    thrown.expect(ParameterException.class);
    runCommand(
        "--client=NewRegistrar",
        "--password=" + PASSWORD,
        "--cert_hash=" + CERT_HASH,
        "--ip_address=" + CLIENT_IP,
        "--unrecognized_flag=foo");
  }

  @Test
  public void testFailure_certHashAndCertFile() throws Exception {
    thrown.expect(IllegalArgumentException.class);
    runCommand(
        "--client=NewRegistrar",
        "--password=" + PASSWORD,
        "--cert_hash=" + CERT_HASH,
        "--cert_file=" + tmpDir.newFile(),
        "--ip_address=" + CLIENT_IP);
  }
}
