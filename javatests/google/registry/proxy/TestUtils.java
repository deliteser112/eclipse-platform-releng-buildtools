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

package google.registry.proxy;

import static com.google.common.truth.Truth.assertThat;
import static google.registry.proxy.handler.EppServiceHandler.EPP_CONTENT_TYPE;
import static google.registry.proxy.handler.EppServiceHandler.FORWARDED_FOR_FIELD;
import static google.registry.proxy.handler.EppServiceHandler.REQUESTED_SERVERNAME_VIA_SNI_FIELD;
import static google.registry.proxy.handler.EppServiceHandler.SSL_CLIENT_CERTIFICATE_HASH_FIELD;
import static java.nio.charset.StandardCharsets.US_ASCII;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpMessage;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.cookie.ClientCookieEncoder;
import io.netty.handler.codec.http.cookie.Cookie;
import io.netty.handler.codec.http.cookie.ServerCookieEncoder;

/** Utility class for various helper methods used in testing. */
public class TestUtils {

  public static FullHttpRequest makeHttpPostRequest(String content, String host, String path) {
    ByteBuf buf = Unpooled.wrappedBuffer(content.getBytes(US_ASCII));
    FullHttpRequest request =
        new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, path, buf);
    request
        .headers()
        .set(HttpHeaderNames.USER_AGENT, "Proxy")
        .set(HttpHeaderNames.HOST, host)
        .setInt(HttpHeaderNames.CONTENT_LENGTH, buf.readableBytes());
    return request;
  }

  public static FullHttpRequest makeHttpGetRequest(String host, String path) {
    FullHttpRequest request =
        new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, path);
    request.headers().set(HttpHeaderNames.HOST, host).setInt(HttpHeaderNames.CONTENT_LENGTH, 0);
    return request;
  }

  public static FullHttpResponse makeHttpResponse(String content, HttpResponseStatus status) {
    ByteBuf buf = Unpooled.wrappedBuffer(content.getBytes(US_ASCII));
    FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status, buf);
    response.headers().setInt(HttpHeaderNames.CONTENT_LENGTH, buf.readableBytes());
    return response;
  }

  public static FullHttpRequest makeWhoisHttpRequest(
      String content, String host, String path, String accessToken) {
    FullHttpRequest request = makeHttpPostRequest(content, host, path);
    request
        .headers()
        .set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE)
        .set(HttpHeaderNames.AUTHORIZATION, "Bearer " + accessToken)
        .set(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.TEXT_PLAIN)
        .set(HttpHeaderNames.ACCEPT, HttpHeaderValues.TEXT_PLAIN);
    return request;
  }

  public static FullHttpRequest makeEppHttpRequest(
      String content,
      String host,
      String path,
      String accessToken,
      String sslClientCertificateHash,
      String serverHostname,
      String clientAddress,
      Cookie... cookies) {
    FullHttpRequest request = makeHttpPostRequest(content, host, path);
    request
        .headers()
        .set(HttpHeaderNames.AUTHORIZATION, "Bearer " + accessToken)
        .set(HttpHeaderNames.CONTENT_TYPE, EPP_CONTENT_TYPE)
        .set(HttpHeaderNames.ACCEPT, EPP_CONTENT_TYPE)
        .set(SSL_CLIENT_CERTIFICATE_HASH_FIELD, sslClientCertificateHash)
        .set(REQUESTED_SERVERNAME_VIA_SNI_FIELD, serverHostname)
        .set(FORWARDED_FOR_FIELD, clientAddress);
    if (cookies.length != 0) {
      request.headers().set(HttpHeaderNames.COOKIE, ClientCookieEncoder.STRICT.encode(cookies));
    }
    return request;
  }

  public static FullHttpResponse makeWhoisHttpResponse(String content, HttpResponseStatus status) {
    FullHttpResponse response = makeHttpResponse(content, status);
    response.headers().set(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.TEXT_PLAIN);
    return response;
  }

  public static FullHttpResponse makeEppHttpResponse(
      String content, HttpResponseStatus status, Cookie... cookies) {
    FullHttpResponse response = makeHttpResponse(content, status);
    response.headers().set(HttpHeaderNames.CONTENT_TYPE, EPP_CONTENT_TYPE);
    for (Cookie cookie : cookies) {
      response.headers().add(HttpHeaderNames.SET_COOKIE, ServerCookieEncoder.STRICT.encode(cookie));
    }
    return response;
  }

  /**
   * Compares two {@link FullHttpMessage} for equivalency.
   *
   * <p>This method is needed because an HTTP message decoded and aggregated from inbound {@link
   * ByteBuf} is of a different class than the one written to the outbound {@link ByteBuf}, and The
   * {@link ByteBuf} implementations that hold the content of the HTTP messages are different, even
   * though the actual content, headers, etc are the same.
   *
   * <p>This method is not type-safe, msg1 & msg2 can be a request and a response, respectively. Do
   * not use this method directly.
   */
  private static void assertHttpMessageEquivalent(FullHttpMessage msg1, FullHttpMessage msg2) {
    assertThat(msg1.protocolVersion()).isEqualTo(msg2.protocolVersion());
    assertThat(msg1.content()).isEqualTo(msg2.content());
    assertThat(msg1.headers()).isEqualTo(msg2.headers());
  }

  public static void assertHttpResponseEquivalent(FullHttpResponse res1, FullHttpResponse res2) {
    assertThat(res1.status()).isEqualTo(res2.status());
    assertHttpMessageEquivalent(res1, res2);
  }

  public static void assertHttpRequestEquivalent(FullHttpRequest req1, FullHttpRequest req2) {
    assertHttpMessageEquivalent(req1, req2);
  }
}
