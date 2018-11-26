// Copyright 2018 The Nomulus Authors. All Rights Reserved.
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

package google.registry.tools.server;

import static google.registry.request.Action.Method.GET;
import static java.nio.charset.StandardCharsets.UTF_8;
import static javax.servlet.http.HttpServletResponse.SC_INTERNAL_SERVER_ERROR;

import com.google.common.flogger.FluentLogger;
import com.google.common.io.BaseEncoding;
import com.google.common.net.MediaType;
import google.registry.keyring.kms.KmsKeyring.StringKeyLabel;
import google.registry.request.Action;
import google.registry.request.Response;
import google.registry.request.auth.Auth;
import java.util.function.Function;
import javax.inject.Inject;
import javax.inject.Named;

/**
 * An action that returns KMS encrypted service account credential in its payload.
 *
 * <p>This credential can be stored locally, and a {@code RemoteApiOptions} can use it to initialize
 * an {@code AppEngineConnection}.
 */
@Action(
    path = DownloadServiceAccountCredentialAction.PATH,
    method = {GET},
    auth = Auth.AUTH_INTERNAL_OR_ADMIN)
public class DownloadServiceAccountCredentialAction implements Runnable {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  public static final String PATH = "/_dr/admin/downloadCredential";

  @Inject
  @Named("encryptedDataRetriever")
  Function<String, String> encryptedDataRetriever;

  @Inject Response response;

  @Inject
  DownloadServiceAccountCredentialAction() {}

  @Override
  public void run() {
    try {
      String encryptedJsonCredential =
          encryptedDataRetriever.apply(StringKeyLabel.JSON_CREDENTIAL_STRING.getLabel());
      response.setContentType(MediaType.APPLICATION_BINARY);
      response.setPayload(BaseEncoding.base64().encode(encryptedJsonCredential.getBytes(UTF_8)));
    } catch (Exception e) {
      logger.atSevere().withCause(e).log("Cannot retrieve encrypted service account credential.");
      response.setPayload(e.getMessage());
      response.setStatus(SC_INTERNAL_SERVER_ERROR);
    }
  }
}
