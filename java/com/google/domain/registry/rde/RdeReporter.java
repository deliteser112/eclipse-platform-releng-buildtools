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

package com.google.domain.registry.rde;

import static com.google.appengine.api.urlfetch.FetchOptions.Builder.validateCertificate;
import static com.google.appengine.api.urlfetch.HTTPMethod.PUT;
import static com.google.common.io.BaseEncoding.base64;
import static com.google.common.net.HttpHeaders.AUTHORIZATION;
import static com.google.common.net.HttpHeaders.CONTENT_TYPE;
import static com.google.domain.registry.util.DomainNameUtils.canonicalizeDomainName;
import static java.nio.charset.StandardCharsets.UTF_8;
import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST;
import static javax.servlet.http.HttpServletResponse.SC_OK;

import com.google.appengine.api.urlfetch.HTTPHeader;
import com.google.appengine.api.urlfetch.HTTPRequest;
import com.google.appengine.api.urlfetch.HTTPResponse;
import com.google.appengine.api.urlfetch.URLFetchService;
import com.google.domain.registry.config.ConfigModule.Config;
import com.google.domain.registry.config.RegistryConfig;
import com.google.domain.registry.keyring.api.KeyModule.Key;
import com.google.domain.registry.request.HttpException.InternalServerErrorException;
import com.google.domain.registry.util.FormattingLogger;
import com.google.domain.registry.util.UrlFetchException;
import com.google.domain.registry.xjc.XjcXmlTransformer;
import com.google.domain.registry.xjc.iirdea.XjcIirdeaResponseElement;
import com.google.domain.registry.xjc.iirdea.XjcIirdeaResult;
import com.google.domain.registry.xjc.rdeheader.XjcRdeHeader;
import com.google.domain.registry.xjc.rdereport.XjcRdeReportReport;
import com.google.domain.registry.xml.XmlException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;

import javax.inject.Inject;

/**
 * Class that uploads a decrypted XML deposit report to ICANN's webserver.
 *
 * @see RdeReportAction
 */
public class RdeReporter {

  private static final FormattingLogger logger = FormattingLogger.getLoggerForCallerClass();

  /** @see "http://tools.ietf.org/html/draft-lozano-icann-registry-interfaces-05#section-4" */
  private static final String REPORT_MIME = "text/xml";

  @Inject RegistryConfig config;
  @Inject URLFetchService urlFetchService;
  @Inject @Config("rdeReportUrlPrefix") String reportUrlPrefix;
  @Inject @Key("icannReportingPassword") String password;
  @Inject RdeReporter() {}

  /** Uploads {@code reportBytes} to ICANN. */
  public void send(byte[] reportBytes) throws IOException, XmlException {
    XjcRdeReportReport report = XjcXmlTransformer.unmarshal(new ByteArrayInputStream(reportBytes));
    XjcRdeHeader header = report.getHeader().getValue();

    // Send a PUT request to ICANN's HTTPS server.
    URL url = makeReportUrl(header.getTld(), report.getId());
    String username = header.getTld() + "_ry";
    String token = base64().encode(String.format("%s:%s", username, password).getBytes(UTF_8));
    HTTPRequest req = new HTTPRequest(url, PUT, validateCertificate().setDeadline(60d));
    req.addHeader(new HTTPHeader(CONTENT_TYPE, REPORT_MIME));
    req.addHeader(new HTTPHeader(AUTHORIZATION, "Basic " + token));
    req.setPayload(reportBytes);
    logger.infofmt("Sending report:\n%s", new String(reportBytes, UTF_8));
    HTTPResponse rsp = urlFetchService.fetch(req);
    switch (rsp.getResponseCode()) {
      case SC_OK:
      case SC_BAD_REQUEST:
        break;
      default:
        throw new UrlFetchException("PUT failed", req, rsp);
    }

    // Ensure the XML response is valid.
    XjcIirdeaResult result = parseResult(rsp);
    if (result.getCode().getValue() != 1000) {
      logger.warningfmt("PUT rejected: %d %s\n%s",
          result.getCode().getValue(),
          result.getMsg(),
          result.getDescription());
      throw new InternalServerErrorException(result.getMsg());
    }
  }

  /**
   * Unmarshals IIRDEA XML result object from {@link HTTPResponse} payload.
   *
   * @see "http://tools.ietf.org/html/draft-lozano-icann-registry-interfaces-05#section-4.1"
   */
  private XjcIirdeaResult parseResult(HTTPResponse rsp) throws XmlException {
    byte[] responseBytes = rsp.getContent();
    logger.infofmt("Received response:\n%s", new String(responseBytes, UTF_8));
    XjcIirdeaResponseElement response =
        XjcXmlTransformer.unmarshal(new ByteArrayInputStream(responseBytes));
    XjcIirdeaResult result = response.getResult();
    return result;
  }

  private URL makeReportUrl(String tld, String id) {
    try {
      return new URL(String.format("%s/%s/%s", reportUrlPrefix, canonicalizeDomainName(tld), id));
    } catch (MalformedURLException e) {
      throw new RuntimeException(e);
    }
  }
}
