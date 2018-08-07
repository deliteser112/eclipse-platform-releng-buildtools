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

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Suppliers.memoize;
import static com.google.common.net.HttpHeaders.X_REQUESTED_WITH;
import static com.google.common.net.MediaType.JSON_UTF_8;
import static google.registry.security.JsonHttp.JSON_SAFETY_PREFIX;
import static google.registry.security.XsrfTokenManager.X_CSRF_TOKEN;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.api.client.http.ByteArrayContent;
import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpHeaders;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestFactory;
import com.google.api.client.http.HttpResponse;
import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.CharStreams;
import com.google.common.net.HostAndPort;
import com.google.common.net.MediaType;
import com.google.re2j.Matcher;
import com.google.re2j.Pattern;
import google.registry.security.XsrfTokenManager;
import google.registry.tools.ServerSideCommand.Connection;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Map;
import javax.annotation.Nullable;
import javax.inject.Inject;
import org.json.simple.JSONValue;

/** An http connection to the appengine server. */
class AppEngineConnection implements Connection {

  /** Pattern to heuristically extract title tag contents in HTML responses. */
  private static final Pattern HTML_TITLE_TAG_PATTERN = Pattern.compile("<title>(.*?)</title>");

  @Inject HttpRequestFactory requestFactory;
  @Inject AppEngineConnectionFlags flags;
  @Inject XsrfTokenManager xsrfTokenManager;

  @Inject
  AppEngineConnection() {}

  /**
   * Memoized XSRF security token.
   *
   * <p>Computing this is expensive since it needs to load {@code ServerSecret} so do it once.
   */
  private final Supplier<String> xsrfToken =
      memoize(() -> xsrfTokenManager.generateToken(getUserId()));

  @Override
  public void prefetchXsrfToken() {
    // Cause XSRF token to be fetched, and then stay resident in cache (since it's memoized).
    xsrfToken.get();
  }

  /** Returns the contents of the title tag in the given HTML, or null if not found. */
  private static String extractHtmlTitle(String html) {
    Matcher matcher = HTML_TITLE_TAG_PATTERN.matcher(html);
    return (matcher.find() ? matcher.group(1) : null);
  }

  /** Returns the HTML from the connection error stream, if any, otherwise the empty string. */
  private static String getErrorHtmlAsString(HttpResponse response) throws IOException {
    return CharStreams.toString(new InputStreamReader(response.getContent(), UTF_8));
  }

  private String internalSend(
      String endpoint, Map<String, ?> params, MediaType contentType, @Nullable byte[] payload)
      throws IOException {
    GenericUrl url = new GenericUrl(String.format("%s%s", getServerUrl(), endpoint));
    url.putAll(params);
    HttpRequest request =
        (payload != null)
            ? requestFactory.buildPostRequest(
                url, new ByteArrayContent(contentType.toString(), payload))
            : requestFactory.buildGetRequest(url);
    HttpHeaders headers = request.getHeaders();
    headers.setCacheControl("no-cache");
    headers.put(X_CSRF_TOKEN, ImmutableList.of(xsrfToken.get()));
    headers.put(X_REQUESTED_WITH, ImmutableList.of("RegistryTool"));
    request.setHeaders(headers);
    request.setFollowRedirects(false);
    request.setThrowExceptionOnExecuteError(false);
    request.setUnsuccessfulResponseHandler(
        (request1, response, supportsRetry) -> {
          String errorTitle = extractHtmlTitle(getErrorHtmlAsString(response));
          throw new IOException(
              String.format(
                  "Error from %s: %d %s%s",
                  request1.getUrl().toString(),
                  response.getStatusCode(),
                  response.getStatusMessage(),
                  (errorTitle == null ? "" : ": " + errorTitle)));
        });
    HttpResponse response = null;
    try {
      response = request.execute();
      return CharStreams.toString(new InputStreamReader(response.getContent(), UTF_8));
    } finally {
      if (response != null) {
        response.disconnect();
      }
    }
  }

  // TODO(b/111123862): Rename this to sendPostRequest()
  @Override
  public String send(String endpoint, Map<String, ?> params, MediaType contentType, byte[] payload)
      throws IOException {
    return internalSend(endpoint, params, contentType, checkNotNull(payload, "payload"));
  }

  @Override
  public String sendGetRequest(String endpoint, Map<String, ?> params) throws IOException {
    return internalSend(endpoint, params, MediaType.PLAIN_TEXT_UTF_8, null);
  }

  @Override
  @SuppressWarnings("unchecked")
  public Map<String, Object> sendJson(String endpoint, Map<String, ?> object) throws IOException {
    String response =
        send(
            endpoint,
            ImmutableMap.of(),
            JSON_UTF_8,
            JSONValue.toJSONString(object).getBytes(UTF_8));
    return (Map<String, Object>) JSONValue.parse(response.substring(JSON_SAFETY_PREFIX.length()));
  }

  @Override
  public String getServerUrl() {
    return (isLocalhost() ? "http://" : "https://") + getServer().toString();
  }

  HostAndPort getServer() {
    return flags.getServer().withDefaultPort(443);  // Default to HTTPS port if unspecified.
  }

  boolean isLocalhost() {
    return flags.getServer().getHost().equals("localhost");
  }

  private String getUserId() {
    return isLocalhost()
        ? UserIdProvider.getTestUserId()
        : UserIdProvider.getProdUserId();
  }
}
