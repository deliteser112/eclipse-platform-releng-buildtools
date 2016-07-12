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

import com.beust.jcommander.ParameterException;
import org.junit.Test;

/** Unit tests for {@link UpdateServerLocksCommand}. */
public class UpdateServerLocksCommandTest extends EppToolCommandTestCase<UpdateServerLocksCommand> {

  @Test
  public void testSuccess_applyOne() throws Exception {
    runCommandForced("--client=NewRegistrar", "--registrar_request=true", "--reason=Test",
        "--domain_name=example.tld", "--apply=serverRenewProhibited");
    eppVerifier().verifySent("update_server_locks_apply_one.xml");
  }

  @Test
  public void testSuccess_multipleWordReason() throws Exception {
    runCommandForced("--client=NewRegistrar", "--registrar_request=false",
        "--reason=\"Test this\"", "--domain_name=example.tld", "--apply=serverRenewProhibited");
    eppVerifier().verifySent("update_server_locks_multiple_word_reason.xml");
  }

  @Test
  public void testSuccess_removeOne() throws Exception {
    runCommandForced("--client=NewRegistrar", "--registrar_request=true", "--reason=Test",
        "--domain_name=example.tld", "--remove=serverRenewProhibited");
    eppVerifier().verifySent("update_server_locks_remove_one.xml");
  }

  @Test
  public void testSuccess_applyAll() throws Exception {
    runCommandForced("--client=NewRegistrar", "--registrar_request=true", "--reason=Test",
        "--domain_name=example.tld", "--apply=all");
    eppVerifier().verifySent("update_server_locks_apply_all.xml");
  }

  @Test
  public void testSuccess_removeAll() throws Exception {
    runCommandForced("--client=NewRegistrar", "--registrar_request=true", "--reason=Test",
        "--domain_name=example.tld", "--remove=all");
    eppVerifier().verifySent("update_server_locks_remove_all.xml");
  }

  @Test
  public void testFailure_applyAllRemoveOne_failsDueToOverlap() throws Exception {
    thrown.expect(IllegalArgumentException.class);
    runCommandForced("--client=NewRegistrar", "--registrar_request=true", "--reason=Test",
        "--domain_name=example.tld", "--apply=all", "--remove=serverRenewProhibited");
  }

  @Test
  public void testFailure_illegalStatus() throws Exception {
    // The EPP status is a valid one by RFC, but invalid for this command.
    thrown.expect(IllegalArgumentException.class);
    runCommandForced("--client=NewRegistrar", "--registrar_request=true", "--reason=Test",
        "--domain_name=example.tld", "--apply=clientRenewProhibited");
  }

  @Test
  public void testFailure_unrecognizedStatus() throws Exception {
    // Handles a status passed to the command that doesn't correspond to any
    // EPP-valid status.
    thrown.expect(IllegalArgumentException.class);
    runCommandForced("--client=NewRegistrar", "--registrar_request=true", "--reason=Test",
        "--domain_name=example.tld", "--apply=foo");
  }

  @Test
  public void testFailure_mainParameter() throws Exception {
    thrown.expect(ParameterException.class);
    runCommandForced("--client=NewRegistrar", "--registrar_request=true", "--reason=Test",
        "--domain_name=example.tld", "example2.tld", "--apply=serverRenewProhibited");
  }

  @Test
  public void testFailure_noOp() throws Exception {
    thrown.expect(IllegalArgumentException.class);
    runCommandForced("--client=NewRegistrar", "--domain_name=example.tld", "--apply=all",
        "--remove=serverRenewProhibited,serverTransferProhibited,"
            + "serverDeleteProhibited,serverUpdateProhibited,serverHold",
            "--registrar_request=true", "--reason=Test");
  }

  @Test
  public void testFailure_missingClientId() throws Exception {
    thrown.expect(ParameterException.class);
    runCommandForced("--domain_name=example.tld", "--registrar_request=true",
        "--apply=serverRenewProhibited", "--reason=Test");
  }

  @Test
  public void testFailure_unknownFlag() throws Exception {
    thrown.expect(ParameterException.class);
    runCommandForced("--client=NewRegistrar", "--registrar_request=true", "--reason=Test",
        "--domain_name=example.tld", "--apply=serverRenewProhibited", "--foo=bar");
  }

  @Test
  public void testFailure_noReasonWhenNotRegistrarRequested() throws Exception {
    thrown.expect(IllegalArgumentException.class);
    runCommandForced("--client=NewRegistrar", "--registrar_request=false",
        "--domain_name=example.tld", "--apply=serverRenewProhibited");
  }

  @Test
  public void testFailure_missingRegistrarRequest() throws Exception {
    thrown.expect(ParameterException.class);
    runCommandForced("--client=NewRegistrar", "--reason=Test",
        "--domain_name=example.tld", "--apply=serverRenewProhibited");
  }
}
