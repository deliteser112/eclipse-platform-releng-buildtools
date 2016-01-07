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

package google.registry.tools;

import static google.registry.model.rde.RdeMode.FULL;

import com.google.common.io.ByteStreams;
import google.registry.keyring.api.KeyModule.Key;
import google.registry.model.rde.RdeNamingUtils;
import google.registry.rde.RdeUtil;
import google.registry.rde.RydePgpCompressionOutputStream;
import google.registry.rde.RydePgpCompressionOutputStreamFactory;
import google.registry.rde.RydePgpEncryptionOutputStream;
import google.registry.rde.RydePgpEncryptionOutputStreamFactory;
import google.registry.rde.RydePgpFileOutputStream;
import google.registry.rde.RydePgpFileOutputStreamFactory;
import google.registry.rde.RydePgpSigningOutputStream;
import google.registry.rde.RydePgpSigningOutputStreamFactory;
import google.registry.rde.RydeTarOutputStream;
import google.registry.rde.RydeTarOutputStreamFactory;
import google.registry.xml.XmlException;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.inject.Inject;
import org.bouncycastle.bcpg.ArmoredOutputStream;
import org.bouncycastle.openpgp.PGPException;
import org.bouncycastle.openpgp.PGPKeyPair;
import org.bouncycastle.openpgp.PGPPublicKey;
import org.joda.time.DateTime;

/** Utility for encrypting an RDE RyDE deposit on the Java 7 NIO file system. */
final class EscrowDepositEncryptor {

  private static final int PEEK_BUFFER_SIZE = 64 * 1024;

  @Inject RydePgpCompressionOutputStreamFactory pgpCompressionFactory;
  @Inject RydePgpEncryptionOutputStreamFactory pgpEncryptionFactory;
  @Inject RydePgpFileOutputStreamFactory pgpFileFactory;
  @Inject RydePgpSigningOutputStreamFactory pgpSigningFactory;
  @Inject RydeTarOutputStreamFactory tarFactory;
  @Inject @Key("rdeSigningKey") PGPKeyPair rdeSigningKey;
  @Inject @Key("rdeReceiverKey") PGPPublicKey rdeReceiverKey;
  @Inject EscrowDepositEncryptor() {}

  /** Creates a {@code .ryde} and {@code .sig} file, provided an XML deposit file. */
  void encrypt(String tld, Path xmlFile, Path outdir)
      throws IOException, PGPException, XmlException {
    try (InputStream xmlFileInput = Files.newInputStream(xmlFile);
        BufferedInputStream xmlInput = new BufferedInputStream(xmlFileInput, PEEK_BUFFER_SIZE)) {
      DateTime watermark = RdeUtil.peekWatermark(xmlInput);
      String name = RdeNamingUtils.makeRydeFilename(tld, watermark, FULL, 1, 0);
      Path rydePath = outdir.resolve(name + ".ryde");
      Path sigPath = outdir.resolve(name + ".sig");
      Path pubPath = outdir.resolve(tld + ".pub");
      PGPKeyPair signingKey = rdeSigningKey;
      try (OutputStream rydeOutput = Files.newOutputStream(rydePath);
          RydePgpSigningOutputStream signLayer =
              pgpSigningFactory.create(rydeOutput, signingKey)) {
        try (RydePgpEncryptionOutputStream encryptLayer =
                pgpEncryptionFactory.create(signLayer, rdeReceiverKey);
            RydePgpCompressionOutputStream compressLayer =
                pgpCompressionFactory.create(encryptLayer);
            RydePgpFileOutputStream fileLayer =
                pgpFileFactory.create(compressLayer, watermark, name + ".tar");
            RydeTarOutputStream tarLayer =
                tarFactory.create(fileLayer, Files.size(xmlFile), watermark, name + ".xml")) {
          ByteStreams.copy(xmlInput, tarLayer);
        }
        Files.write(sigPath, signLayer.getSignature());
        try (OutputStream pubOutput = Files.newOutputStream(pubPath);
            ArmoredOutputStream ascOutput = new ArmoredOutputStream(pubOutput)) {
          signingKey.getPublicKey().encode(ascOutput);
        }
      }
    }
  }
}
