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

package google.registry.reporting;

import static com.google.common.net.MediaType.CSV_UTF_8;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.api.client.http.ByteArrayContent;
import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpHeaders;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpResponse;
import com.google.api.client.http.HttpTransport;
import com.google.common.io.ByteStreams;
import google.registry.config.RegistryConfig.Config;
import google.registry.keyring.api.KeyModule.Key;
import google.registry.reporting.IcannReportingModule.ReportType;
import google.registry.request.HttpException.InternalServerErrorException;
import google.registry.util.FormattingLogger;
import google.registry.xjc.XjcXmlTransformer;
import google.registry.xjc.iirdea.XjcIirdeaResponseElement;
import google.registry.xjc.iirdea.XjcIirdeaResult;
import google.registry.xml.XmlException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import javax.inject.Inject;

/**
 * Class that uploads a CSV file to ICANN's endpoint via an HTTP PUT call.
 *
 * <p> It uses basic authorization credentials as specified in the "Registry Interfaces" draft.
 *
 * @see IcannReportingUploadAction
 * @see <a href=https://tools.ietf.org/html/draft-lozano-icann-registry-interfaces-07#page-9>
 *   ICANN Reporting Specification</a>
 */
public class IcannHttpReporter {

  private static final FormattingLogger logger = FormattingLogger.getLoggerForCallerClass();

  @Inject HttpTransport httpTransport;
  @Inject @Key("icannReportingPassword") String password;
  @Inject @Config("icannTransactionsReportingUploadUrl") String icannTransactionsUrl;
  @Inject @Config("icannActivityReportingUploadUrl") String icannActivityUrl;
  @Inject IcannHttpReporter() {}

  /** Uploads {@code reportBytes} to ICANN. */
  public void send(
      byte[] reportBytes,
      String tld,
      String yearMonth,
      ReportType reportType) throws XmlException, IOException {
    GenericUrl uploadUrl = new GenericUrl(makeUrl(tld, yearMonth, reportType));
    HttpRequest request =
        httpTransport
            .createRequestFactory()
            .buildPutRequest(uploadUrl, new ByteArrayContent(CSV_UTF_8.toString(), reportBytes));

    HttpHeaders headers = request.getHeaders();
    headers.setBasicAuthentication(tld + "_ry", password);
    headers.setContentType(CSV_UTF_8.toString());
    request.setHeaders(headers);
    request.setFollowRedirects(false);

    HttpResponse response = null;
    logger.infofmt(
        "Sending %s report to %s with content length %s",
        reportType,
        uploadUrl.toString(),
        request.getContent().getLength());
    try {
      response = request.execute();
      byte[] content;
      try {
        content = ByteStreams.toByteArray(response.getContent());
      } finally {
        response.getContent().close();
      }
      logger.infofmt("Received response code %s", response.getStatusCode());
      logger.infofmt("Response content: %s", new String(content, UTF_8));
      XjcIirdeaResult result = parseResult(content);
      if (result.getCode().getValue() != 1000) {
        logger.warningfmt(
            "PUT rejected, status code %s:\n%s\n%s",
            result.getCode(),
            result.getMsg(),
            result.getDescription());
        throw new InternalServerErrorException(result.getMsg());
      }
    } finally {
      if (response != null) {
        response.disconnect();
      } else {
        logger.warningfmt(
            "Received null response from ICANN server at %s", uploadUrl.toString());
      }
    }
  }

  private XjcIirdeaResult parseResult(byte[] content) throws XmlException, IOException {
    XjcIirdeaResponseElement response =
        XjcXmlTransformer.unmarshal(
            XjcIirdeaResponseElement.class, new ByteArrayInputStream(content));
    XjcIirdeaResult result = response.getResult();
    return result;
  }

  private String makeUrl(String tld, String yearMonth, ReportType reportType) {
    String urlPrefix = getUrlPrefix(reportType);
    return String.format("%s/%s/%s", urlPrefix, tld, yearMonth);
  }

  private String getUrlPrefix(ReportType reportType) {
    switch (reportType) {
      case TRANSACTIONS:
        return icannTransactionsUrl;
      case ACTIVITY:
        return icannActivityUrl;
      default:
        throw new IllegalStateException(
            String.format(
                "Received invalid reportType! Expected ACTIVITY or TRANSACTIONS, got %s.",
                reportType));
    }
  }
}
