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

import static com.google.common.truth.Truth.assertThat;
import static java.util.Arrays.asList;

import com.google.common.collect.ImmutableSet;
import google.registry.testing.ExceptionRule;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link TeeOutputStream}. */
@RunWith(JUnit4.class)
public class TeeOutputStreamTest {

  @Rule
  public ExceptionRule thrown = new ExceptionRule();

  private final ByteArrayOutputStream outputA = new ByteArrayOutputStream();
  private final ByteArrayOutputStream outputB = new ByteArrayOutputStream();
  private final ByteArrayOutputStream outputC = new ByteArrayOutputStream();

  @Test
  public void testWrite_writesToMultipleStreams() throws Exception {
    // Write shared data using the tee output stream.
    try (OutputStream tee =
        new TeeOutputStream(asList(outputA, outputB, outputC))) {
      tee.write("hello ".getBytes());
      tee.write("hello world!".getBytes(), 6, 5);
      tee.write('!');
    }
    // Write some more data to the different streams - they should not have been closed.
    outputA.write("a".getBytes());
    outputB.write("b".getBytes());
    outputC.write("c".getBytes());
    // Check the results.
    assertThat(outputA.toString()).isEqualTo("hello world!a");
    assertThat(outputB.toString()).isEqualTo("hello world!b");
    assertThat(outputC.toString()).isEqualTo("hello world!c");
  }

  @Test
  @SuppressWarnings("resource")
  public void testConstructor_failsWithEmptyIterable() {
    thrown.expect(IllegalArgumentException.class);
    new TeeOutputStream(ImmutableSet.<OutputStream>of());
  }

  @Test
  public void testWriteInteger_failsAfterClose() throws Exception {
    OutputStream tee = new TeeOutputStream(asList(outputA));
    tee.close();
    thrown.expect(IllegalStateException.class, "outputstream closed");
    tee.write(1);
  }

  @Test
  public void testWriteByteArray_failsAfterClose() throws Exception {
    OutputStream tee = new TeeOutputStream(asList(outputA));
    tee.close();
    thrown.expect(IllegalStateException.class, "outputstream closed");
    tee.write("hello".getBytes());
  }

  @Test
  public void testWriteByteSubarray_failsAfterClose() throws Exception {
    OutputStream tee = new TeeOutputStream(asList(outputA));
    tee.close();
    thrown.expect(IllegalStateException.class, "outputstream closed");
    tee.write("hello".getBytes(), 1, 3);
  }
}
