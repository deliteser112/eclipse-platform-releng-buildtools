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

package google.registry.rde;

import static com.google.common.base.Preconditions.checkArgument;
import static org.bouncycastle.openpgp.PGPLiteralData.BINARY;

import com.google.auto.factory.AutoFactory;
import com.google.auto.factory.Provided;
import google.registry.config.ConfigModule.Config;
import google.registry.util.ImprovedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import javax.annotation.WillNotClose;
import org.bouncycastle.openpgp.PGPLiteralDataGenerator;
import org.joda.time.DateTime;

/**
 * OpenPGP literal data layer generator that wraps {@link OutputStream}.
 *
 * <p>OpenPGP messages are like an onion; there can be many layers like compression and encryption.
 * It's important to wrap out plaintext in a literal data layer such as this so the code that's
 * unwrapping the onion knows when to stop.
 *
 * <p>According to escrow spec, the PGP message should contain a single tar file.
 */
@AutoFactory(allowSubclasses = true)
public class RydePgpFileOutputStream extends ImprovedOutputStream {

  /**
   * Creates a new instance for a particular file.
   *
   * @param os is the upstream {@link OutputStream} which is not closed by this object
   * @throws IllegalArgumentException if {@code filename} isn't a {@code .tar} file
   * @throws RuntimeException to rethrow {@link IOException}
   */
  public RydePgpFileOutputStream(
      @Provided @Config("rdeRydeBufferSize") Integer bufferSize,
      @WillNotClose OutputStream os,
      DateTime modified,
      String filename) {
    super(createDelegate(bufferSize, os, modified, filename));
  }

  private static OutputStream
      createDelegate(int bufferSize, OutputStream os, DateTime modified, String filename) {
    try {
      checkArgument(filename.endsWith(".tar"),
          "Ryde PGP message should contain a tar file.");
      return new PGPLiteralDataGenerator().open(
          os, BINARY, filename, modified.toDate(), new byte[bufferSize]);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}
