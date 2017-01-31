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

package google.registry.testing;

import static com.google.common.base.Preconditions.checkState;
import static com.google.common.base.Strings.nullToEmpty;
import static com.google.common.base.Throwables.getRootCause;
import static google.registry.testing.EppExceptionSubject.assertAboutEppExceptions;

import google.registry.flows.EppException;
import javax.annotation.Nullable;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

/**
 * A test rule similar to JUnit's {@code ExpectedException} rule that does extra checking to ensure
 * that {@link EppException} derivatives have EPP-compliant error messages.
 */
public class ExceptionRule implements TestRule {

  @Nullable
  Class<? extends Throwable> expectedExceptionClass;

  @Nullable
  String expectedMessage;

  private boolean useRootCause;

  @Override
  public Statement apply(final Statement base, Description description) {
    return new Statement() {
      @Override
      public void evaluate() throws Throwable {
        try {
          base.evaluate();
          if (expectedExceptionClass != null) {
            throw new AssertionError(String.format(
                "Expected test to throw %s%s",
                expectedExceptionClass.getSimpleName(),
                expectedMessage == null ? "" : (" with message: " + expectedMessage)));
          }
        } catch (Throwable e) {
          Throwable cause = useRootCause ? getRootCause(e) : e;
          if (expectedExceptionClass == null
              || !(expectedExceptionClass.isAssignableFrom(cause.getClass())
                  && nullToEmpty(cause.getMessage()).contains(nullToEmpty(expectedMessage)))) {
            throw e;  // We didn't expect this so pass it through.
          }
          if (e instanceof EppException) {
            assertAboutEppExceptions().that((EppException) e).marshalsToXml();
          }
        }
      }};
  }

  public void expect(Class<? extends Throwable> expectedExceptionClass) {
    checkState(this.expectedExceptionClass == null,
        "Don't use multiple `thrown.expect()` statements in your test.");
    this.expectedExceptionClass = expectedExceptionClass;
  }

  public void expect(Class<? extends Throwable> expectedExceptionClass, String expectedMessage) {
    expect(expectedExceptionClass);
    this.expectedMessage = expectedMessage;
  }

  public void expectRootCause(Class<? extends Throwable> expectedExceptionClass) {
    expect(expectedExceptionClass);
    this.useRootCause = true;
  }

  public void expectRootCause(
      Class<? extends Throwable> expectedExceptionClass, String expectedMessage) {
    expect(expectedExceptionClass, expectedMessage);
    this.useRootCause = true;
  }
}
