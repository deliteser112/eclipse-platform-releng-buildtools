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

package google.registry.rdap;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableMap;
import google.registry.rdap.RdapMetrics.EndpointType;
import google.registry.request.Parameter;
import google.registry.request.ParameterMap;
import google.registry.request.RequestUrl;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.inject.Inject;

/**
 * Base RDAP (new WHOIS) action for domain, nameserver and entity search requests.
 *
 * @see <a href="https://tools.ietf.org/html/rfc7482">
 *        RFC 7482: Registration Data Access Protocol (RDAP) Query Format</a>
 */
public abstract class RdapSearchActionBase extends RdapActionBase {

  @Inject @RequestUrl String requestUrl;
  @Inject @ParameterMap ImmutableListMultimap<String, String> parameterMap;
  @Inject @Parameter("cursor") Optional<String> cursorTokenParam;

  protected Optional<String> cursorString;

  RdapSearchActionBase(String humanReadableObjectTypeName, EndpointType endpointType) {
    super(humanReadableObjectTypeName, endpointType);
  }

  /**
   * Decodes the cursor token passed in the HTTP request.
   *
   * <p>The cursor token is just the Base 64 encoded value of the last data item returned. To fetch
   * the next page, the code can just decode the cursor, and return only data whose value is greater
   * than the cursor value.
   */
  protected void decodeCursorToken() {
    if (!cursorTokenParam.isPresent()) {
      cursorString = Optional.empty();
    } else {
      cursorString =
          Optional.of(
              new String(
                  Base64.getDecoder().decode(cursorTokenParam.get().getBytes(UTF_8)), UTF_8));
    }
  }

  /** Returns an encoded cursor token to pass back in the RDAP JSON link strings. */
  protected String encodeCursorToken(String nextCursorString) {
    return new String(Base64.getEncoder().encode(nextCursorString.getBytes(UTF_8)), UTF_8);
  }

  /** Returns the original request URL, but with the specified parameter added or overridden. */
  protected String getRequestUrlWithExtraParameter(String parameterName, String parameterValue) {
    return getRequestUrlWithExtraParameter(parameterName, ImmutableList.of(parameterValue));
  }

  /**
   * Returns the original request URL, but with the specified parameter added or overridden.
   *
   * <p>This version handles a list of parameter values, all associated with the same name.
   *
   * <p>Example: If the original parameters were "a=w&a=x&b=y&c=z", and this method is called with
   * parameterName = "b" and parameterValues of "p" and "q", the result will be
   * "a=w&a=x&c=z&b=p&b=q". The new values of parameter "b" replace the old ones.
   *
   */
  protected String getRequestUrlWithExtraParameter(
      String parameterName, List<String> parameterValues) {
    StringBuilder stringBuilder = new StringBuilder(requestUrl);
    boolean first = true;
    // Step one: loop through the existing parameters, copying all of them except for the parameter
    // we want to explicitly set.
    for (Map.Entry<String, String> entry : parameterMap.entries()) {
      if (!entry.getKey().equals(parameterName)) {
        appendParameter(stringBuilder, entry.getKey(), entry.getValue(), first);
        first = false;
      }
    }
    // Step two: tack on all values of the explicit parameter.
    for (String parameterValue : parameterValues) {
      appendParameter(stringBuilder, parameterName, parameterValue, first);
      first = false;
    }
    return stringBuilder.toString();
  }

  private void appendParameter(
      StringBuilder stringBuilder, String name, String value, boolean first) {
    try {
      stringBuilder.append(first ? '?' : '&');
      stringBuilder.append(URLEncoder.encode(name, "UTF-8"));
      stringBuilder.append('=');
      stringBuilder.append(URLEncoder.encode(value, "UTF-8"));
    } catch (UnsupportedEncodingException e) {
      throw new RuntimeException(e);
    }
  }

  ImmutableList<ImmutableMap<String, Object>> getNotices(RdapSearchResults results) {
    ImmutableList<ImmutableMap<String, Object>> notices = results.getIncompletenessWarnings();
    if (results.nextCursor().isPresent()) {
      ImmutableList.Builder<ImmutableMap<String, Object>> noticesBuilder =
          new ImmutableList.Builder<>();
      noticesBuilder.addAll(notices);
      noticesBuilder.add(
          RdapJsonFormatter.makeRdapJsonNavigationLinkNotice(
              Optional.of(
                  getRequestUrlWithExtraParameter(
                      "cursor", encodeCursorToken(results.nextCursor().get())))));
      notices = noticesBuilder.build();
    }
    return notices;
  }
}
