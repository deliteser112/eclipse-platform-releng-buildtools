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

package com.google.domain.registry.model.registry;

import static com.google.common.truth.Truth.assertThat;
import static com.google.domain.registry.model.ofy.ObjectifyService.ofy;
import static com.google.domain.registry.model.registry.RegistryCursor.CursorType.BRDA;
import static com.google.domain.registry.model.registry.RegistryCursor.CursorType.RDE_UPLOAD;
import static com.google.domain.registry.testing.DatastoreHelper.createTld;

import com.google.domain.registry.model.EntityTestCase;

import com.googlecode.objectify.VoidWork;

import org.joda.time.DateTime;
import org.junit.Test;

/** Unit tests for {@link RegistryCursor}. */
public class RegistryCursorTest extends EntityTestCase {

  @Test
  public void test_persistence() {
    createTld("tld");
    clock.advanceOneMilli();
    final DateTime time = DateTime.parse("2012-07-12T03:30:00.000Z");
    ofy().transact(new VoidWork() {
      @Override
      public void vrun() {
        RegistryCursor.save(Registry.get("tld"), RDE_UPLOAD, time);
      }});
    assertThat(RegistryCursor.load(Registry.get("tld"), BRDA)).isAbsent();
    assertThat(RegistryCursor.load(Registry.get("tld"), RDE_UPLOAD)).hasValue(time);
  }
}
