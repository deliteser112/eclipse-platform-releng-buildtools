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

package google.registry.tools.server;

import static com.google.common.truth.Truth.assertThat;
import static google.registry.testing.DatastoreHelper.createTlds;
import static javax.servlet.http.HttpServletResponse.SC_OK;

import google.registry.model.registry.label.PremiumList;
import google.registry.testing.AppEngineRule;
import google.registry.testing.ExceptionRule;
import google.registry.testing.FakeJsonResponse;
import org.joda.money.Money;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Unit tests for {@link UpdatePremiumListAction}.
 */
@RunWith(JUnit4.class)
public class UpdatePremiumListActionTest {

  @Rule
  public final AppEngineRule appEngine = AppEngineRule.builder()
      .withDatastore()
      .build();

  @Rule
  public final ExceptionRule thrown = new ExceptionRule();

  UpdatePremiumListAction action;
  FakeJsonResponse response;

  @Before
  public void init() throws Exception {
    createTlds("foo", "xn--q9jyb4c", "how");
    action = new UpdatePremiumListAction();
    response = new FakeJsonResponse();
    action.response = response;
  }

  @Test
  public void test_invalidRequest_missingInput_returnsErrorStatus() throws Exception {
    action.name = "foo";
    action.run();
    assertThat(response.getResponseMap().get("status")).isEqualTo("error");
  }

  @Test
  public void test_invalidRequest_listDoesNotExist_returnsErrorStatus() throws Exception {
    action.name = "bamboozle";
    action.inputData = "richer,JPY 5000";
    action.run();
    assertThat(response.getResponseMap().get("status")).isEqualTo("error");
    Object obj = response.getResponseMap().get("error");
    assertThat(obj).isInstanceOf(String.class);
    String error = obj.toString();
    assertThat(error).contains("Could not update premium list");
  }

  @Test
  public void test_success() throws Exception {
    action.name = "foo";
    action.inputData = "rich,USD 75\nricher,USD 5000\npoor, USD 0.99";
    action.run();
    assertThat(response.getStatus()).isEqualTo(SC_OK);
    PremiumList premiumList = PremiumList.get("foo").get();
    assertThat(premiumList.getPremiumListEntries()).hasSize(3);
    assertThat(premiumList.getPremiumPrice("rich")).hasValue(Money.parse("USD 75"));
    assertThat(premiumList.getPremiumPrice("richer")).hasValue(Money.parse("USD 5000"));
    assertThat(premiumList.getPremiumPrice("poor")).hasValue(Money.parse("USD 0.99"));
    assertThat(premiumList.getPremiumPrice("diamond")).isAbsent();
  }
}
