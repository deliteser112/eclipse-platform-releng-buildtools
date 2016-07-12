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

package google.registry.util;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import javax.annotation.OverridingMethodsMustInvokeSuper;
import javax.annotation.WillCloseWhenClosed;
import javax.annotation.concurrent.NotThreadSafe;

/**
 * {@link OutputStream} wrapper that offers some additional magic.
 *
 * <ul>
 * <li>Byte counting
 * <li>Always {@link #flush()} on {@link #close()}
 * <li>Check expected byte count when closed (Optional)
 * <li>Close original {@link OutputStream} when closed (Optional)
 * <li>Overridable {@link #onClose()} method
 * <li>Throws {@link NullPointerException} if written after {@link #close()}
 * </ul>
 *
 * @see ImprovedInputStream
 * @see com.google.common.io.CountingOutputStream
 */
@NotThreadSafe
public class ImprovedOutputStream extends FilterOutputStream {

  private static final FormattingLogger logger = FormattingLogger.getLoggerForCallerClass();

  private long count;
  private final long expected;
  private final boolean shouldClose;

  public ImprovedOutputStream(@WillCloseWhenClosed OutputStream out) {
    this(out, true, -1);
  }

  public ImprovedOutputStream(OutputStream out, boolean shouldClose, long expected) {
    super(checkNotNull(out, "out"));
    checkArgument(expected >= -1, "expected >= 0 or -1");
    this.shouldClose = shouldClose;
    this.expected = expected;
  }

  /** Returns the number of bytes that have been written to this stream thus far. */
  public long getBytesWritten() {
    return count;
  }

  /** @see java.io.FilterOutputStream#write(int) */
  @Override
  @OverridingMethodsMustInvokeSuper
  public void write(int b) throws IOException {
    out.write(b);
    ++count;
  }

  /** @see #write(byte[], int, int) */
  @Override
  public final void write(byte[] b) throws IOException {
    this.write(b, 0, b.length);
  }

  /** @see java.io.FilterOutputStream#write(byte[], int, int) */
  @Override
  @OverridingMethodsMustInvokeSuper
  public void write(byte[] b, int off, int len) throws IOException {
    out.write(b, off, len);
    count += len;
  }

  /**
   * Flushes, logs byte count, checks byte count (optional), closes (optional), and self-sabotages.
   *
   * <p>This method may not be overridden, use {@link #onClose()} instead.
   *
   * @see java.io.OutputStream#close()
   */
  @Override
  @NonFinalForTesting
  public void close() throws IOException {
    if (out == null) {
      return;
    }
    try {
      flush();
    } catch (IOException e) {
      logger.warningfmt(e, "%s.flush() failed", getClass().getSimpleName());
    }
    logger.infofmt("%s closed with %,d bytes written", getClass().getSimpleName(), count);
    if (expected != -1 && count != expected) {
      throw new IOException(String.format(
          "%s expected %,d bytes but got %,d bytes", getClass().getSimpleName(), expected, count));
    }
    onClose();
    if (shouldClose) {
      out.close();
    }
    out = null;
  }

  /**
   * Overridable method that's called by {@link #close()}.
   *
   * <p>This method does nothing by default.
   *
   * @throws IOException
   */
  protected void onClose() throws IOException {
    // Does nothing by default.
  }
}
