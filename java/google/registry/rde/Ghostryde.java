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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static org.bouncycastle.bcpg.CompressionAlgorithmTags.ZLIB;
import static org.bouncycastle.bcpg.SymmetricKeyAlgorithmTags.AES_128;
import static org.bouncycastle.jce.provider.BouncyCastleProvider.PROVIDER_NAME;
import static org.bouncycastle.openpgp.PGPLiteralData.BINARY;
import static org.joda.time.DateTimeZone.UTC;

import com.google.common.io.ByteStreams;
import google.registry.config.ConfigModule.Config;
import google.registry.util.FormattingLogger;
import google.registry.util.ImprovedInputStream;
import google.registry.util.ImprovedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.NoSuchAlgorithmException;
import java.security.ProviderException;
import java.security.SecureRandom;
import javax.annotation.CheckReturnValue;
import javax.annotation.Nullable;
import javax.annotation.WillCloseWhenClosed;
import javax.annotation.WillNotClose;
import javax.annotation.concurrent.Immutable;
import javax.annotation.concurrent.NotThreadSafe;
import javax.inject.Inject;
import org.bouncycastle.openpgp.PGPCompressedData;
import org.bouncycastle.openpgp.PGPCompressedDataGenerator;
import org.bouncycastle.openpgp.PGPEncryptedDataGenerator;
import org.bouncycastle.openpgp.PGPEncryptedDataList;
import org.bouncycastle.openpgp.PGPException;
import org.bouncycastle.openpgp.PGPLiteralData;
import org.bouncycastle.openpgp.PGPLiteralDataGenerator;
import org.bouncycastle.openpgp.PGPObjectFactory;
import org.bouncycastle.openpgp.PGPPrivateKey;
import org.bouncycastle.openpgp.PGPPublicKey;
import org.bouncycastle.openpgp.PGPPublicKeyEncryptedData;
import org.bouncycastle.openpgp.bc.BcPGPObjectFactory;
import org.bouncycastle.openpgp.operator.bc.BcPublicKeyDataDecryptorFactory;
import org.bouncycastle.openpgp.operator.bc.BcPublicKeyKeyEncryptionMethodGenerator;
import org.bouncycastle.openpgp.operator.jcajce.JcePGPDataEncryptorBuilder;
import org.joda.time.DateTime;

/**
 * Utility class for reading and writing data in the ghostryde container format.
 *
 * <p>Whenever we stage sensitive data to cloud storage (like XML RDE deposit data), we
 * <a href="http://youtu.be/YPNJjL9iznY">GHOST RYDE IT</a> first to keep it safe from the prying
 * eyes of anyone with access to the <a href="https://cloud.google.com/console">Google Cloud
 * Console</a>.
 *
 * <p>This class has an unusual API that's designed to take advantage of Java 7 try-with-resource
 * statements to the greatest extent possible, while also maintaining security contracts at
 * compile-time.
 *
 * <p>Here's how you write a file:
 *
 * <pre>   {@code
 *   File in = new File("lol.txt");
 *   File out = new File("lol.txt.ghostryde");
 *   Ghostryde ghost = new Ghostryde(1024);
 *   try (OutputStream output = new FileOutputStream(out);
 *       Ghostryde.Encryptor encryptor = ghost.openEncryptor(output, publicKey);
 *       Ghostryde.Compressor kompressor = ghost.openCompressor(encryptor);
 *       OutputStream go = ghost.openOutput(kompressor, in.getName(), DateTime.now(UTC));
 *       InputStream input = new FileInputStream(in)) &lbrace;
 *     ByteStreams.copy(input, go);
 *   &rbrace;}</pre>
 *
 * <p>Here's how you read a file:
 *
 * <pre>   {@code
 *   File in = new File("lol.txt.ghostryde");
 *   File out = new File("lol.txt");
 *   Ghostryde ghost = new Ghostryde(1024);
 *   try (InputStream fileInput = new FileInputStream(in);
 *       Ghostryde.Decryptor decryptor = ghost.openDecryptor(fileInput, privateKey);
 *       Ghostryde.Decompressor decompressor = ghost.openDecompressor(decryptor);
 *       Ghostryde.Input input = ghost.openInput(decompressor);
 *       OutputStream fileOutput = new FileOutputStream(out)) &lbrace;
 *     System.out.println("name = " + input.getName());
 *     System.out.println("modified = " + input.getModified());
 *     ByteStreams.copy(input, fileOutput);
 *   &rbrace;}</pre>
 *
 * <h2>Simple API</h2>
 *
 * <p>If you're writing test code or are certain your data can fit in memory, you might find these
 * static methods more convenient:
 *
 * <pre>   {@code
 *   byte[] data = "hello kitty".getBytes(UTF_8);
 *   byte[] blob = Ghostryde.encode(data, publicKey, "lol.txt", DateTime.now(UTC));
 *   Ghostryde.Result result = Ghostryde.decode(blob, privateKey);
 *   }</pre>
 *
 * <h2>GhostRYDE Format</h2>
 *
 * <p>A {@code .ghostryde} file is the exact same thing as a {@code .gpg} file, except the OpenPGP
 * message layers will always be present and in a specific order. You can analyse the layers on the
 * command-line using the {@code gpg --list-packets blah.ghostryde} command.
 *
 * <p>Ghostryde is different from RyDE in the sense that ghostryde is only used for <i>internal</i>
 * storage; whereas RyDE is meant to protect data being stored by a third-party.
 */
@Immutable
public final class Ghostryde {

  private static final FormattingLogger logger = FormattingLogger.getLoggerForCallerClass();

  /**
   * Compression algorithm to use when creating ghostryde files.
   *
   * <p>We're going to use ZLIB since it's better than ZIP.
   *
   * @see org.bouncycastle.bcpg.CompressionAlgorithmTags
   */
  static final int COMPRESSION_ALGORITHM = ZLIB;

  /**
   * Symmetric encryption cipher to use when creating ghostryde files.
   *
   * <p>We're going to use AES-128 just like {@link RydePgpEncryptionOutputStream}, although we
   * aren't forced to use this algorithm by the ICANN RFCs since this is an internal format.
   *
   * @see org.bouncycastle.bcpg.SymmetricKeyAlgorithmTags
   */
  static final int CIPHER = AES_128;

  /**
   * Unlike {@link RydePgpEncryptionOutputStream}, we're going to enable the integrity packet
   * because it makes GnuPG happy. It's also probably necessary to prevent tampering since we
   * don't sign ghostryde files.
   */
  static final boolean USE_INTEGRITY_PACKET = true;

  /**
   * The source of random bits. You are strongly discouraged from changing this value because at
   * Google it's configured to use {@code /dev/&lbrace;,u&rbrace;random} in production and somehow
   * magically go fast and not drain entropy in the testing environment.
   *
   * @see SecureRandom#getInstance(String)
   */
  static final String RANDOM_SOURCE = "NativePRNG";

  /**
   * Creates a ghostryde file from an in-memory byte array.
   *
   * @throws PGPException
   * @throws IOException
   */
  public static byte[] encode(byte[] data, PGPPublicKey key, String name, DateTime modified)
      throws IOException, PGPException {
    checkNotNull(data, "data");
    checkArgument(key.isEncryptionKey(), "not an encryption key");
    Ghostryde ghost = new Ghostryde(1024 * 64);
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    try (Encryptor encryptor = ghost.openEncryptor(output, key);
        Compressor kompressor = ghost.openCompressor(encryptor);
        OutputStream go = ghost.openOutput(kompressor, name, modified)) {
      go.write(data);
    }
    return output.toByteArray();
  }

  /**
   * Deciphers a ghostryde file from an in-memory byte array.
   *
   * @throws PGPException
   * @throws IOException
   */
  public static DecodeResult decode(byte[] data, PGPPrivateKey key)
      throws IOException, PGPException {
    checkNotNull(data, "data");
    Ghostryde ghost = new Ghostryde(1024 * 64);
    ByteArrayInputStream dataStream = new ByteArrayInputStream(data);
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    String name;
    DateTime modified;
    try (Decryptor decryptor = ghost.openDecryptor(dataStream, key);
        Decompressor decompressor = ghost.openDecompressor(decryptor);
        Input input = ghost.openInput(decompressor)) {
      name = input.getName();
      modified = input.getModified();
      ByteStreams.copy(input, output);
    }
    return new DecodeResult(output.toByteArray(), name, modified);
  }

  /** Result class for the {@link Ghostryde#decode(byte[], PGPPrivateKey)} method. */
  @Immutable
  public static final class DecodeResult {
    private final byte[] data;
    private final String name;
    private final DateTime modified;

    DecodeResult(byte[] data, String name, DateTime modified) {
      this.data = checkNotNull(data, "data");
      this.name = checkNotNull(name, "name");
      this.modified = checkNotNull(modified, "modified");
    }

    /** Returns the decoded ghostryde content bytes. */
    public byte[] getData() {
      return data;
    }

    /** Returns the name of the original file, taken from the literal data packet. */
    public String getName() {
      return name;
    }

    /** Returns the time this file was created or modified, take from the literal data packet. */
    public DateTime getModified() {
      return modified;
    }
  }

  /**
   * PGP literal file {@link InputStream}.
   *
   * @see Ghostryde#openInput(Decompressor)
   */
  @NotThreadSafe
  public static final class Input extends ImprovedInputStream {
    private final String name;
    private final DateTime modified;

    Input(@WillCloseWhenClosed InputStream input, String name, DateTime modified) {
      super(input);
      this.name = checkNotNull(name, "name");
      this.modified = checkNotNull(modified, "modified");
    }

    /** Returns the name of the original file, taken from the literal data packet. */
    public String getName() {
      return name;
    }

    /** Returns the time this file was created or modified, take from the literal data packet. */
    public DateTime getModified() {
      return modified;
    }
  }

  /**
   * PGP literal file {@link OutputStream}.
   *
   * <p>This class isn't needed for ordering safety, but is included regardless for consistency and
   * to improve the appearance of log messages.
   *
   * @see Ghostryde#openOutput(Compressor, String, DateTime)
   */
  @NotThreadSafe
  public static final class Output extends ImprovedOutputStream {
    Output(@WillCloseWhenClosed OutputStream os) {
      super(os);
    }
  }

  /**
   * Encryption {@link OutputStream}.
   *
   * <p>This type exists to guarantee {@code open*()} methods are called in the correct order.
   *
   * @see Ghostryde#openEncryptor(OutputStream, PGPPublicKey)
   */
  @NotThreadSafe
  public static final class Encryptor extends ImprovedOutputStream {
    Encryptor(@WillCloseWhenClosed OutputStream os) {
      super(os);
    }
  }

  /**
   * Decryption {@link InputStream}.
   *
   * <p>This type exists to guarantee {@code open*()} methods are called in the correct order.
   *
   * @see Ghostryde#openDecryptor(InputStream, PGPPrivateKey)
   */
  @NotThreadSafe
  public static final class Decryptor extends ImprovedInputStream {
    private final PGPPublicKeyEncryptedData crypt;

    Decryptor(@WillCloseWhenClosed InputStream input, PGPPublicKeyEncryptedData crypt) {
      super(input);
      this.crypt = checkNotNull(crypt, "crypt");
    }

    /**
     * Verifies that the ciphertext wasn't corrupted or tampered with.
     *
     * <p>Note: If {@link Ghostryde#USE_INTEGRITY_PACKET} is {@code true}, any ghostryde file
     * without an integrity packet will be considered invalid and an exception will be thrown.
     *
     * @throws IllegalStateException to propagate {@link PGPException}
     * @throws IOException
     */
    @Override
    protected void onClose() throws IOException {
      if (USE_INTEGRITY_PACKET) {
        try {
          if (!crypt.verify()) {
            throw new PGPException("ghostryde integrity check failed: possible tampering D:");
          }
        } catch (PGPException e) {
          throw new IllegalStateException(e);
        }
      }
    }
  }

  /**
   * Compression {@link OutputStream}.
   *
   * <p>This type exists to guarantee {@code open*()} methods are called in the correct order.
   *
   * @see Ghostryde#openCompressor(Encryptor)
   */
  @NotThreadSafe
  public static final class Compressor extends ImprovedOutputStream {
    Compressor(@WillCloseWhenClosed OutputStream os) {
      super(os);
    }
  }

  /**
   * Decompression {@link InputStream}.
   *
   * <p>This type exists to guarantee {@code open*()} methods are called in the correct order.
   *
   * @see Ghostryde#openDecompressor(Decryptor)
   */
  @NotThreadSafe
  public static final class Decompressor extends ImprovedInputStream {
    Decompressor(@WillCloseWhenClosed InputStream input) {
      super(input);
    }
  }

  private final int bufferSize;

  /** Constructs a new {@link Ghostryde} object. */
  @Inject
  public Ghostryde(
      @Config("rdeGhostrydeBufferSize") int bufferSize) {
    checkArgument(bufferSize > 0, "bufferSize");
    this.bufferSize = bufferSize;
  }

  /**
   * Opens a new {@link Encryptor} (Writing Step 1/3)
   *
   * <p>This is the first step in creating a ghostryde file. After this method, you'll want to
   * call {@link #openCompressor(Encryptor)}.
   *
   * @param os is the upstream {@link OutputStream} to which the result is written.
   * @param publicKey is the public encryption key of the recipient.
   * @throws IOException
   * @throws PGPException
   */
  @CheckReturnValue
  public Encryptor openEncryptor(@WillNotClose OutputStream os, PGPPublicKey publicKey)
      throws IOException, PGPException {
    PGPEncryptedDataGenerator encryptor = new PGPEncryptedDataGenerator(
        new JcePGPDataEncryptorBuilder(CIPHER)
           .setWithIntegrityPacket(USE_INTEGRITY_PACKET)
           .setSecureRandom(getRandom())
           .setProvider(PROVIDER_NAME));
    encryptor.addMethod(new BcPublicKeyKeyEncryptionMethodGenerator(publicKey));
    return new Encryptor(encryptor.open(os, new byte[bufferSize]));
  }

  /** Does stuff. */
  private SecureRandom getRandom() {
    SecureRandom random;
    try {
      random = SecureRandom.getInstance(RANDOM_SOURCE);
    } catch (NoSuchAlgorithmException e) {
      throw new ProviderException(e);
    }
    return random;
  }

  /**
   * Opens a new {@link Compressor} (Writing Step 2/3)
   *
   * <p>This is the second step in creating a ghostryde file. After this method, you'll want to
   * call {@link #openOutput(Compressor, String, DateTime)}.
   *
   * @param os is the value returned by {@link #openEncryptor(OutputStream, PGPPublicKey)}.
   * @throws IOException
   * @throws PGPException
   */
  @CheckReturnValue
  public Compressor openCompressor(@WillNotClose Encryptor os) throws IOException, PGPException {
    PGPCompressedDataGenerator kompressor = new PGPCompressedDataGenerator(COMPRESSION_ALGORITHM);
    return new Compressor(kompressor.open(os, new byte[bufferSize]));
  }

  /**
   * Opens an {@link OutputStream} to which the actual data should be written (Writing Step 3/3)
   *
   * <p>This is the third and final step in creating a ghostryde file. You'll want to write data
   * to the returned object.
   *
   * @param os is the value returned by {@link #openCompressor(Encryptor)}.
   * @param name is a filename for your data which gets written in the literal tag.
   * @param modified is a timestamp for your data which gets written to the literal tags.
   * @throws IOException
   */
  @CheckReturnValue
  public Output openOutput(@WillNotClose Compressor os, String name, DateTime modified)
      throws IOException {
    return new Output(new PGPLiteralDataGenerator().open(
        os, BINARY, name, modified.toDate(), new byte[bufferSize]));
  }

  /**
   * Opens a new {@link Decryptor} (Reading Step 1/3)
   *
   * <p>This is the first step in opening a ghostryde file. After this method, you'll want to
   * call {@link #openDecompressor(Decryptor)}.
   *
   * @param input is an {@link InputStream} of the ghostryde file data.
   * @param privateKey is the private encryption key of the recipient (which is us!)
   * @throws IOException
   * @throws PGPException
   */
  @CheckReturnValue
  public Decryptor openDecryptor(@WillNotClose InputStream input, PGPPrivateKey privateKey)
      throws IOException, PGPException {
    checkNotNull(privateKey, "privateKey");
    PGPObjectFactory fact = new BcPGPObjectFactory(checkNotNull(input, "input"));
    PGPEncryptedDataList crypts = pgpCast(fact.nextObject(), PGPEncryptedDataList.class);
    checkState(crypts.size() > 0);
    if (crypts.size() > 1) {
      logger.warningfmt("crypts.size() is %d (should be 1)", crypts.size());
    }
    PGPPublicKeyEncryptedData crypt = pgpCast(crypts.get(0), PGPPublicKeyEncryptedData.class);
    if (crypt.getKeyID() != privateKey.getKeyID()) {
      throw new PGPException(String.format(
          "Message was encrypted for keyid %x but ours is %x",
          crypt.getKeyID(), privateKey.getKeyID()));
    }
    return new Decryptor(
        crypt.getDataStream(new BcPublicKeyDataDecryptorFactory(privateKey)),
        crypt);
  }

  /**
   * Opens a new {@link Decompressor} (Reading Step 2/3)
   *
   * <p>This is the second step in reading a ghostryde file. After this method, you'll want to
   * call {@link #openInput(Decompressor)}.
   *
   * @param input is the value returned by {@link #openDecryptor}.
   * @throws IOException
   * @throws PGPException
   */
  @CheckReturnValue
  public Decompressor openDecompressor(@WillNotClose Decryptor input)
      throws IOException, PGPException {
    PGPObjectFactory fact = new BcPGPObjectFactory(checkNotNull(input, "input"));
    PGPCompressedData compressed = pgpCast(fact.nextObject(), PGPCompressedData.class);
    return new Decompressor(compressed.getDataStream());
  }

  /**
   * Opens a new {@link Input} for reading the original contents (Reading Step 3/3)
   *
   * <p>This is the final step in reading a ghostryde file. After calling this method, you should
   * call the read methods on the returned {@link InputStream}.
   *
   * @param input is the value returned by {@link #openDecompressor}.
   * @throws IOException
   * @throws PGPException
   */
  @CheckReturnValue
  public Input openInput(@WillNotClose Decompressor input) throws IOException, PGPException {
    PGPObjectFactory fact = new BcPGPObjectFactory(checkNotNull(input, "input"));
    PGPLiteralData literal = pgpCast(fact.nextObject(), PGPLiteralData.class);
    DateTime modified = new DateTime(literal.getModificationTime(), UTC);
    return new Input(literal.getDataStream(), literal.getFileName(), modified);
  }

  /** Safely extracts an object from an OpenPGP message. */
  private static <T> T pgpCast(@Nullable Object object, Class<T> expect) throws PGPException {
    if (object == null) {
      throw new PGPException(String.format(
          "Expected %s but out of objects", expect.getSimpleName()));
    }
    if (!expect.isAssignableFrom(object.getClass())) {
      throw new PGPException(String.format(
          "Expected %s but got %s", expect.getSimpleName(), object.getClass().getSimpleName()));
    }
    return expect.cast(object);
  }
}
