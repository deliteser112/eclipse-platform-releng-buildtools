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
import static google.registry.keyring.api.PgpHelper.KeyRequirement.ENCRYPT;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThan;
import static org.junit.Assume.assumeThat;

import com.google.common.io.ByteStreams;
import google.registry.keyring.api.Keyring;
import google.registry.rde.Ghostryde.DecodeResult;
import google.registry.testing.BouncyCastleProviderRule;
import google.registry.testing.ExceptionRule;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import org.bouncycastle.openpgp.PGPException;
import org.bouncycastle.openpgp.PGPKeyPair;
import org.bouncycastle.openpgp.PGPPrivateKey;
import org.bouncycastle.openpgp.PGPPublicKey;
import org.joda.time.DateTime;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.theories.DataPoints;
import org.junit.experimental.theories.Theories;
import org.junit.experimental.theories.Theory;
import org.junit.runner.RunWith;

/** Unit tests for {@link Ghostryde}. */
@RunWith(Theories.class)
@SuppressWarnings("resource")
public class GhostrydeTest {

  @Rule
  public final ExceptionRule thrown = new ExceptionRule();

  @Rule
  public final BouncyCastleProviderRule bouncy = new BouncyCastleProviderRule();

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
    new Content("hi"),
    new Content("(◕‿◕)"),
    new Content(repeat("Fanatics have their dreams, wherewith they weave.\n", 1000)),
    new Content("\0yolo"),
    new Content(""),
  };

  @Theory
  public void testSimpleApi(Filename filename, Content content) throws Exception {
    Keyring keyring = new RdeKeyringModule().get();
    byte[] data = content.get().getBytes(UTF_8);
    DateTime mtime = DateTime.parse("1984-12-18T00:30:00Z");
    PGPPublicKey publicKey = keyring.getRdeStagingEncryptionKey();
    PGPPrivateKey privateKey = keyring.getRdeStagingDecryptionKey();

    byte[] blob = Ghostryde.encode(data, publicKey, filename.get(), mtime);
    DecodeResult result = Ghostryde.decode(blob, privateKey);

    assertThat(result.getName()).isEqualTo(filename.get());
    assertThat(result.getModified()).isEqualTo(mtime);
    assertThat(new String(result.getData(), UTF_8)).isEqualTo(content.get());
  }

  @Theory
  public void testStreamingApi(BufferSize bufferSize, Filename filename, Content content)
      throws Exception {
    Keyring keyring = new RdeKeyringModule().get();
    byte[] data = content.get().getBytes(UTF_8);
    DateTime mtime = DateTime.parse("1984-12-18T00:30:00Z");
    PGPPublicKey publicKey = keyring.getRdeStagingEncryptionKey();
    PGPPrivateKey privateKey = keyring.getRdeStagingDecryptionKey();

    Ghostryde ghost = new Ghostryde(bufferSize.get());
    ByteArrayOutputStream bsOut = new ByteArrayOutputStream();
    try (Ghostryde.Encryptor encryptor = ghost.openEncryptor(bsOut, publicKey);
        Ghostryde.Compressor kompressor = ghost.openCompressor(encryptor);
        OutputStream output = ghost.openOutput(kompressor, filename.get(), mtime)) {
      output.write(data);
    }

    ByteArrayInputStream bsIn = new ByteArrayInputStream(bsOut.toByteArray());
    bsOut.reset();
    try (Ghostryde.Decryptor decryptor = ghost.openDecryptor(bsIn, privateKey);
        Ghostryde.Decompressor decompressor = ghost.openDecompressor(decryptor);
        Ghostryde.Input input = ghost.openInput(decompressor)) {
      assertThat(input.getName()).isEqualTo(filename.get());
      assertThat(input.getModified()).isEqualTo(mtime);
      ByteStreams.copy(input, bsOut);
    }
    assertThat(bsOut.size()).isEqualTo(data.length);

    assertThat(new String(bsOut.toByteArray(), UTF_8)).isEqualTo(content.get());
  }

  @Theory
  public void testEncryptOnly(Content content) throws Exception {
    Keyring keyring = new RdeKeyringModule().get();
    byte[] data = content.get().getBytes(UTF_8);
    PGPPublicKey publicKey = keyring.getRdeStagingEncryptionKey();
    PGPPrivateKey privateKey = keyring.getRdeStagingDecryptionKey();

    Ghostryde ghost = new Ghostryde(1024);
    ByteArrayOutputStream bsOut = new ByteArrayOutputStream();
    try (Ghostryde.Encryptor encryptor = ghost.openEncryptor(bsOut, publicKey)) {
      encryptor.write(data);
    }

    ByteArrayInputStream bsIn = new ByteArrayInputStream(bsOut.toByteArray());
    bsOut.reset();
    try (Ghostryde.Decryptor decryptor = ghost.openDecryptor(bsIn, privateKey)) {
      ByteStreams.copy(decryptor, bsOut);
    }

    assertThat(new String(bsOut.toByteArray(), UTF_8)).isEqualTo(content.get());
  }

  @Theory
  public void testEncryptCompressOnly(Content content) throws Exception {
    Keyring keyring = new RdeKeyringModule().get();
    PGPPublicKey publicKey = keyring.getRdeStagingEncryptionKey();
    PGPPrivateKey privateKey = keyring.getRdeStagingDecryptionKey();
    byte[] data = content.get().getBytes(UTF_8);

    Ghostryde ghost = new Ghostryde(1024);
    ByteArrayOutputStream bsOut = new ByteArrayOutputStream();
    try (Ghostryde.Encryptor encryptor = ghost.openEncryptor(bsOut, publicKey);
        Ghostryde.Compressor kompressor = ghost.openCompressor(encryptor)) {
      kompressor.write(data);
    }

    assertThat(new String(bsOut.toByteArray(), UTF_8)).isNotEqualTo(content.get());

    ByteArrayInputStream bsIn = new ByteArrayInputStream(bsOut.toByteArray());
    bsOut.reset();
    try (Ghostryde.Decryptor decryptor = ghost.openDecryptor(bsIn, privateKey);
        Ghostryde.Decompressor decompressor = ghost.openDecompressor(decryptor)) {
      ByteStreams.copy(decompressor, bsOut);
    }

    assertThat(new String(bsOut.toByteArray(), UTF_8)).isEqualTo(content.get());
  }

  @Theory
  public void testFailure_tampering(Content content) throws Exception {
    assumeThat(content.get().length(), is(greaterThan(100)));

    Keyring keyring = new RdeKeyringModule().get();
    PGPPublicKey publicKey = keyring.getRdeStagingEncryptionKey();
    PGPPrivateKey privateKey = keyring.getRdeStagingDecryptionKey();
    byte[] data = content.get().getBytes(UTF_8);
    DateTime mtime = DateTime.parse("1984-12-18T00:30:00Z");

    Ghostryde ghost = new Ghostryde(1024);
    ByteArrayOutputStream bsOut = new ByteArrayOutputStream();
    try (Ghostryde.Encryptor encryptor = ghost.openEncryptor(bsOut, publicKey);
        Ghostryde.Compressor kompressor = ghost.openCompressor(encryptor);
        OutputStream output = ghost.openOutput(kompressor, "lol", mtime)) {
      output.write(data);
    }

    byte[] ciphertext = bsOut.toByteArray();
    korruption(ciphertext, ciphertext.length / 2);
    thrown.expect(IllegalStateException.class, "tampering");

    ByteArrayInputStream bsIn = new ByteArrayInputStream(ciphertext);
    try (Ghostryde.Decryptor decryptor = ghost.openDecryptor(bsIn, privateKey)) {
      ByteStreams.copy(decryptor, ByteStreams.nullOutputStream());
    }
  }

  @Theory
  public void testFailure_corruption(Content content) throws Exception {
    assumeThat(content.get().length(), is(lessThan(100)));

    Keyring keyring = new RdeKeyringModule().get();
    PGPPublicKey publicKey = keyring.getRdeStagingEncryptionKey();
    PGPPrivateKey privateKey = keyring.getRdeStagingDecryptionKey();
    byte[] data = content.get().getBytes(UTF_8);
    DateTime mtime = DateTime.parse("1984-12-18T00:30:00Z");

    Ghostryde ghost = new Ghostryde(1024);
    ByteArrayOutputStream bsOut = new ByteArrayOutputStream();
    try (Ghostryde.Encryptor encryptor = ghost.openEncryptor(bsOut, publicKey);
        Ghostryde.Compressor kompressor = ghost.openCompressor(encryptor);
        OutputStream output = ghost.openOutput(kompressor, "lol", mtime)) {
      output.write(data);
    }

    byte[] ciphertext = bsOut.toByteArray();
    korruption(ciphertext, ciphertext.length / 2);
    thrown.expect(PGPException.class);

    ByteArrayInputStream bsIn = new ByteArrayInputStream(ciphertext);
    try (Ghostryde.Decryptor decryptor = ghost.openDecryptor(bsIn, privateKey)) {
      ByteStreams.copy(decryptor, ByteStreams.nullOutputStream());
    }
  }

  @Test
  public void testFailure_keyMismatch() throws Exception {
    RdeKeyringModule keyringModule = new RdeKeyringModule();
    byte[] data = "Fanatics have their dreams, wherewith they weave.".getBytes(UTF_8);
    DateTime mtime = DateTime.parse("1984-12-18T00:30:00Z");
    PGPKeyPair dsa1 = keyringModule.get("rde-unittest@registry.test", ENCRYPT);
    PGPKeyPair dsa2 = keyringModule.get("rde-unittest-dsa@registry.test", ENCRYPT);
    PGPPublicKey publicKey = dsa1.getPublicKey();
    PGPPrivateKey privateKey = dsa2.getPrivateKey();

    Ghostryde ghost = new Ghostryde(1024);
    ByteArrayOutputStream bsOut = new ByteArrayOutputStream();
    try (Ghostryde.Encryptor encryptor = ghost.openEncryptor(bsOut, publicKey);
        Ghostryde.Compressor kompressor = ghost.openCompressor(encryptor);
        OutputStream output = ghost.openOutput(kompressor, "lol", mtime)) {
      output.write(data);
    }

    thrown.expect(
        PGPException.class,
        "Message was encrypted for keyid a59c132f3589a1d5 but ours is c9598c84ec70b9fd");
    ByteArrayInputStream bsIn = new ByteArrayInputStream(bsOut.toByteArray());
    try (Ghostryde.Decryptor decryptor = ghost.openDecryptor(bsIn, privateKey)) {
      ByteStreams.copy(decryptor, ByteStreams.nullOutputStream());
    }
  }

  @Test
  @Ignore("Intentionally corrupting a PGP key is easier said than done >_>")
  public void testFailure_keyCorruption() throws Exception {
    RdeKeyringModule keyringModule = new RdeKeyringModule();
    byte[] data = "Fanatics have their dreams, wherewith they weave.".getBytes(UTF_8);
    DateTime mtime = DateTime.parse("1984-12-18T00:30:00Z");
    PGPKeyPair rsa = keyringModule.get("rde-unittest@registry.test", ENCRYPT);
    PGPPublicKey publicKey = rsa.getPublicKey();

    // Make the last byte of the private key off by one. muahahaha
    byte[] keyData = rsa.getPrivateKey().getPrivateKeyDataPacket().getEncoded();
    keyData[keyData.length - 1]++;
    PGPPrivateKey privateKey = new PGPPrivateKey(
        rsa.getKeyID(),
        rsa.getPrivateKey().getPublicKeyPacket(),
        rsa.getPrivateKey().getPrivateKeyDataPacket());

    Ghostryde ghost = new Ghostryde(1024);
    ByteArrayOutputStream bsOut = new ByteArrayOutputStream();
    try (Ghostryde.Encryptor encryptor = ghost.openEncryptor(bsOut, publicKey);
        Ghostryde.Compressor kompressor = ghost.openCompressor(encryptor);
        OutputStream output = ghost.openOutput(kompressor, "lol", mtime)) {
      output.write(data);
    }

    ByteArrayInputStream bsIn = new ByteArrayInputStream(bsOut.toByteArray());
    try (Ghostryde.Decryptor decryptor = ghost.openDecryptor(bsIn, privateKey)) {
      ByteStreams.copy(decryptor, ByteStreams.nullOutputStream());
    }
  }

  private void korruption(byte[] bytes, int position) {
    if (bytes[position] == 23) {
      bytes[position] = 7;
    } else {
      bytes[position] = 23;
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
