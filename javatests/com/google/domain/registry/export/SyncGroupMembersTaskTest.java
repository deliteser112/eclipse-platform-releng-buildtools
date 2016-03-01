// Copyright 2016 Google Inc. All Rights Reserved.
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

package com.google.domain.registry.export;

import static com.google.common.truth.Truth.assertThat;
import static com.google.domain.registry.export.SyncGroupMembersTask.getGroupEmailAddressForContactType;
import static com.google.domain.registry.model.ofy.ObjectifyService.ofy;
import static com.google.domain.registry.model.registrar.RegistrarContact.Type.ADMIN;
import static com.google.domain.registry.model.registrar.RegistrarContact.Type.MARKETING;
import static com.google.domain.registry.model.registrar.RegistrarContact.Type.TECH;
import static com.google.domain.registry.testing.DatastoreHelper.persistResource;
import static javax.servlet.http.HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
import static javax.servlet.http.HttpServletResponse.SC_OK;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.domain.registry.groups.DirectoryGroupsConnection;
import com.google.domain.registry.groups.GroupsConnection.Role;
import com.google.domain.registry.model.registrar.Registrar;
import com.google.domain.registry.model.registrar.RegistrarContact;
import com.google.domain.registry.request.Response;
import com.google.domain.registry.testing.AppEngineRule;
import com.google.domain.registry.testing.ExceptionRule;
import com.google.domain.registry.testing.InjectRule;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import java.io.IOException;

/**
 * Unit tests for {@link SyncGroupMembersTask}.
 *
 * <p>Note that this relies on the registrars NewRegistrar and TheRegistrar created by default in
 * {@link AppEngineRule}.
 */
@RunWith(MockitoJUnitRunner.class)
public class SyncGroupMembersTaskTest {

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

  private void runTask() {
    SyncGroupMembersTask task = new SyncGroupMembersTask();
    task.groupsConnection = connection;
    task.response = response;
    task.publicDomainName = "domain-registry.example";
    task.run();
  }

  @Test
  public void test_getGroupEmailAddressForContactType_convertsToLowercase() {
    assertThat(getGroupEmailAddressForContactType(
            "SomeRegistrar",
            RegistrarContact.Type.ADMIN,
            "domain-registry.example"))
        .isEqualTo("someregistrar-primary-contacts@domain-registry.example");
  }

  @Test
  public void test_getGroupEmailAddressForContactType_convertsNonAlphanumericChars() {
    assertThat(getGroupEmailAddressForContactType(
            "Weird.ಠ_ಠRegistrar",
              MARKETING,
            "domain-registry.example"))
        .isEqualTo("weirdregistrar-marketing-contacts@domain-registry.example");
  }

  @Test
  public void test_doPost_noneModified() throws Exception {
    persistResource(Registrar.loadByClientId("NewRegistrar")
        .asBuilder()
        .setContactsRequireSyncing(false)
        .build());
    persistResource(Registrar.loadByClientId("TheRegistrar")
        .asBuilder()
        .setContactsRequireSyncing(false)
        .build());
    runTask();
    verify(response).setStatus(SC_OK);
    verify(response).setPayload("NOT_MODIFIED No registrar contacts have been updated "
        + "since the last time servlet ran.\n");
    assertThat(Registrar.loadByClientId("NewRegistrar").getContactsRequireSyncing()).isFalse();
  }

  @Test
  public void test_doPost_syncsNewContact() throws Exception {
    runTask();
    verify(connection).addMemberToGroup(
        "newregistrar-primary-contacts@domain-registry.example",
        "janedoe@theregistrar.com",
        Role.MEMBER);
    verify(response).setStatus(SC_OK);
    verify(response).setPayload("OK Group memberships successfully updated.\n");
    assertThat(Registrar.loadByClientId("NewRegistrar").getContactsRequireSyncing()).isFalse();
  }

  @Test
  public void test_doPost_removesOldContact() throws Exception {
    when(connection.getMembersOfGroup("newregistrar-primary-contacts@domain-registry.example"))
        .thenReturn(ImmutableSet.of("defunct@example.com", "janedoe@theregistrar.com"));
    runTask();
    verify(connection).removeMemberFromGroup(
        "newregistrar-primary-contacts@domain-registry.example", "defunct@example.com");
    verify(response).setStatus(SC_OK);
    assertThat(Registrar.loadByClientId("NewRegistrar").getContactsRequireSyncing()).isFalse();
  }

  @Test
  public void test_doPost_removesAllContactsFromGroup() throws Exception {
    when(connection.getMembersOfGroup("newregistrar-primary-contacts@domain-registry.example"))
        .thenReturn(ImmutableSet.of("defunct@example.com", "janedoe@theregistrar.com"));
    ofy().deleteWithoutBackup()
        .entities(Registrar.loadByClientId("NewRegistrar").getContacts())
        .now();
    runTask();
    verify(connection).removeMemberFromGroup(
        "newregistrar-primary-contacts@domain-registry.example", "defunct@example.com");
    verify(connection).removeMemberFromGroup(
        "newregistrar-primary-contacts@domain-registry.example", "janedoe@theregistrar.com");
    verify(response).setStatus(SC_OK);
    assertThat(Registrar.loadByClientId("NewRegistrar").getContactsRequireSyncing()).isFalse();
  }

  @Test
  public void test_doPost_addsAndRemovesContacts_acrossMultipleRegistrars() throws Exception {
    when(connection.getMembersOfGroup("newregistrar-primary-contacts@domain-registry.example"))
        .thenReturn(ImmutableSet.of("defunct@example.com", "janedoe@theregistrar.com"));
    when(connection.getMembersOfGroup("newregistrar-marketing-contacts@domain-registry.example"))
        .thenReturn(ImmutableSet.<String> of());
    when(connection.getMembersOfGroup("theregistrar-technical-contacts@domain-registry.example"))
        .thenReturn(ImmutableSet.<String> of());
    when(connection.getMembersOfGroup("theregistrar-primary-contacts@domain-registry.example"))
        .thenReturn(ImmutableSet.<String> of());
    persistResource(
        new RegistrarContact.Builder()
            .setParent(Registrar.loadByClientId("NewRegistrar"))
            .setName("Binary Star")
            .setEmailAddress("binarystar@example.tld")
            .setTypes(ImmutableSet.of(ADMIN, MARKETING))
            .build());
    persistResource(
        new RegistrarContact.Builder()
            .setParent(Registrar.loadByClientId("TheRegistrar"))
            .setName("Hexadecimal")
            .setEmailAddress("hexadecimal@snow.fall")
            .setTypes(ImmutableSet.of(TECH))
            .build());
    runTask();
    verify(connection).removeMemberFromGroup(
        "newregistrar-primary-contacts@domain-registry.example", "defunct@example.com");
    verify(connection).addMemberToGroup(
        "newregistrar-primary-contacts@domain-registry.example",
        "binarystar@example.tld",
        Role.MEMBER);
    verify(connection).addMemberToGroup(
        "newregistrar-marketing-contacts@domain-registry.example",
        "binarystar@example.tld",
        Role.MEMBER);
    verify(connection).addMemberToGroup(
        "theregistrar-primary-contacts@domain-registry.example",
        "johndoe@theregistrar.com",
        Role.MEMBER);
    verify(connection).addMemberToGroup(
        "theregistrar-technical-contacts@domain-registry.example",
        "hexadecimal@snow.fall",
        Role.MEMBER);
    verify(response).setStatus(SC_OK);
    assertThat(Iterables.filter(Registrar.loadAll(), new Predicate<Registrar>() {
      @Override
      public boolean apply(Registrar registrar) {
        return registrar.getContactsRequireSyncing();
      }})).isEmpty();
  }

  @Test
  public void test_doPost_gracefullyHandlesExceptionForSingleRegistrar() throws Exception {
    when(connection.getMembersOfGroup("newregistrar-primary-contacts@domain-registry.example"))
        .thenReturn(ImmutableSet.<String> of());
    when(connection.getMembersOfGroup("theregistrar-primary-contacts@domain-registry.example"))
        .thenThrow(new IOException("Internet was deleted"));
    runTask();
    verify(connection).addMemberToGroup(
        "newregistrar-primary-contacts@domain-registry.example",
        "janedoe@theregistrar.com",
        Role.MEMBER);
    verify(response).setStatus(SC_INTERNAL_SERVER_ERROR);
    verify(response).setPayload("FAILED Error occurred while updating registrar contacts.\n");
    assertThat(Registrar.loadByClientId("NewRegistrar").getContactsRequireSyncing()).isFalse();
    assertThat(Registrar.loadByClientId("TheRegistrar").getContactsRequireSyncing()).isTrue();
  }
}
