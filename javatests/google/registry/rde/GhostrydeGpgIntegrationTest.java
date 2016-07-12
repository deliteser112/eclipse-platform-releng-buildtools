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

package google.registry.rde;

import static com.google.common.base.Strings.repeat;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;
import static google.registry.testing.SystemInfo.hasCommand;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assume.assumeTrue;

import com.google.common.io.CharStreams;
import google.registry.keyring.api.Keyring;
import google.registry.testing.BouncyCastleProviderRule;
import google.registry.testing.GpgSystemCommandRule;
import google.registry.testing.ShardableTestCase;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import org.bouncycastle.openpgp.PGPPublicKey;
import org.joda.time.DateTime;
import org.junit.Rule;
import org.junit.experimental.theories.DataPoints;
import org.junit.experimental.theories.Theories;
import org.junit.experimental.theories.Theory;
import org.junit.runner.RunWith;

/** GnuPG integration tests for {@link Ghostryde}. */
@RunWith(Theories.class)
@SuppressWarnings("resource")
public class GhostrydeGpgIntegrationTest extends ShardableTestCase {

  @Rule
  public final BouncyCastleProviderRule bouncy = new BouncyCastleProviderRule();

  @Rule
  public final GpgSystemCommandRule gpg = new GpgSystemCommandRule(
      RdeTestData.get("pgp-public-keyring.asc"),
      RdeTestData.get("pgp-private-keyring-registry.asc"));

  @DataPoints
  public static GpgCommand[] commands = new GpgCommand[] {
    new GpgCommand("gpg"),
    new GpgCommand("gpg2"),
  };

  @DataPoints
  public static BufferSize[] bufferSizes = new BufferSize[] {
    new BufferSize(1),
    new BufferSize(7),
  };

  @DataPoints
  public static Filename[] filenames = new Filename[] {
    new Filename("lol.txt"),
    // new Filename("(◕‿◕).txt"),  // gpg displays this with zany hex characters.
  };

  @DataPoints
  public static Content[] contents = new Content[] {
    new Content("(◕‿◕)"),
    new Content(repeat("Fanatics have their dreams, wherewith they weave\n", 1000)),
    new Content("\0yolo"),
    new Content(""),
  };

  @Theory
  public void test(GpgCommand cmd, BufferSize bufferSize, Filename filename, Content content)
      throws Exception {
    assumeTrue(hasCommand(cmd.get() + " --version"));
    Keyring keyring = new RdeKeyringModule().get();
    PGPPublicKey publicKey = keyring.getRdeStagingEncryptionKey();
    File file = new File(gpg.getCwd(), "love.gpg");
    byte[] data = content.get().getBytes(UTF_8);
    DateTime mtime = DateTime.parse("1984-12-18T00:30:00Z");

    Ghostryde ghost = new Ghostryde(bufferSize.get());
    try (OutputStream output = new FileOutputStream(file);
        Ghostryde.Encryptor encryptor = ghost.openEncryptor(output, publicKey);
        Ghostryde.Compressor kompressor = ghost.openCompressor(encryptor);
        OutputStream os = ghost.openOutput(kompressor, filename.get(), mtime)) {
      os.write(data);
    }

    Process pid = gpg.exec(cmd.get(), "--list-packets", file.getPath());
    String stdout = CharStreams.toString(new InputStreamReader(pid.getInputStream(), UTF_8));
    String stderr = CharStreams.toString(new InputStreamReader(pid.getErrorStream(), UTF_8));
    assertWithMessage(stderr).that(pid.waitFor()).isEqualTo(0);
    assertThat(stdout).contains(":compressed packet:");
    assertThat(stdout).contains(":encrypted data packet:");
    assertThat(stdout).contains("version 3, algo 1, keyid A59C132F3589A1D5");
    assertThat(stdout).contains("name=\"" + filename.get() + "\"");
    assertThat(stderr).contains("encrypted with 2048-bit RSA key, ID 3589A1D5");

    pid = gpg.exec(cmd.get(), "--use-embedded-filename", file.getPath());
    stderr = CharStreams.toString(new InputStreamReader(pid.getErrorStream(), UTF_8));
    assertWithMessage(stderr).that(pid.waitFor()).isEqualTo(0);
    File dataFile = new File(gpg.getCwd(), filename.get());
    assertThat(dataFile.exists()).isTrue();
    assertThat(slurp(dataFile)).isEqualTo(content.get());
  }

  private String slurp(File file) throws FileNotFoundException, IOException {
    return CharStreams.toString(new InputStreamReader(new FileInputStream(file), UTF_8));
  }

  private static class GpgCommand {
    private final String value;

    GpgCommand(String value) {
      this.value = value;
    }

    String get() {
      return value;
    }
  }

  private static class BufferSize {
    private final int value;

    BufferSize(int value) {
      this.value = value;
    }

    int get() {
      return value;
    }
  }

  private static class Filename {
    private final String value;

    Filename(String value) {
      this.value = value;
    }

    String get() {
      return value;
    }
  }

  private static class Content {
    private final String value;

    Content(String value) {
      this.value = value;
    }

    String get() {
      return value;
    }
  }
}
