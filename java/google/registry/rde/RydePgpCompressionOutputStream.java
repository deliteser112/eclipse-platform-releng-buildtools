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

package google.registry.rde;

import static org.bouncycastle.bcpg.CompressionAlgorithmTags.ZIP;

import google.registry.util.ImprovedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import javax.annotation.WillNotClose;
import org.bouncycastle.openpgp.PGPCompressedDataGenerator;
import org.bouncycastle.openpgp.PGPException;

/**
 * OpenPGP compression service that wraps an {@link OutputStream}.
 *
 * <p>This uses the ZIP compression algorithm per the ICANN escrow specification.
 */
public class RydePgpCompressionOutputStream extends ImprovedOutputStream {

  private static final int BUFFER_SIZE = 64 * 1024;

  /**
   * Creates a new instance that compresses data.
   *
   * @param os is the upstream {@link OutputStream} which is not closed by this object
   * @throws RuntimeException to rethrow {@link PGPException} and {@link IOException}
   */
  public RydePgpCompressionOutputStream(
      @WillNotClose OutputStream os) {
    super("RydePgpCompressionOutputStream", createDelegate(BUFFER_SIZE, os));
  }

  private static OutputStream createDelegate(int bufferSize, OutputStream os) {
    try {
      return new PGPCompressedDataGenerator(ZIP).open(os, new byte[bufferSize]);
    } catch (IOException | PGPException e) {
      throw new RuntimeException(e);
    }
  }
}
