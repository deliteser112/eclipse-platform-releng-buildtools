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

package com.google.domain.registry.testing.sftp;

import com.google.common.collect.ImmutableList;

import org.apache.ftpserver.FtpServer;
import org.apache.ftpserver.ftplet.FtpException;
import org.apache.sshd.SshServer;
import org.apache.sshd.common.Channel;
import org.apache.sshd.common.Cipher;
import org.apache.sshd.common.Compression;
import org.apache.sshd.common.KeyPairProvider;
import org.apache.sshd.common.Mac;
import org.apache.sshd.common.NamedFactory;
import org.apache.sshd.common.Session;
import org.apache.sshd.common.Signature;
import org.apache.sshd.common.cipher.AES128CBC;
import org.apache.sshd.common.cipher.AES192CBC;
import org.apache.sshd.common.cipher.AES256CBC;
import org.apache.sshd.common.cipher.BlowfishCBC;
import org.apache.sshd.common.cipher.TripleDESCBC;
import org.apache.sshd.common.compression.CompressionNone;
import org.apache.sshd.common.mac.HMACMD5;
import org.apache.sshd.common.mac.HMACMD596;
import org.apache.sshd.common.mac.HMACSHA1;
import org.apache.sshd.common.mac.HMACSHA196;
import org.apache.sshd.common.random.BouncyCastleRandom;
import org.apache.sshd.common.random.SingletonRandomFactory;
import org.apache.sshd.common.signature.SignatureDSA;
import org.apache.sshd.common.signature.SignatureRSA;
import org.apache.sshd.server.Command;
import org.apache.sshd.server.FileSystemFactory;
import org.apache.sshd.server.FileSystemView;
import org.apache.sshd.server.ForwardingAcceptorFactory;
import org.apache.sshd.server.PasswordAuthenticator;
import org.apache.sshd.server.PublickeyAuthenticator;
import org.apache.sshd.server.SshFile;
import org.apache.sshd.server.channel.ChannelDirectTcpip;
import org.apache.sshd.server.channel.ChannelSession;
import org.apache.sshd.server.filesystem.NativeFileSystemFactory;
import org.apache.sshd.server.filesystem.NativeSshFile;
import org.apache.sshd.server.kex.DHG1;
import org.apache.sshd.server.kex.DHG14;
import org.apache.sshd.server.session.DefaultForwardingAcceptorFactory;
import org.apache.sshd.server.session.ServerSession;
import org.apache.sshd.server.session.SessionFactory;
import org.apache.sshd.server.sftp.SftpSubsystem;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.security.KeyPair;
import java.security.PublicKey;
import java.security.Security;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.annotation.Nullable;

/** In-process SFTP server using Apache SSHD. */
public class TestSftpServer implements FtpServer {

  private static final Logger logger = Logger.getLogger(TestSftpServer.class.getName());

  private static SingletonRandomFactory secureRandomFactory;

  static {
    Security.addProvider(new BouncyCastleProvider());
    secureRandomFactory = new SingletonRandomFactory(new BouncyCastleRandom.Factory());
  }

  private static final String HOST_KEY = ""
      + "-----BEGIN RSA PRIVATE KEY-----\n"
      + "MIIEowIBAAKCAQEAx7uuoDyMR3c+sIg0fPfBeyoUjPa6hh3yN5S4/HLEOOaXcA2c\n"
      + "uhNm8WwIXF/FwC32CJHZcCC3XhNeQDIpEt5wj9NHr++pjYWjp4Ue+n/2QbDO4LKB\n"
      + "fb67zRbL++BZNswBKYwmzaiHoyWYERj+ZrZpf7hqHnKTAs4NIUCkhUvUHQzgdfT6\n"
      + "vfEYv2HrM2VVVaqpUALrkogZHqQYfLA+InHXXZ3x+GmdSBCxaxUHHZypwETv+yBJ\n"
      + "YAP+9Ofcmu3mpedK/3o3KJ2pD4OXwoXAtbHDOfnJMJylCKcCT3aodpdULSf4A8Zm\n"
      + "o4fxppQCbGUw43D7u18he5qNdLDkgCV8iXewbQIDAQABAoIBAH/Fs9e0BDVvtj3u\n"
      + "VE2hnTeyWsU2zWog3CPsU07ECH0yHqzDOIDdCpzk9JBLgFEJ1fvzebs+Yq+fCktd\n"
      + "C2OTw0Ru78xAMCJl3KS9B21O0PWDK0UZTLdpffCcZc/y3H+ukAvJKcWky2h2E0rU\n"
      + "x2JjzSe0jMZ/m0ZPFJ0yIk1XjhEqWgp3OCP9EHaO82+GM8UTuoBrfOs8gcv6aqiO\n"
      + "L06BU6a6i75chUtZbSadaUnZOE8tIyXit6p63bBHp2oN4D8FiOPBuTDwFEI4zrA4\n"
      + "vNcfRvKQWnQjlWm91BCajNxVqWi7XtWK7ikmwefZWNcd83RSf6vfb8ThpjwHmBaE\n"
      + "dbbL9i0CgYEA+KwB5qiaRxryBI1xqQaH6HiQ5WtW06ft43l2FrTZGPocjyF7KyWn\n"
      + "lS5q0J4tdSSkNfXqhT8aosm3VjfIxoDuQQO0ptD+km5qe1xu/qxLHJYd5FP67X9O\n"
      + "66e8sxcDSuKSvZ6wNeKNOL5SC7dDLCuMZoRTvXxGneg9a1w2x1MVrZsCgYEAzZ56\n"
      + "cqqNvavj4x2yDG+4oAB4/sQvVFK2+PopFpO5w8ezLDVfnoIv56gfmqMN9UdZH8Ew\n"
      + "PJhuEFRcdePo2ZnNjsWf9NhniJSMsEPTIc5qOPyoo5DcQM6DU8f4HC236uR+5P0h\n"
      + "jLfKRpvZl+N3skJi98QQr9PeLyb0sM8zRbZ0fpcCgYA9nuIZtk4EsLioSCSSLfwf\n"
      + "r0C4mRC7AjIA3GhW2Bm0BsZs8W8EEiCk5wuxBoFdNec7N+UVf72p+TJlOw2Vov1n\n"
      + "PvPVIpTy1EmuqAkZMriqLMjbe7QChjmYS8iG2H0IYXzbYCdqMumr1f2eyZrrpx7z\n"
      + "iHb3zYPyPUp7AC7S1dPZYQKBgQCK0p+LQVk3IJFYalkmiltVM1x9bUkjHkFIseUB\n"
      + "yDUYWIDArTxkkTL0rY7A4atv2X7zsIP3tVZCEiLmuTwhhfTBmu3W6jBkhx7Bdtla\n"
      + "LrmKxhK5c/kwi/0gmJcLt1Y/8Ys24SxAjGm16E0tfjb3FFkrPKWjgGC25w83PH06\n"
      + "aOgX+wKBgBLkLh/rwpiD4e8ZVjGuCn9A0H/2KZknXyNbkVjPho0FXBKIlfMa6c34\n"
      + "fRLDvHVZ0xo4dQxp38Wg+ZzIwGoAHhuJpSsfMsWKVXwNwV4iMEt2zyZRomui3TzT\n"
      + "+8awtDyALwvL05EBuxctT3iFGQcUj/fNCi0PeLoTSscdH2pdvHTb\n"
      + "-----END RSA PRIVATE KEY-----\n";

  private static final String KEY_TYPE = "ssh-rsa";

  private static final KeyPair HOST_KEY_PAIR = createKeyPair(HOST_KEY);

  @Nullable
  private static KeyPair createKeyPair(String key) {
    try (PEMParser pemParser = new PEMParser(new StringReader(key))) {
      PEMKeyPair pemPair = (PEMKeyPair) pemParser.readObject();
      KeyPair result = new JcaPEMKeyConverter().setProvider("BC").getKeyPair(pemPair);
      logger.info("Read key pair " + result);
      return result;
    } catch (IOException e) {
      logger.log(Level.SEVERE, "Couldn't read key pair from string(!)", e);
      return null;
    }
  }

  // Apache provides a NativeFileSystemView, but it assumes that the root
  // directory you want is /home/username.  Yep.
  // So reuse as much as we can.
  private static class TestFileSystemView implements FileSystemView {
    private final String userName;
    private final File home;

    public TestFileSystemView(String userName, File home) {
      this.userName = userName;
      this.home = home;
    }

    @Override
    public SshFile getFile(SshFile arg1, String arg2) {
      return null;
    }

    @Override
    public SshFile getFile(String fileName) {
      File file = new File(home, fileName);
      // Work around demands of NativeSshFile constructor.
      String absolutePath = fileName.equals(".") ? "/" : fileName;
      return new TestSshFile(absolutePath, file, userName, home);
    }
  }

  private static class TestSshFile extends NativeSshFile {
    // Purely an end-run around the protected constructor
    @SuppressWarnings("unused")
    TestSshFile(String fileName, File file, String userName, File home) {
      super(fileName, file, userName);
    }
  }

  public static FtpServer createSftpServer(
      final String authorizedUser,
      @Nullable final String authorizedPassword,
      @Nullable final PublicKey authorizedPublicKey,
      int port,
      final File home,
      SessionFactory sessionFactory) {

    final SshServer server = setUpDefaultServer();
    server.setPort(port);
    server.setSessionFactory(sessionFactory);

    NamedFactory<Command> sftpSubsystemFactory = new SftpSubsystem.Factory();
    server.setSubsystemFactories(ImmutableList.of(sftpSubsystemFactory));

    if (authorizedPassword != null) {
      PasswordAuthenticator passwordAuthenticator = new PasswordAuthenticator() {
        @Override
        public boolean authenticate(String username, String password, ServerSession session) {
          return username.equals(authorizedUser) && password.equals(authorizedPassword);
        }
      };
      server.setPasswordAuthenticator(passwordAuthenticator);
    }

    // This authenticator checks that the user is presenting the right key. If authenticate
    // returns true, then the server will make sure that the user can prove they have that key.
    // Not that you would know this from the Apache javadocs.
    if (authorizedPublicKey != null) {
      PublickeyAuthenticator publicKeyAuthenticator = new PublickeyAuthenticator() {
        @Override
        public boolean authenticate(String username, PublicKey publicKey, ServerSession session) {
          return Arrays.equals(publicKey.getEncoded(), authorizedPublicKey.getEncoded());
        }
      };
      server.setPublickeyAuthenticator(publicKeyAuthenticator);
    }

    FileSystemFactory fileSystemFactory = new FileSystemFactory() {
      @Override
      public FileSystemView createFileSystemView(Session session) {
        return new TestFileSystemView("anyone", home);
      }
    };
    server.setFileSystemFactory(fileSystemFactory);


    KeyPairProvider keyPairProvider = new KeyPairProvider() {
      @Override
      public KeyPair loadKey(String type) {
        return (type.equals(KEY_TYPE)) ? HOST_KEY_PAIR : null;
      }

      @Override
      public String getKeyTypes() {
        return KEY_TYPE;
      }
    };
    server.setKeyPairProvider(keyPairProvider);

    return new TestSftpServer(server);
  }

  private final SshServer server;

  private boolean stopped = true;

  private TestSftpServer(SshServer server) {
    this.server = server;
  }

  @Override
  public void suspend() {
    // NOP
  }

  @Override
  public synchronized void stop() {
    try {
      logger.info("Stopping server");
      server.stop();
      stopped = true;
    } catch (InterruptedException e) {
      logger.log(Level.WARNING, "Server shutdown interrupted", e);
    }
  }

  @Override
  public synchronized void start() throws FtpException {
    try {
      logger.info("Starting server");
      server.start();
      stopped = false;
    } catch (IOException e) {
      logger.log(Level.WARNING, "Couldn't start server", e);
      throw new FtpException("Couldn't start server", e);
    }
  }

  @Override
  public void resume() {
    // NOP
  }

  @Override
  public boolean isSuspended() {
    return false;
  }

  @Override
  public boolean isStopped() {
    return stopped;
  }

  // More almost-cut-and-paste from Apache.  Their version of this method
  // creates a new "singleton" random number generator each time it's called,
  // which in turn waits for enough securely random bits to be available from
  // the system.  Certainly for test purposes it's good enough for everyone
  // to share the same random seed.  SuppressWarnings because Apache is a bit
  // more lax about generics than we are.
  private static SshServer setUpDefaultServer() {
    SshServer sshd = new SshServer();
    // DHG14 uses 2048 bits key which are not supported by the default JCE provider
    sshd.setKeyExchangeFactories(Arrays.asList(
        new DHG14.Factory(),
        new DHG1.Factory()));
    sshd.setRandomFactory(secureRandomFactory);
    setUpDefaultCiphers(sshd);
    // Compression is not enabled by default
    // sshd.setCompressionFactories(Arrays.<NamedFactory<Compression>>asList(
    //         new CompressionNone.Factory(),
    //         new CompressionZlib.Factory(),
    //         new CompressionDelayedZlib.Factory()));
    sshd.setCompressionFactories(Arrays.<NamedFactory<Compression>>asList(
        new CompressionNone.Factory()));
    sshd.setMacFactories(Arrays.<NamedFactory<Mac>>asList(
        new HMACMD5.Factory(),
        new HMACSHA1.Factory(),
        new HMACMD596.Factory(),
        new HMACSHA196.Factory()));
    sshd.setChannelFactories(Arrays.<NamedFactory<Channel>>asList(
        new ChannelSession.Factory(),
        new ChannelDirectTcpip.Factory()));
    sshd.setSignatureFactories(Arrays.<NamedFactory<Signature>>asList(
        new SignatureDSA.Factory(),
        new SignatureRSA.Factory()));
    sshd.setFileSystemFactory(new NativeFileSystemFactory());

    ForwardingAcceptorFactory faf = new DefaultForwardingAcceptorFactory();
    sshd.setTcpipForwardNioSocketAcceptorFactory(faf);
    sshd.setX11ForwardNioSocketAcceptorFactory(faf);

    return sshd;
  }

  private static void setUpDefaultCiphers(SshServer sshd) {
    List<NamedFactory<Cipher>> avail = new LinkedList<>();
    avail.add(new AES128CBC.Factory());
    avail.add(new TripleDESCBC.Factory());
    avail.add(new BlowfishCBC.Factory());
    avail.add(new AES192CBC.Factory());
    avail.add(new AES256CBC.Factory());

    for (Iterator<NamedFactory<Cipher>> i = avail.iterator(); i.hasNext();) {
      final NamedFactory<Cipher> f = i.next();
      try {
        final Cipher c = f.create();
        final byte[] key = new byte[c.getBlockSize()];
        final byte[] iv = new byte[c.getIVSize()];
        c.init(Cipher.Mode.Encrypt, key, iv);
      } catch (Exception e) {
        i.remove();
      }
    }
    sshd.setCipherFactories(avail);
  }
}
