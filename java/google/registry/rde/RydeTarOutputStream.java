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

import static com.google.common.base.Preconditions.checkArgument;

import google.registry.util.ImprovedOutputStream;
import google.registry.util.PosixTarHeader;
import java.io.IOException;
import java.io.OutputStream;
import javax.annotation.WillNotClose;
import org.joda.time.DateTime;

/**
 * Single-file POSIX tar archive creator that wraps an {@link OutputStream}.
 */
public class RydeTarOutputStream extends ImprovedOutputStream {

  private final long expectedSize;

  /**
   * Creates a new instance that outputs a tar archive.
   *
   * @param os is the upstream {@link OutputStream} which is not closed by this object
   * @param size is the length in bytes of the one file, which you will write to this object
   * @param modified is the {@link PosixTarHeader.Builder#setMtime mtime} you want to set
   * @param filename is the name of the one file that will be contained in this archive
   * @throws RuntimeException to rethrow {@link IOException}
   * @throws IllegalArgumentException if {@code size} is negative
   */
  public RydeTarOutputStream(
      @WillNotClose OutputStream os, long size, DateTime modified, String filename) {
    super("RydeTarOutputStream", os, false);
    checkArgument(size >= 0);
    this.expectedSize = size;
    checkArgument(filename.endsWith(".xml"),
        "Ryde expects tar archive to contain a filename with an '.xml' extension.");
    try {
      os.write(new PosixTarHeader.Builder()
          .setName(filename)
          .setSize(size)
          .setMtime(modified)
          .build()
          .getBytes());
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  /** Writes the end of archive marker. */
  @Override
  public void onClose() throws IOException {
    if (getBytesWritten() != expectedSize) {
      throw new IOException(
          String.format(
              "RydeTarOutputStream expected %,d bytes, but got %,d bytes",
              expectedSize, getBytesWritten()));
    }
    // Round up to a 512-byte boundary and another 1024-bytes to indicate end of archive.
    out.write(new byte[1024 + 512 - (int) (getBytesWritten() % 512L)]);
  }
}
