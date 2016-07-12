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

package google.registry.tools.server;

import static com.google.common.truth.Truth.assertThat;
import static javax.servlet.http.HttpServletResponse.SC_OK;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.base.Optional;
import google.registry.groups.DirectoryGroupsConnection;
import google.registry.groups.GroupsConnection.Role;
import google.registry.request.HttpException.BadRequestException;
import google.registry.request.HttpException.InternalServerErrorException;
import google.registry.request.Response;
import google.registry.testing.AppEngineRule;
import google.registry.testing.ExceptionRule;
import google.registry.testing.InjectRule;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

/**
 * Unit tests for {@link CreateGroupsAction}.
 */
@RunWith(MockitoJUnitRunner.class)
public class CreateGroupsActionTest {

  @Rule
  public final AppEngineRule appEngine = AppEngineRule.builder()
      .withDatastore()
      .build();

  @Rule
  public final ExceptionRule thrown = new ExceptionRule();

  @Rule
  public final InjectRule inject = new InjectRule();

  @Mock
  private DirectoryGroupsConnection connection;

  @Mock
  private Response response;

  private void runAction(String clientId) {
    CreateGroupsAction action = new CreateGroupsAction();
    action.response = response;
    action.groupsConnection = connection;
    action.publicDomainName = "domain-registry.example";
    action.clientId = Optional.fromNullable(clientId);
    action.run();
  }

  @Test
  public void test_invalidRequest_missingClientId() throws Exception {
    thrown.expect(BadRequestException.class,
        "Error creating Google Groups, missing parameter: clientId");
    runAction(null);
  }

  @Test
  public void test_invalidRequest_invalidClientId() throws Exception {
    thrown.expect(BadRequestException.class,
        "Error creating Google Groups; could not find registrar with id completelyMadeUpClientId");
    runAction("completelyMadeUpClientId");
  }

  @Test
  public void test_createsAllGroupsSuccessfully() throws Exception {
    runAction("NewRegistrar");
    verify(response).setStatus(SC_OK);
    verify(response).setPayload("Success!");
    verifyGroupCreationCallsForNewRegistrar();
    verify(connection).addMemberToGroup("registrar-primary-contacts@domain-registry.example",
        "newregistrar-primary-contacts@domain-registry.example",
        Role.MEMBER);
  }

  @Test
  public void test_createsSomeGroupsSuccessfully_whenOthersFail() throws Exception {
    when(connection.createGroup("newregistrar-primary-contacts@domain-registry.example"))
        .thenThrow(new RuntimeException("Could not contact server."));
    doThrow(new RuntimeException("Invalid access.")).when(connection).addMemberToGroup(
        "registrar-technical-contacts@domain-registry.example",
        "newregistrar-technical-contacts@domain-registry.example",
        Role.MEMBER);
    try {
      runAction("NewRegistrar");
    } catch (InternalServerErrorException e) {
      String responseString = e.toString();
      assertThat(responseString).contains("abuse => Success");
      assertThat(responseString).contains("billing => Success");
      assertThat(responseString).contains("legal => Success");
      assertThat(responseString).contains("marketing => Success");
      assertThat(responseString).contains("whois-inquiry => Success");
      assertThat(responseString).contains(
          "primary => java.lang.RuntimeException: Could not contact server.");
      assertThat(responseString).contains(
          "technical => java.lang.RuntimeException: Invalid access.");
      verifyGroupCreationCallsForNewRegistrar();
      return;
    }
    Assert.fail("Should have thrown InternalServerErrorException.");
  }

  private void verifyGroupCreationCallsForNewRegistrar() throws Exception {
    verify(connection).createGroup("newregistrar-abuse-contacts@domain-registry.example");
    verify(connection).createGroup("newregistrar-primary-contacts@domain-registry.example");
    verify(connection).createGroup("newregistrar-billing-contacts@domain-registry.example");
    verify(connection).createGroup("newregistrar-legal-contacts@domain-registry.example");
    verify(connection).createGroup("newregistrar-marketing-contacts@domain-registry.example");
    verify(connection).createGroup("newregistrar-technical-contacts@domain-registry.example");
    verify(connection).createGroup("newregistrar-whois-inquiry-contacts@domain-registry.example");
    verify(connection).addMemberToGroup("registrar-abuse-contacts@domain-registry.example",
        "newregistrar-abuse-contacts@domain-registry.example",
        Role.MEMBER);
    // Note that addMemberToGroup for primary is verified separately for the success test because
    // the exception thrown on group creation in the failure test causes the servlet not to get to
    // this line.
    verify(connection).addMemberToGroup("registrar-billing-contacts@domain-registry.example",
        "newregistrar-billing-contacts@domain-registry.example",
        Role.MEMBER);
    verify(connection).addMemberToGroup("registrar-legal-contacts@domain-registry.example",
        "newregistrar-legal-contacts@domain-registry.example",
        Role.MEMBER);
    verify(connection).addMemberToGroup("registrar-marketing-contacts@domain-registry.example",
        "newregistrar-marketing-contacts@domain-registry.example",
        Role.MEMBER);
    verify(connection).addMemberToGroup("registrar-technical-contacts@domain-registry.example",
        "newregistrar-technical-contacts@domain-registry.example",
        Role.MEMBER);
    verify(connection).addMemberToGroup(
        "registrar-whois-inquiry-contacts@domain-registry.example",
        "newregistrar-whois-inquiry-contacts@domain-registry.example",
        Role.MEMBER);
  }
}
