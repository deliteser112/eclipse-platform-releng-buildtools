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

import static com.google.common.truth.Truth.assertThat;
import static java.nio.charset.StandardCharsets.UTF_8;

import google.registry.keyring.api.Keyring;
import google.registry.rde.Ghostryde;
import google.registry.rde.Ghostryde.DecodeResult;
import google.registry.rde.RdeKeyringModule;
import google.registry.testing.BouncyCastleProviderRule;
import google.registry.testing.InjectRule;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

/** Unit tests for {@link GhostrydeCommand}. */
public class GhostrydeCommandTest extends CommandTestCase<GhostrydeCommand> {

  private static final DateTime MODIFIED_TIME = DateTime.parse("1984-12-18T04:20:00Z");
  private static final byte[] SONG_BY_CHRISTINA_ROSSETTI = (""
      + "When I am dead, my dearest,       \n"
      + "  Sing no sad songs for me;       \n"
      + "Plant thou no roses at my head,   \n"
      + "  Nor shady cypress tree:         \n"
      + "Be the green grass above me       \n"
      + "  With showers and dewdrops wet;  \n"
      + "And if thou wilt, remember,       \n"
      + "  And if thou wilt, forget.       \n"
      + "                                  \n"
      + "I shall not see the shadows,      \n"
      + "  I shall not feel the rain;      \n"
      + "I shall not hear the nightingale  \n"
      + "  Sing on, as if in pain:         \n"
      + "And dreaming through the twilight \n"
      + "  That doth not rise nor set,     \n"
      + "Haply I may remember,             \n"
      + "  And haply may forget.           \n").getBytes(UTF_8);

  @Rule
  public final InjectRule inject = new InjectRule();

  @Rule
  public final BouncyCastleProviderRule bouncy = new BouncyCastleProviderRule();

  private Keyring keyring;

  @Before
  public void before() throws Exception {
    keyring = new RdeKeyringModule().get();
    command.ghostryde = new Ghostryde(1024);
    command.rdeStagingDecryptionKey = keyring.getRdeStagingDecryptionKey();
    command.rdeStagingEncryptionKey = keyring.getRdeStagingEncryptionKey();
  }

  @Test
  public void testEncrypt_outputIsAFile_writesToFile() throws Exception {
    Path inFile = Paths.get(tmpDir.newFile("atrain.txt").toString());
    Path outFile = Paths.get(tmpDir.newFile().toString());
    Files.write(inFile, SONG_BY_CHRISTINA_ROSSETTI);
    Files.setLastModifiedTime(inFile, FileTime.fromMillis(MODIFIED_TIME.getMillis()));
    runCommand("--encrypt", "--input=" + inFile, "--output=" + outFile);
    DecodeResult decoded = Ghostryde.decode(
        Files.readAllBytes(outFile),
        keyring.getRdeStagingDecryptionKey());
    assertThat(decoded.getData()).isEqualTo(SONG_BY_CHRISTINA_ROSSETTI);
    assertThat(decoded.getName()).isEqualTo("atrain.txt");
    assertThat(decoded.getModified()).isEqualTo(MODIFIED_TIME);
  }

  @Test
  public void testEncrypt_outputIsADirectory_appendsGhostrydeExtension() throws Exception {
    Path inFile = Paths.get(tmpDir.newFile("atrain.txt").toString());
    Path outDir = Paths.get(tmpDir.newFolder().toString());
    Files.write(inFile, SONG_BY_CHRISTINA_ROSSETTI);
    Files.setLastModifiedTime(inFile, FileTime.fromMillis(MODIFIED_TIME.getMillis()));
    runCommand("--encrypt", "--input=" + inFile, "--output=" + outDir);
    Path outFile = outDir.resolve("atrain.txt.ghostryde");
    DecodeResult decoded = Ghostryde.decode(
        Files.readAllBytes(outFile),
        keyring.getRdeStagingDecryptionKey());
    assertThat(decoded.getData()).isEqualTo(SONG_BY_CHRISTINA_ROSSETTI);
    assertThat(decoded.getName()).isEqualTo("atrain.txt");
    assertThat(decoded.getModified()).isEqualTo(MODIFIED_TIME);
  }

  @Test
  public void testDecrypt_outputIsAFile_writesToFile() throws Exception {
    Path inFile = Paths.get(tmpDir.newFile().toString());
    Path outFile = Paths.get(tmpDir.newFile().toString());
    Files.write(inFile, Ghostryde.encode(
        SONG_BY_CHRISTINA_ROSSETTI,
        keyring.getRdeStagingEncryptionKey(),
        "atrain.txt",
        MODIFIED_TIME));
    runCommand("--decrypt", "--input=" + inFile, "--output=" + outFile);
    assertThat(Files.readAllBytes(outFile)).isEqualTo(SONG_BY_CHRISTINA_ROSSETTI);
    assertThat(Files.getLastModifiedTime(outFile))
        .isEqualTo(FileTime.fromMillis(MODIFIED_TIME.getMillis()));
  }

  @Test
  public void testDecrypt_outputIsADirectory_writesToFileFromInnerName() throws Exception {
    Path inFile = Paths.get(tmpDir.newFile().toString());
    Path outDir = Paths.get(tmpDir.newFolder().toString());
    Files.write(inFile, Ghostryde.encode(
        SONG_BY_CHRISTINA_ROSSETTI,
        keyring.getRdeStagingEncryptionKey(),
        "atrain.txt",
        MODIFIED_TIME));
    runCommand("--decrypt", "--input=" + inFile, "--output=" + outDir);
    Path outFile = outDir.resolve("atrain.txt");
    assertThat(Files.readAllBytes(outFile)).isEqualTo(SONG_BY_CHRISTINA_ROSSETTI);
    assertThat(Files.getLastModifiedTime(outFile))
        .isEqualTo(FileTime.fromMillis(MODIFIED_TIME.getMillis()));
  }
}
