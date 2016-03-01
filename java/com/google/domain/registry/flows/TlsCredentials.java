// Copyright 2016 Google Inc. All Rights Reserved.
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

package com.google.domain.registry.flows;

import static com.google.common.base.MoreObjects.toStringHelper;
import static com.google.common.base.Strings.isNullOrEmpty;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.net.HostAndPort;
import com.google.common.net.InetAddresses;
import com.google.domain.registry.flows.EppException.AuthenticationErrorException;
import com.google.domain.registry.model.registrar.Registrar;
import com.google.domain.registry.util.CidrAddressBlock;
import com.google.domain.registry.util.FormattingLogger;

import java.net.InetAddress;

import javax.servlet.http.HttpServletRequest;

/**
 * Container and validation for TLS certificate and ip-whitelisting.
 */
public final class TlsCredentials implements TransportCredentials {

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

  private static final FormattingLogger logger = FormattingLogger.getLoggerForCallerClass();

  private final String clientCertificateHash;
  private final InetAddress clientInetAddr;
  private final String sni;

  @VisibleForTesting
  public TlsCredentials(String clientCertificateHash, InetAddress clientInetAddr, String sni) {
    this.clientCertificateHash = clientCertificateHash;
    this.clientInetAddr = clientInetAddr;
    this.sni = sni;
  }

  /**
   * Extracts the client TLS certificate and source internet address
   * from the given HTTP request.
   */
  TlsCredentials(HttpServletRequest req) {
    this(req.getHeader(EppTlsServlet.SSL_CLIENT_CERTIFICATE_HASH_FIELD),
        parseInetAddress(req.getHeader(EppTlsServlet.FORWARDED_FOR_FIELD)),
        req.getHeader(EppTlsServlet.REQUESTED_SERVERNAME_VIA_SNI_FIELD));
  }

  static InetAddress parseInetAddress(String asciiAddr) {
    try {
      return InetAddresses.forString(HostAndPort.fromString(asciiAddr).getHostText());
    } catch (IllegalArgumentException e) {
      return null;
    }
  }

  @Override
  public boolean performsLoginCheck() {
    return false;
  }

  /** Returns {@code true} if frontend passed us the requested server name. */
  boolean hasSni() {
    return !isNullOrEmpty(sni);
  }

  @Override
  public void validate(Registrar registrar) throws AuthenticationErrorException {
    validateIp(registrar);
    validateCertificate(registrar);
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
      logger.infofmt("Request did not include %s", EppTlsServlet.SSL_CLIENT_CERTIFICATE_HASH_FIELD);
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

  @Override
  public String toString() {
    return toStringHelper(getClass())
        .add("system hash code", System.identityHashCode(this))
        .add("clientCertificateHash", clientCertificateHash)
        .add("clientInetAddress", clientInetAddr)
        .add("sni", sni)
        .toString();
  }
}
