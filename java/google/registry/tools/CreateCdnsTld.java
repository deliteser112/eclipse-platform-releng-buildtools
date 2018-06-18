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

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.dns.Dns;
import com.google.api.services.dns.model.ManagedZone;
import com.google.api.services.dns.model.ManagedZoneDnsSecConfig;
import com.google.common.annotations.VisibleForTesting;
import google.registry.config.RegistryConfig.Config;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import javax.inject.Inject;

@Parameters(separators = " =", commandDescription = "Create a Managed Zone for a TLD in Cloud DNS.")
class CreateCdnsTld extends ConfirmingCommand {

  @Parameter(names = "--description", description = "Description of the new TLD.")
  String description;

  @Parameter(
    names = "--dns_name",
    description = "DNS name of the new tld, including trailing period, e.g.: search.",
    required = true
  )
  String dnsName;

  @Nullable
  @Parameter(
    names = "--name",
    description = "Managed zone name.  If not specified, dns_name is used."
  )
  String name;

  @Inject
  @Config("projectId")
  String projectId;

  private static final String KEY_VALUE_FORMAT = "  %s = %s";

  private ManagedZone managedZone;

  @Override
  protected void init() {
    managedZone =
        new ManagedZone()
            .setDescription(description)
            .setNameServerSet(
                RegistryToolEnvironment.get() == RegistryToolEnvironment.PRODUCTION
                ? "cloud-dns-registry"
                : "cloud-dns-registry-test")
            .setDnsName(dnsName)
            .setName((name != null) ? name : dnsName)
            .setDnssecConfig(new ManagedZoneDnsSecConfig().setNonExistence("NSEC").setState("ON"));
  }

  @Override
  protected String prompt() {
    return String.format(
        "Creating TLD with:\n%s\n%s",
        String.format(KEY_VALUE_FORMAT, "projectId", projectId),
        managedZone
            .entrySet()
            .stream()
            .map(entry -> String.format(KEY_VALUE_FORMAT, entry.getKey(), entry.getValue()))
            .collect(Collectors.joining("\n")));
  }

  @Override
  public String execute() throws IOException, GeneralSecurityException {
    Dns dnsService = createDnsService();
    Dns.ManagedZones.Create request = dnsService.managedZones().create(projectId, managedZone);
    ManagedZone response = request.execute();
    return String.format("Created managed zone: %s", response);
  }

  @VisibleForTesting
  Dns createDnsService() throws IOException, GeneralSecurityException {
    // TODO(b/67367533): We should be obtaining the Dns instance from CloudDnsWriter module.  But
    // to do this cleanly we need to refactor everything down to the credential object.  Having
    // done that, this method will go away and this class will become final.
    HttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();
    JsonFactory jsonFactory = JacksonFactory.getDefaultInstance();

    GoogleCredential credential = GoogleCredential.getApplicationDefault();
    if (credential.createScopedRequired()) {
      credential =
          credential.createScoped(
              Arrays.asList(
                  "https://www.googleapis.com/auth/cloud-platform",
                  "https://www.googleapis.com/auth/cloud-platform.read-only",
                  "https://www.googleapis.com/auth/ndev.clouddns.readonly",
                  "https://www.googleapis.com/auth/ndev.clouddns.readwrite"));
    }

    Dns.Builder builder =
        new Dns.Builder(httpTransport, jsonFactory, credential).setApplicationName(projectId);
    if (RegistryToolEnvironment.get() != RegistryToolEnvironment.PRODUCTION) {
      builder
          .setRootUrl("https://staging-www.sandbox.googleapis.com")
          .setServicePath("dns/v2beta1_staging/projects/");
    }

    return builder.build();
  }
}
