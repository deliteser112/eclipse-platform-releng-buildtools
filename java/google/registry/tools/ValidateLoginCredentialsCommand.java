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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Strings.isNullOrEmpty;
import static com.google.common.io.Resources.getResource;
import static google.registry.flows.EppXmlTransformer.unmarshal;
import static google.registry.tools.CommandUtilities.runFlow;
import static google.registry.util.X509Utils.getCertificateHash;
import static google.registry.util.X509Utils.loadCertificate;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.base.Optional;
import com.google.template.soy.SoyFileSet;
import com.google.template.soy.data.SoyMapData;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;

import google.registry.flows.FlowRunner;
import google.registry.flows.FlowRunner.CommitMode;
import google.registry.flows.FlowRunner.UserPrivileges;
import google.registry.flows.HttpSessionMetadata;
import google.registry.flows.TlsCredentials;
import google.registry.flows.session.LoginFlow;
import google.registry.model.eppcommon.Trid;
import google.registry.model.eppinput.EppInput;
import google.registry.tools.Command.GtechCommand;
import google.registry.tools.Command.RemoteApiCommand;
import google.registry.tools.params.PathParameter;
import google.registry.tools.soy.LoginSoyInfo;
import google.registry.util.BasicHttpSession;
import google.registry.util.SystemClock;

import java.nio.file.Files;
import java.nio.file.Path;

import javax.annotation.Nullable;

/** A command to execute an epp command. */
@Parameters(separators = " =", commandDescription = "Test registrar login credentials")
final class ValidateLoginCredentialsCommand implements RemoteApiCommand, GtechCommand {

  @Parameter(
      names = {"-c", "--client"},
      description = "Client identifier of the registrar to test",
      required = true)
  private String clientIdentifier;

  @Parameter(
      names = {"-p", "--password"},
      description = "Password of the registrar to test",
      required = true)
  private String password;

  @Nullable
  @Parameter(
      names = {"-f", "--cert_file"},
      description = "File containing the client certificate",
      validateWith = PathParameter.InputFile.class)
  private Path clientCertificatePath;

  @Nullable
  @Parameter(
      names = {"-h", "--cert_hash"},
      description = "Hash of the client certificate.")
  private String clientCertificateHash;

  @Parameter(
      names = {"-i", "--ip_address"},
      description = "Client ip address to pretend to use")
  private String clientIpAddress = "10.0.0.1";

  @Override
  public void run() throws Exception {
    checkArgument(clientCertificatePath == null || isNullOrEmpty(clientCertificateHash),
        "Can't specify both --cert_hash and --cert_file");
    if (clientCertificatePath != null) {
      String asciiCrt = new String(Files.readAllBytes(clientCertificatePath), US_ASCII);
      clientCertificateHash = getCertificateHash(loadCertificate(asciiCrt));
    }
    byte[] inputXmlBytes = SoyFileSet.builder()
        .add(getResource(LoginSoyInfo.class, LoginSoyInfo.getInstance().getFileName()))
        .build()
        .compileToTofu()
        .newRenderer(LoginSoyInfo.LOGIN)
        .setData(new SoyMapData("clientIdentifier", clientIdentifier, "password", password))
        .render()
        .getBytes(UTF_8);
    System.out.println(runFlow(
        new FlowRunner(
            LoginFlow.class,
            unmarshal(EppInput.class, inputXmlBytes),
            Trid.create(null),
            new HttpSessionMetadata(
                new TlsCredentials(
                    clientCertificateHash,
                    Optional.of(clientIpAddress),
                    "placeholder"),  // behave as if we have SNI on, since we're validating a cert
                new BasicHttpSession()),
            inputXmlBytes,
            null,
            new SystemClock()),
        CommitMode.DRY_RUN,
        UserPrivileges.NORMAL));
  }
}
