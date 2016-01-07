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

import static com.google.common.truth.Truth.assertThat;
import static google.registry.util.ResourceUtils.readResourceBytes;

import com.google.common.io.ByteSource;
import com.google.common.io.Files;
import google.registry.rde.RdeKeyringModule;
import google.registry.rde.RdeTestData;
import google.registry.rde.RydePgpCompressionOutputStreamFactory;
import google.registry.rde.RydePgpEncryptionOutputStreamFactory;
import google.registry.rde.RydePgpFileOutputStreamFactory;
import google.registry.rde.RydePgpSigningOutputStreamFactory;
import google.registry.rde.RydeTarOutputStreamFactory;
import google.registry.testing.BouncyCastleProviderRule;
import google.registry.testing.Providers;
import java.io.File;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

/** Unit tests for {@link EncryptEscrowDepositCommand}. */
public class EncryptEscrowDepositCommandTest
    extends CommandTestCase<EncryptEscrowDepositCommand> {

  @Rule
  public final BouncyCastleProviderRule bouncy = new BouncyCastleProviderRule();

  private final ByteSource depositXml =
      readResourceBytes(RdeTestData.class, "testdata/deposit_full.xml");

  static EscrowDepositEncryptor createEncryptor() {
    EscrowDepositEncryptor res = new EscrowDepositEncryptor();
    res.pgpCompressionFactory = new RydePgpCompressionOutputStreamFactory(Providers.of(1024));
    res.pgpEncryptionFactory = new RydePgpEncryptionOutputStreamFactory(Providers.of(1024));
    res.pgpFileFactory = new RydePgpFileOutputStreamFactory(Providers.of(1024));
    res.pgpSigningFactory = new RydePgpSigningOutputStreamFactory();
    res.tarFactory = new RydeTarOutputStreamFactory();
    res.rdeReceiverKey = new RdeKeyringModule().get().getRdeReceiverKey();
    res.rdeSigningKey = new RdeKeyringModule().get().getRdeSigningKey();
    return res;
  }

  @Before
  public void before() throws Exception {
    command.encryptor = createEncryptor();
  }

  @Test
  public void testSuccess() throws Exception {
    File outDir = tmpDir.newFolder();
    File depositFile = tmpDir.newFile("deposit.xml");
    Files.write(depositXml.read(), depositFile);
    runCommand(
        "--tld=lol",
        "--input=" + depositFile.getPath(),
        "--outdir=" + outDir.getPath());
    assertThat(outDir.list()).asList().containsExactly(
        "lol_2010-10-17_full_S1_R0.ryde",
        "lol_2010-10-17_full_S1_R0.sig",
        "lol.pub");
  }
}
