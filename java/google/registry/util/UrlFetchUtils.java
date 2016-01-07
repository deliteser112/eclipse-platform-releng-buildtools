// Copyright 2016 The Nomulus Authors. All Rights Reserved.
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

package google.registry.util;

import static com.google.common.io.BaseEncoding.base32;
import static com.google.common.io.BaseEncoding.base64;
import static com.google.common.net.HttpHeaders.AUTHORIZATION;
import static com.google.common.net.HttpHeaders.CONTENT_DISPOSITION;
import static com.google.common.net.HttpHeaders.CONTENT_LENGTH;
import static com.google.common.net.HttpHeaders.CONTENT_TYPE;
import static java.lang.String.format;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.appengine.api.urlfetch.HTTPHeader;
import com.google.appengine.api.urlfetch.HTTPRequest;
import com.google.appengine.api.urlfetch.HTTPResponse;
import com.google.common.base.Ascii;
import com.google.common.base.Optional;
import com.google.common.net.MediaType;
import java.security.NoSuchAlgorithmException;
import java.security.ProviderException;
import java.security.SecureRandom;

/** Helper methods for the App Engine URL fetch service. */
public final class UrlFetchUtils {

  @NonFinalForTesting
  private static SecureRandom secureRandom = initSecureRandom();

  /** Returns value of first header matching {@code name}. */
  public static Optional<String> getHeaderFirst(HTTPResponse rsp, String name) {
    return getHeaderFirstInternal(rsp.getHeadersUncombined(), name);
  }

  /** Returns value of first header matching {@code name}. */
  public static Optional<String> getHeaderFirst(HTTPRequest req, String name) {
    return getHeaderFirstInternal(req.getHeaders(), name);
  }

  private static Optional<String> getHeaderFirstInternal(Iterable<HTTPHeader> hdrs, String name) {
    name = Ascii.toLowerCase(name);
    for (HTTPHeader header : hdrs) {
      if (Ascii.toLowerCase(header.getName()).equals(name)) {
        return Optional.of(header.getValue());
      }
    }
    return Optional.absent();
  }

  /**
   * Sets payload on request as a {@code multipart/form-data} request.
   *
   * <p>This is equivalent to running the command: {@code curl -F fieldName=@payload.txt URL}
   *
   * @see "http://www.ietf.org/rfc/rfc2388.txt"
   */
  public static <T> void setPayloadMultipart(
      HTTPRequest request, String name, String filename, MediaType contentType, T data) {
    String boundary = createMultipartBoundary();
    StringBuilder multipart = new StringBuilder();
    multipart.append(format("--%s\r\n", boundary));
    multipart.append(format("%s: form-data; name=\"%s\"; filename=\"%s\"\r\n",
        CONTENT_DISPOSITION, name, filename));
    multipart.append(format("%s: %s\r\n", CONTENT_TYPE, contentType.toString()));
    multipart.append("\r\n");
    multipart.append(data);
    multipart.append("\r\n");
    multipart.append(format("--%s--", boundary));
    byte[] payload = multipart.toString().getBytes(UTF_8);
    request.addHeader(new HTTPHeader(CONTENT_TYPE, "multipart/form-data; boundary=" + boundary));
    request.addHeader(new HTTPHeader(CONTENT_LENGTH, Integer.toString(payload.length)));
    request.setPayload(payload);
  }

  private static String createMultipartBoundary() {
    byte[] rand = new byte[5];  // Avoid base32 padding since `5 * 8 % log2(32) == 0`
    secureRandom.nextBytes(rand);
    return "------------------------------" + base32().encode(rand);
  }

  private static SecureRandom initSecureRandom() {
    try {
      return SecureRandom.getInstance("NativePRNG");
    } catch (NoSuchAlgorithmException e) {
      throw new ProviderException(e);
    }
  }

  /** Sets the HTTP Basic Authentication header on an {@link HTTPRequest}. */
  public static void setAuthorizationHeader(HTTPRequest req, Optional<String> login) {
    if (login.isPresent()) {
      String token = base64().encode(login.get().getBytes(UTF_8));
      req.addHeader(new HTTPHeader(AUTHORIZATION, "Basic " + token));
    }
  }
}
