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

package google.registry.model.poll;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.testing.NullPointerTester;
import com.google.common.testing.NullPointerTester.Visibility;
import org.joda.time.DateTime;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link MessageQueueInfo}. */
@RunWith(JUnit4.class)
public final class MessageQueueInfoTest {

  @Test
  public void testDeadCodeWeDontWantToDelete() throws Exception {
    MessageQueueInfo mp = new MessageQueueInfo();
    mp.queueDate = DateTime.parse("1984-12-18TZ");
    assertThat(mp.getQueueDate()).isEqualTo(DateTime.parse("1984-12-18TZ"));
    mp.msg = "sloth";
    assertThat(mp.getMsg()).isEqualTo("sloth");
    mp.queueLength = 123;
    assertThat(mp.getQueueLength()).isEqualTo(123);
    mp.messageId = "adorable";
    assertThat(mp.getMessageId()).isEqualTo("adorable");
  }

  @Test
  public void testNullness() {
    NullPointerTester tester = new NullPointerTester();
    tester.testStaticMethods(MessageQueueInfo.class, Visibility.PROTECTED);
  }
}
