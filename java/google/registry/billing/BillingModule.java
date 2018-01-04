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

package google.registry.billing;

import static google.registry.request.RequestParameters.extractRequiredParameter;

import com.google.api.client.googleapis.extensions.appengine.auth.oauth2.AppIdentityCredential;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.services.dataflow.Dataflow;
import com.google.common.collect.ImmutableSet;
import dagger.Module;
import dagger.Provides;
import google.registry.config.RegistryConfig.Config;
import google.registry.request.Parameter;
import java.util.Set;
import java.util.function.Function;
import javax.servlet.http.HttpServletRequest;

/** Module for dependencies required by monthly billing actions. */
@Module
public final class BillingModule {

  public static final String DETAIL_REPORT_PREFIX = "invoice_details";
  public static final String OVERALL_INVOICE_PREFIX = "CRR-INV";

  static final String PARAM_JOB_ID = "jobId";
  static final String PARAM_DIRECTORY_PREFIX = "directoryPrefix";
  static final String BILLING_QUEUE = "billing";
  static final String CRON_QUEUE = "retryable-cron-tasks";
  // TODO(larryruili): Replace with invoices/yyyy-MM after verifying 2017-12 invoice.
  static final String RESULTS_DIRECTORY_PREFIX = "results/";

  private static final String CLOUD_PLATFORM_SCOPE =
      "https://www.googleapis.com/auth/cloud-platform";

  /** Provides the invoicing Dataflow jobId enqueued by {@link GenerateInvoicesAction}. */
  @Provides
  @Parameter(PARAM_JOB_ID)
  static String provideJobId(HttpServletRequest req) {
    return extractRequiredParameter(req, PARAM_JOB_ID);
  }

  /** Provides the subdirectory under a GCS bucket that we copy detail reports from. */
  @Provides
  @Parameter(PARAM_DIRECTORY_PREFIX)
  static String provideDirectoryPrefix(HttpServletRequest req) {
    return extractRequiredParameter(req, PARAM_DIRECTORY_PREFIX);
  }

  /** Constructs a {@link Dataflow} API client with default settings. */
  @Provides
  static Dataflow provideDataflow(
      @Config("projectId") String projectId,
      HttpTransport transport,
      JsonFactory jsonFactory,
      Function<Set<String>, AppIdentityCredential> appIdentityCredentialFunc) {

    return new Dataflow.Builder(
            transport,
            jsonFactory,
            appIdentityCredentialFunc.apply(ImmutableSet.of(CLOUD_PLATFORM_SCOPE)))
        .setApplicationName(String.format("%s billing", projectId))
        .build();
  }
}
