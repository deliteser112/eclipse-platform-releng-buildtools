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

package google.registry.groups;

import static com.google.common.truth.Truth.assertThat;
import static google.registry.groups.DirectoryGroupsConnection.getDefaultGroupPermissions;
import static javax.servlet.http.HttpServletResponse.SC_CONFLICT;
import static javax.servlet.http.HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
import static javax.servlet.http.HttpServletResponse.SC_NOT_FOUND;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.LowLevelHttpRequest;
import com.google.api.client.http.LowLevelHttpResponse;
import com.google.api.client.json.Json;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.testing.http.HttpTesting;
import com.google.api.client.testing.http.MockHttpTransport;
import com.google.api.client.testing.http.MockLowLevelHttpRequest;
import com.google.api.client.testing.http.MockLowLevelHttpResponse;
import com.google.api.services.admin.directory.Directory;
import com.google.api.services.admin.directory.model.Group;
import com.google.api.services.admin.directory.model.Member;
import com.google.api.services.admin.directory.model.Members;
import com.google.api.services.groupssettings.Groupssettings;
import com.google.api.services.groupssettings.model.Groups;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import google.registry.groups.GroupsConnection.Role;
import google.registry.testing.ExceptionRule;
import java.io.IOException;
import java.util.Set;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

/**
 * Unit tests for {@link DirectoryGroupsConnection}.
 */
@RunWith(MockitoJUnitRunner.class)
public class DirectoryGroupsConnectionTest {

  @Rule
  public final ExceptionRule thrown = new ExceptionRule();

  @Mock
  private Directory directory;

  @Mock
  private Groupssettings groupsSettings;

  @Mock
  private Directory.Members members;

  @Mock
  private Directory.Groups directoryGroups;

  @Mock
  private Groupssettings.Groups settingsGroups;

  @Mock
  private Directory.Members.Insert membersInsert;

  @Mock
  private Directory.Groups.Insert groupsInsert;

  @Mock
  private Directory.Groups.Get groupsGet;

  @Mock
  private Directory.Members.Get membersGet;

  @Mock
  private Directory.Members.List membersList;

  @Mock
  private Groupssettings.Groups.Patch groupsSettingsPatch;

  private DirectoryGroupsConnection connection;
  private Member expectedOwner = new Member();

  @Before
  public void init() throws Exception {
    when(directory.members()).thenReturn(members);
    when(members.insert(anyString(), any(Member.class))).thenReturn(membersInsert);
    when(members.get(anyString(), anyString())).thenReturn(membersGet);
    when(directory.groups()).thenReturn(directoryGroups);
    when(directoryGroups.insert(any(Group.class))).thenReturn(groupsInsert);
    when(directoryGroups.get(anyString())).thenReturn(groupsGet);
    when(groupsSettings.groups()).thenReturn(settingsGroups);
    when(settingsGroups.patch(anyString(), any(Groups.class))).thenReturn(groupsSettingsPatch);
    connection = new DirectoryGroupsConnection();
    connection.directory = directory;
    connection.groupsSettings = groupsSettings;
    connection.googleAppsAdminEmailAddress = "admin@domain-registry.example";
    expectedOwner.setEmail("admin@domain-registry.example");
    expectedOwner.setRole("OWNER");
  }

  @Test
  public void test_addMemberToGroup_succeeds() throws Exception {
    runAddMemberTest();
  }

  @Test
  public void test_addMemberToGroup_isIdempotent_whenMemberAlreadyExists() throws Exception {
    Member expected = runAddMemberTest();
    when(membersInsert.execute())
        .thenThrow(makeResponseException(SC_CONFLICT, "Member already exists."));
    when(membersGet.execute()).thenReturn(expected);
    connection.addMemberToGroup("spam@example.com", "jim@example.com", Role.MEMBER);
    verify(members, times(2)).insert("spam@example.com", expected);
  }

  @Test
  public void test_addMemberToGroup_handlesExceptionThrownByDirectoryService() throws Exception {
    when(membersInsert.execute()).thenThrow(
        makeResponseException(SC_INTERNAL_SERVER_ERROR, "Could not contact Directory server."));
    thrown.expect(GoogleJsonResponseException.class);
    runAddMemberTest();
  }

  @Test
  public void test_addMemberToGroup_handlesMemberKeyNotFoundException() throws Exception {
    when(membersInsert.execute()).thenThrow(
        makeResponseException(SC_NOT_FOUND, "Resource Not Found: memberKey"));
    thrown.expect(RuntimeException.class,
        "Adding member jim@example.com to group spam@example.com "
            + "failed because the member wasn't found.");
    connection.addMemberToGroup("spam@example.com", "jim@example.com", Role.MEMBER);
  }

  @Test
  public void test_addMemberToGroup_createsGroupWhenNonexistent() throws Exception {
    Member expected = new Member();
    expected.setEmail("tim@example.com");
    expected.setRole("MEMBER");
    when(membersInsert.execute())
        .thenThrow(makeResponseException(SC_NOT_FOUND, "Resource Not Found: groupKey"))
        .thenReturn(expected)
        .thenReturn(expectedOwner);
    Group expectedGroup = createExpectedGroup("nonexistentgroup@example.com");
    connection.addMemberToGroup("nonexistentgroup@example.com", "tim@example.com", Role.MEMBER);
    verify(members, times(2)).insert("nonexistentgroup@example.com", expected);
    verify(members).insert("nonexistentgroup@example.com", expectedOwner);
    verify(membersInsert, times(3)).execute();
    verify(directoryGroups).insert(expectedGroup);
    verify(groupsInsert).execute();
    verify(settingsGroups).patch("nonexistentgroup@example.com", getDefaultGroupPermissions());
    verify(groupsSettingsPatch).execute();
  }

  private Group createExpectedGroup(String groupKey) {
    Group group = new Group();
    group.setEmail(groupKey);
    return group;
  }

  @Test
  public void test_createGroup_succeeds() throws Exception {
    runCreateGroupTest();
  }

  @Test
  public void test_createGroup_isIdempotent_whenGroupAlreadyExists() throws Exception {
    Group expected = runCreateGroupTest();
    when(groupsInsert.execute())
        .thenThrow(makeResponseException(SC_CONFLICT, "Entity already exists."));
    when(groupsGet.execute()).thenReturn(expected);
    assertThat(connection.createGroup("spamlovers@hormel.com")).isEqualTo(expected);
    verify(directoryGroups).get("spamlovers@hormel.com");
    verify(groupsGet).execute();
  }

  @Test
  public void test_createGroup_handlesExceptionThrownByDirectoryService() throws Exception {
    when(groupsInsert.execute()).thenThrow(
        makeResponseException(SC_INTERNAL_SERVER_ERROR, "Could not contact Directory server."));
    thrown.expect(GoogleJsonResponseException.class);
    runCreateGroupTest();
  }

  @Test
  public void test_getMembersOfGroup_succeeds() throws Exception {
    Member member1 = new Member();
    member1.setEmail("john@example.com");
    Member member2 = new Member();
    member2.setEmail("tim@bar.foo");
    Member member3 = new Member();
    member3.setEmail("tank@spam.how");
    Member member4 = new Member();
    member4.setEmail("jank@latte.soy");
    Members page1 = new Members();
    page1.setNextPageToken("page2");
    page1.setMembers(ImmutableList.of(member1, member2));
    Members page2 = new Members();
    page2.setNextPageToken(null);
    page2.setMembers(ImmutableList.of(member3, member4));
    when(membersList.execute()).thenReturn(page1, page2);
    when(membersList.getPageToken()).thenReturn("page2", (String) null);
    when(membersList.setRoles("MEMBER")).thenReturn(membersList);
    when(members.list("contacts@bar.foo")).thenReturn(membersList);

    Set<String> emails = connection.getMembersOfGroup("contacts@bar.foo");
    verify(members).list("contacts@bar.foo");
    verify(membersList).setRoles("MEMBER");
    assertThat(emails).containsExactlyElementsIn(
        ImmutableSet.of("john@example.com", "tim@bar.foo", "tank@spam.how", "jank@latte.soy"));
  }

  @Test
  public void test_getMembersOfNonexistentGroup_returnsEmptySet() throws Exception {
    when(members.list("nonexistent_group@fake.notreal")).thenReturn(membersList);
    when(membersList.setRoles("MEMBER")).thenReturn(membersList);
    when(membersList.execute())
        .thenThrow(makeResponseException(SC_NOT_FOUND, "Resource Not Found: groupKey"));
    assertThat(connection.getMembersOfGroup("nonexistent_group@fake.notreal")).isEmpty();
  }

  /** Runs a full test to add a member to the group and returns the expected Member. */
  private Member runAddMemberTest() throws Exception {
    connection.addMemberToGroup("spam@example.com", "jim@example.com", Role.MEMBER);
    Member expected = new Member();
    expected.setEmail("jim@example.com");
    expected.setRole("MEMBER");
    verify(members).insert("spam@example.com", expected);
    verify(membersInsert).execute();
    return expected;
  }

  /** Runs a full test to create a group and returns the expected Group. */
  private Group runCreateGroupTest() throws Exception {
    when(membersInsert.execute()).thenReturn(expectedOwner);
    connection.createGroup("spamlovers@hormel.com");
    Group expected = createExpectedGroup("spamlovers@hormel.com");
    verify(directoryGroups).insert(eq(expected));
    verify(groupsInsert).execute();
    verify(members).insert("spamlovers@hormel.com", expectedOwner);
    verify(membersInsert).execute();
    verify(settingsGroups).patch("spamlovers@hormel.com", getDefaultGroupPermissions());
    verify(groupsSettingsPatch).execute();
    return expected;
  }

  /** Returns a valid GoogleJsonResponseException for the given status code and error message.  */
  private GoogleJsonResponseException makeResponseException(
      final int statusCode,
      final String message) throws Exception {
    HttpTransport transport = new MockHttpTransport() {
      @Override
      public LowLevelHttpRequest buildRequest(String method, String url) throws IOException {
        return new MockLowLevelHttpRequest() {
          @Override
          public LowLevelHttpResponse execute() throws IOException {
            MockLowLevelHttpResponse response = new MockLowLevelHttpResponse();
            response.setStatusCode(statusCode);
            response.setContentType(Json.MEDIA_TYPE);
            response.setContent(String.format(
                "{\"error\":{\"code\":%d,\"message\":\"%s\",\"domain\":\"global\","
                + "\"reason\":\"duplicate\"}}",
                statusCode,
                message));
            return response;
          }};
      }};
    HttpRequest request = transport.createRequestFactory()
        .buildGetRequest(HttpTesting.SIMPLE_GENERIC_URL)
        .setThrowExceptionOnExecuteError(false);
    return GoogleJsonResponseException.from(new JacksonFactory(), request.execute());
  }
}
