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

package google.registry.tools;

import static com.google.common.base.Preconditions.checkArgument;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static org.joda.time.DateTimeZone.UTC;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.google.common.io.ByteStreams;
import google.registry.keyring.api.KeyModule.Key;
import google.registry.rde.Ghostryde;
import google.registry.tools.params.PathParameter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import javax.inject.Inject;
import org.bouncycastle.openpgp.PGPException;
import org.bouncycastle.openpgp.PGPPrivateKey;
import org.bouncycastle.openpgp.PGPPublicKey;
import org.joda.time.DateTime;

/** Command to encrypt/decrypt {@code .ghostryde} files. */
@Parameters(separators = " =", commandDescription = "Encrypt/decrypt a ghostryde file.")
final class GhostrydeCommand implements Command {

  @Parameter(
      names = {"-e", "--encrypt"},
      description = "Encrypt mode.")
  private boolean encrypt;

  @Parameter(
      names = {"-d", "--decrypt"},
      description = "Decrypt mode.")
  private boolean decrypt;

  @Parameter(
      names = {"-i", "--input"},
      description = "Input file.",
      validateWith = PathParameter.InputFile.class)
  private Path input = Paths.get("/dev/stdin");

  @Parameter(
      names = {"-o", "--output"},
      description = "Output file. If this is a directory, then in --encrypt mode, the output "
          + "filename will be the input filename with '.ghostryde' appended, and in --decrypt "
          + "mode, the output filename will be determined based on the name stored within the "
          + "archive.",
      validateWith = PathParameter.class)
  private Path output = Paths.get("/dev/stdout");

  @Inject
  Ghostryde ghostryde;

  @Inject
  @Key("rdeStagingEncryptionKey")
  PGPPublicKey rdeStagingEncryptionKey;

  @Inject
  @Key("rdeStagingDecryptionKey")
  PGPPrivateKey rdeStagingDecryptionKey;

  @Override
  public final void run() throws Exception {
    checkArgument(encrypt ^ decrypt, "Please specify either --encrypt or --decrypt");
    if (encrypt) {
      runEncrypt();
    } else {
      runDecrypt();
    }
  }

  private void runEncrypt() throws IOException, PGPException {
    Path outFile = Files.isDirectory(output)
        ? output.resolve(input.getFileName() + ".ghostryde")
        : output;
    try (OutputStream out = Files.newOutputStream(outFile);
        Ghostryde.Encryptor encryptor =
            ghostryde.openEncryptor(out, rdeStagingEncryptionKey);
        Ghostryde.Compressor kompressor = ghostryde.openCompressor(encryptor);
        Ghostryde.Output ghostOutput =
            ghostryde.openOutput(kompressor, input.getFileName().toString(),
                new DateTime(Files.getLastModifiedTime(input).toMillis(), UTC));
        InputStream in = Files.newInputStream(input)) {
      ByteStreams.copy(in, ghostOutput);
    }
  }

  private void runDecrypt() throws IOException, PGPException {
    try (InputStream in = Files.newInputStream(input);
        Ghostryde.Decryptor decryptor =
            ghostryde.openDecryptor(in, rdeStagingDecryptionKey);
        Ghostryde.Decompressor decompressor = ghostryde.openDecompressor(decryptor);
        Ghostryde.Input ghostInput = ghostryde.openInput(decompressor)) {
      Path outFile = Files.isDirectory(output)
          ? output.resolve(ghostInput.getName())
          : output;
      Files.copy(ghostInput, outFile, REPLACE_EXISTING);
      Files.setLastModifiedTime(outFile,
          FileTime.fromMillis(ghostInput.getModified().getMillis()));
    }
  }
}
