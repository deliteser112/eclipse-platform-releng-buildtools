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

import com.google.common.flogger.FluentLogger;
import org.junit.runners.BlockJUnit4ClassRunner;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.InitializationError;
import org.junit.runners.model.Statement;

/**
 * A JUnit test runner which can retry each test up to 3 times.
 *
 * <p>This runner is for our visual regression to prevent flakes
 * during the run. The test is repeated multiple times until it
 * passes and it only fails if it failed 3 times.
 */
public class RepeatableRunner extends BlockJUnit4ClassRunner {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final int MAX_ATTEMPTS = 3;

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
      for (int i = 1; i <= MAX_ATTEMPTS; i++) {
        try {
          statement.evaluate();
          return;
        } catch (Throwable e) {
          logger.atSevere().log(
              "[%s] Attempt %d of %d failed!\n", method.getName(), i, MAX_ATTEMPTS);
          if (i == MAX_ATTEMPTS) {
            throw e;
          }
        }
      }
    }
  }
}
