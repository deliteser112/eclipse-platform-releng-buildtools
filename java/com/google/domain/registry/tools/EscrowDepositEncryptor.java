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

package com.google.domain.registry.tools;

import static com.google.domain.registry.model.rde.RdeMode.FULL;

import com.google.common.io.ByteStreams;
import com.google.domain.registry.keyring.api.KeyModule.Key;
import com.google.domain.registry.model.rde.RdeNamingUtils;
import com.google.domain.registry.rde.RdeUtil;
import com.google.domain.registry.rde.RydePgpCompressionOutputStream;
import com.google.domain.registry.rde.RydePgpCompressionOutputStreamFactory;
import com.google.domain.registry.rde.RydePgpEncryptionOutputStream;
import com.google.domain.registry.rde.RydePgpEncryptionOutputStreamFactory;
import com.google.domain.registry.rde.RydePgpFileOutputStream;
import com.google.domain.registry.rde.RydePgpFileOutputStreamFactory;
import com.google.domain.registry.rde.RydePgpSigningOutputStream;
import com.google.domain.registry.rde.RydePgpSigningOutputStreamFactory;
import com.google.domain.registry.rde.RydeTarOutputStream;
import com.google.domain.registry.rde.RydeTarOutputStreamFactory;
import com.google.domain.registry.xml.XmlException;

import org.bouncycastle.bcpg.ArmoredOutputStream;
import org.bouncycastle.openpgp.PGPException;
import org.bouncycastle.openpgp.PGPKeyPair;
import org.bouncycastle.openpgp.PGPPublicKey;
import org.joda.time.DateTime;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.inject.Inject;

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
