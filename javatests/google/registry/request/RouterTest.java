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

package google.registry.request;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.base.Function;
import com.google.common.base.Optional;
import google.registry.testing.ExceptionRule;
import java.util.concurrent.Callable;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link Router}. */
@RunWith(JUnit4.class)
public final class RouterTest {

  @Rule
  public final ExceptionRule thrown = new ExceptionRule();

  ////////////////////////////////////////////////////////////////////////////////////////////////

  public interface Empty {}

  @Test
  public void testRoute_noRoutes_returnsAbsent() throws Exception {
    assertThat(Router.create(Empty.class).route("")).isAbsent();
    assertThat(Router.create(Empty.class).route("/")).isAbsent();
  }

  ////////////////////////////////////////////////////////////////////////////////////////////////

  @Action(path = "/sloth")
  public final class SlothTask implements Runnable {
    @Override
    public void run() {}
  }

  public interface SlothComponent {
    SlothTask slothTask();
  }

  @Test
  public void testRoute_pathMatch_returnsRoute() throws Exception {
    Optional<Route> route = Router.create(SlothComponent.class).route("/sloth");
    assertThat(route).isPresent();
    assertThat(route.get().action().path()).isEqualTo("/sloth");
    assertThat(route.get().instantiator()).isInstanceOf(Function.class);
  }

  @Test
  public void testRoute_pathMismatch_returnsAbsent() throws Exception {
    assertThat(Router.create(SlothComponent.class).route("/doge")).isAbsent();
  }

  @Test
  public void testRoute_pathIsAPrefix_notAllowedByDefault() throws Exception {
    assertThat(Router.create(SlothComponent.class).route("/sloth/extra")).isAbsent();
  }

  ////////////////////////////////////////////////////////////////////////////////////////////////

  @Action(path = "/prefix", isPrefix = true)
  public final class PrefixTask implements Runnable {
    @Override
    public void run() {}
  }

  public interface PrefixComponent {
    PrefixTask prefixTask();
  }

  @Test
  public void testRoute_prefixMatches_returnsRoute() throws Exception {
    assertThat(Router.create(PrefixComponent.class).route("/prefix")).isPresent();
    assertThat(Router.create(PrefixComponent.class).route("/prefix/extra")).isPresent();
  }

  @Test
  public void testRoute_prefixDoesNotMatch_returnsAbsent() throws Exception {
    assertThat(Router.create(PrefixComponent.class).route("")).isAbsent();
    assertThat(Router.create(PrefixComponent.class).route("/")).isAbsent();
    assertThat(Router.create(PrefixComponent.class).route("/ulysses")).isAbsent();
    assertThat(Router.create(PrefixComponent.class).route("/man/of/sadness")).isAbsent();
  }

  ////////////////////////////////////////////////////////////////////////////////////////////////

  @Action(path = "/prefix/long", isPrefix = true)
  public final class LongTask implements Runnable {
    @Override
    public void run() {}
  }

  public interface LongPathComponent {
    PrefixTask prefixTask();
    LongTask longTask();
  }

  @Test
  public void testRoute_prefixAndLongPathMatch_returnsLongerPath() throws Exception {
    Optional<Route> route = Router.create(LongPathComponent.class).route("/prefix/long");
    assertThat(route).isPresent();
    assertThat(route.get().action().path()).isEqualTo("/prefix/long");
  }

  @Test
  public void testRoute_prefixAndLongerPrefixMatch_returnsLongerPrefix() throws Exception {
    Optional<Route> route = Router.create(LongPathComponent.class).route("/prefix/longer");
    assertThat(route).isPresent();
    assertThat(route.get().action().path()).isEqualTo("/prefix/long");
  }

  @Test
  public void testRoute_onlyShortPrefixMatches_returnsShortPrefix() throws Exception {
    Optional<Route> route = Router.create(LongPathComponent.class).route("/prefix/cat");
    assertThat(route).isPresent();
    assertThat(route.get().action().path()).isEqualTo("/prefix");
  }

  ////////////////////////////////////////////////////////////////////////////////////////////////

  public interface WeirdMethodsComponent {
    SlothTask hasAnArgumentWhichIsIgnored(boolean lol);
    Callable<?> notARunnableWhichIsIgnored();
  }

  @Test
  public void testRoute_methodsInComponentAreIgnored_noRoutes() throws Exception {
    assertThat(Router.create(WeirdMethodsComponent.class).route("/sloth")).isAbsent();
  }

  ////////////////////////////////////////////////////////////////////////////////////////////////

  @Action(path = "/samePathAsOtherTask")
  public final class DuplicateTask1 implements Runnable {
    @Override
    public void run() {}
  }

  @Action(path = "/samePathAsOtherTask")
  public final class DuplicateTask2 implements Runnable {
    @Override
    public void run() {}
  }

  public interface DuplicateComponent {
    DuplicateTask1 duplicateTask1();
    DuplicateTask2 duplicateTask2();
  }

  @Test
  public void testCreate_twoTasksWithSameMethodAndPath_resultsInError() throws Exception {
    thrown.expect(IllegalArgumentException.class, "Multiple entries with same key");
    Router.create(DuplicateComponent.class);
  }
}
