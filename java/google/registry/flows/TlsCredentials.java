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

package google.registry.flows;

import static com.google.common.base.MoreObjects.toStringHelper;
import static com.google.common.base.Strings.isNullOrEmpty;
import static google.registry.request.RequestParameters.extractOptionalHeader;
import static google.registry.request.RequestParameters.extractRequiredHeader;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.net.HostAndPort;
import com.google.common.net.InetAddresses;
import dagger.Module;
import dagger.Provides;
import google.registry.flows.EppException.AuthenticationErrorException;
import google.registry.model.registrar.Registrar;
import google.registry.request.Header;
import google.registry.util.CidrAddressBlock;
import google.registry.util.FormattingLogger;
import java.net.InetAddress;
import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;

/**
 * Container and validation for TLS certificate and ip-whitelisting.
 *
 * <p>Credentials are based on the following headers:
 * <dl>
 *   <dt>X-GFE-Requested-Servername-SNI
 *   <dd>
 *     This field should contain a base64 encoded digest of the client's TLS certificate. It is
 *     validated during an EPP login command against a known good value that is transmitted out of
 *     band.
 *   <dt>X-Forwarded-For
 *   <dd>
 *     This field should contain the host and port of the connecting client. It is validated during
 *     an EPP login command against an IP whitelist that is transmitted out of band.
 *   <dt>X-GFE-Requested-Servername-SNI
 *   <dd>
 *     This field should contain the servername that the client requested during the TLS handshake.
 *     It is unused, but expected to be present in the GFE-proxied configuration.
 * </dl>
 */
public class TlsCredentials implements TransportCredentials {

  private static final FormattingLogger logger = FormattingLogger.getLoggerForCallerClass();

  private final String clientCertificateHash;
  private final String sni;
  private final InetAddress clientInetAddr;

  @Inject
  @VisibleForTesting
  public TlsCredentials(
      @Header("X-GFE-SSL-Certificate") String clientCertificateHash,
      @Header("X-Forwarded-For") Optional<String> clientAddress,
      @Header("X-GFE-Requested-Servername-SNI") String sni) {
    this.clientCertificateHash = clientCertificateHash;
    this.clientInetAddr = clientAddress.isPresent() ? parseInetAddress(clientAddress.get()) : null;
    this.sni = sni;
  }

  static InetAddress parseInetAddress(String asciiAddr) {
    try {
      return InetAddresses.forString(HostAndPort.fromString(asciiAddr).getHostText());
    } catch (IllegalArgumentException e) {
      return null;
    }
  }

  /** Returns {@code true} if frontend passed us the requested server name. */
  boolean hasSni() {
    return !isNullOrEmpty(sni);
  }

  @Override
  public void validate(Registrar registrar, String password) throws AuthenticationErrorException {
    validateIp(registrar);
    validateCertificate(registrar);
    validatePassword(registrar, password);
  }

  /**
   * Verifies {@link #clientInetAddr} is in CIDR whitelist associated with {@code registrar}.
   *
   * @throws BadRegistrarIpAddressException If IP address is not in the whitelist provided
   */
  private void validateIp(Registrar registrar) throws AuthenticationErrorException {
    ImmutableList<CidrAddressBlock> ipWhitelist = registrar.getIpAddressWhitelist();
    if (ipWhitelist.isEmpty()) {
      logger.infofmt("Skipping IP whitelist check because %s doesn't have an IP whitelist",
          registrar.getClientIdentifier());
      return;
    }
    for (CidrAddressBlock cidrAddressBlock : ipWhitelist) {
      if (cidrAddressBlock.contains(clientInetAddr)) {
        // IP address is in whitelist; return early.
        return;
      }
    }
    logger.infofmt("%s not in %s's CIDR whitelist: %s",
        clientInetAddr, registrar.getClientIdentifier(), ipWhitelist);
    throw new BadRegistrarIpAddressException();
  }

  /**
   * Verifies client SSL certificate is permitted to issue commands as {@code registrar}.
   *
   * @throws NoSniException if frontend didn't send host or certificate hash headers
   * @throws MissingRegistrarCertificateException if frontend didn't send certificate hash header
   * @throws BadRegistrarCertificateException if registrar requires certificate and it didn't match
   */
  private void validateCertificate(Registrar registrar) throws AuthenticationErrorException {
    if (isNullOrEmpty(registrar.getClientCertificateHash())
        && isNullOrEmpty(registrar.getFailoverClientCertificateHash())) {
      logger.infofmt(
          "Skipping SSL certificate check because %s doesn't have any certificate hashes on file",
          registrar.getClientIdentifier());
      return;
    }
    if (isNullOrEmpty(clientCertificateHash)) {
      // If there's no SNI header that's probably why we don't have a cert, so send a specific
      // message. Otherwise, send a missing certificate message.
      if (!hasSni()) {
        throw new NoSniException();
      }
      logger.infofmt("Request did not include %s", "X-GFE-SSL-Certificate");
      throw new MissingRegistrarCertificateException();
    }
    if (!clientCertificateHash.equals(registrar.getClientCertificateHash())
        && !clientCertificateHash.equals(registrar.getFailoverClientCertificateHash())) {
      logger.warningfmt("bad certificate hash (%s) for %s, wanted either %s or %s",
          clientCertificateHash,
          registrar.getClientIdentifier(),
          registrar.getClientCertificateHash(),
          registrar.getFailoverClientCertificateHash());
      throw new BadRegistrarCertificateException();
    }
  }

  private void validatePassword(Registrar registrar, String password)
      throws BadRegistrarPasswordException {
    if (!registrar.testPassword(password)) {
      throw new BadRegistrarPasswordException();
    }
  }

  @Override
  public String toString() {
    return toStringHelper(getClass())
        .add("clientCertificateHash", clientCertificateHash)
        .add("clientAddress", clientInetAddr)
        .add("sni", sni)
        .toString();
  }

  /** Registrar certificate does not match stored certificate. */
  public static class BadRegistrarCertificateException extends AuthenticationErrorException {
    public BadRegistrarCertificateException() {
      super("Registrar certificate does not match stored certificate");
    }
  }

  /** Registrar certificate not present. */
  public static class MissingRegistrarCertificateException extends AuthenticationErrorException {
    public MissingRegistrarCertificateException() {
      super("Registrar certificate not present");
    }
  }

  /** SNI header is required. */
  public static class NoSniException extends AuthenticationErrorException {
    public NoSniException() {
      super("SNI header is required");
    }
  }

  /** Registrar IP address is not in stored whitelist. */
  public static class BadRegistrarIpAddressException extends AuthenticationErrorException {
    public BadRegistrarIpAddressException() {
      super("Registrar IP address is not in stored whitelist");
    }
  }

  /** Dagger module for the EPP TLS endpoint. */
  @Module
  public static final class EppTlsModule {
    @Provides
    @Header("X-GFE-SSL-Certificate")
    static String provideClientCertificateHash(HttpServletRequest req) {
      return extractRequiredHeader(req, "X-GFE-SSL-Certificate");
    }

    @Provides
    @Header("X-Forwarded-For")
    static Optional<String> provideForwardedFor(HttpServletRequest req) {
      return extractOptionalHeader(req, "X-Forwarded-For");
    }

    @Provides
    @Header("X-GFE-Requested-Servername-SNI")
    static String provideRequestedServername(HttpServletRequest req) {
      return extractRequiredHeader(req, "X-GFE-Requested-Servername-SNI");
    }
  }
}
