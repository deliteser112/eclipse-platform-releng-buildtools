// Copyright 2019 The Nomulus Authors. All Rights Reserved.
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

package google.registry.webdriver;

import static com.google.common.base.Preconditions.checkState;

import com.google.common.flogger.FluentLogger;
import org.junit.runners.BlockJUnit4ClassRunner;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.InitializationError;
import org.junit.runners.model.Statement;

/**
 * A JUnit test runner which can retry each test up to 3 times.
 *
 * <p>This runner is for our visual regression to prevent flakes during the run. The test is
 * repeated multiple times until it passes and it only fails if it failed 3 times.
 */
public class RepeatableRunner extends BlockJUnit4ClassRunner {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final boolean RUN_ALL_ATTEMPTS =
      Boolean.parseBoolean(System.getProperty("test.screenshot.runAllAttempts", "false"));

  private static final int MAX_ATTEMPTS =
      Integer.parseInt(System.getProperty("test.screenshot.maxAttempts", "3"));

  // TODO(b/127984872): Find an elegant way to pass the index of attempt to the test
  public static int currentAttemptIndex;

  /** Constructs a new instance of the default runner */
  public RepeatableRunner(Class<?> klass) throws InitializationError {
    super(klass);
  }

  @Override
  protected Statement methodBlock(FrameworkMethod method) {
    Statement statement = super.methodBlock(method);
    return new RepeatableStatement(statement, method);
  }

  private static class RepeatableStatement extends Statement {

    private Statement statement;
    private FrameworkMethod method;

    public RepeatableStatement(Statement statement, FrameworkMethod method) {
      this.statement = statement;
      this.method = method;
    }

    @Override
    public void evaluate() throws Throwable {
      checkState(MAX_ATTEMPTS > 0);
      int numSuccess = 0, numFailure = 0;
      Throwable firstException = null;
      for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
        currentAttemptIndex = attempt;
        try {
          statement.evaluate();
          numSuccess++;
          if (RUN_ALL_ATTEMPTS) {
            logger.atInfo().log(
                "[%s] Attempt %d of %d succeeded!\n", method.getName(), attempt, MAX_ATTEMPTS);
            continue;
          }
          return;
        } catch (Throwable e) {
          numFailure++;
          if (firstException == null) {
            firstException = e;
          }
          logger.atWarning().log(
              "[%s] Attempt %d of %d failed!\n", method.getName(), attempt, MAX_ATTEMPTS);
        }
      }
      logger.atInfo().log(
          "Test [%s] was executed %d times, %d attempts succeeded and %d attempts failed.",
          method.getName(), numSuccess + numFailure, numSuccess, numFailure);
      if (numSuccess == 0) {
        logger.atSevere().log(
            "[%s] didn't pass after all %d attempts failed!\n", method.getName(), MAX_ATTEMPTS);
        throw firstException;
      }
    }
  }
}
