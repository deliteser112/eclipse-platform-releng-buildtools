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

package google.registry.model.registry;

import static com.google.common.truth.Truth.assertThat;
import static google.registry.model.ofy.ObjectifyService.ofy;
import static google.registry.model.registry.RegistryCursor.CursorType.BRDA;
import static google.registry.model.registry.RegistryCursor.CursorType.RDE_UPLOAD;
import static google.registry.testing.DatastoreHelper.createTld;

import com.googlecode.objectify.VoidWork;

import google.registry.model.EntityTestCase;
import google.registry.model.common.Cursor;

import org.joda.time.DateTime;
import org.junit.Test;

/** Unit tests for {@link RegistryCursor}. */
public class RegistryCursorTest extends EntityTestCase {

  @Test
  public void testPersistence() {
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

  @Test
  public void testSuccess_dualRead_newOverOld() {
    createTld("tld");
    final DateTime newCursorTime = DateTime.parse("2012-07-12T03:30:00.000Z");
    final DateTime oldCursorTime = DateTime.parse("2012-07-11T03:30:00.000Z");
    ofy().transact(
        new VoidWork() {
          @Override
          public void vrun() {
            ofy()
                .save()
                .entities(
                    // We can't use RegistryCursor.save() since dual-writing happens there.
                    RegistryCursor.create(Registry.get("tld"), RDE_UPLOAD, oldCursorTime),
                    Cursor.create(
                        Cursor.CursorType.RDE_UPLOAD, newCursorTime, Registry.get("tld")))
                .now();
          }
        });
    assertThat(RegistryCursor.load(Registry.get("tld"), RDE_UPLOAD)).hasValue(newCursorTime);
  }

  @Test
  public void testSuccess_dualRead_onlyOld() {
    createTld("tld");
    final DateTime oldCursorTime = DateTime.parse("2012-07-11T03:30:00.000Z");
    ofy().transact(
        new VoidWork() {
          @Override
          public void vrun() {
            ofy()
                .save()
                .entity(RegistryCursor.create(Registry.get("tld"), RDE_UPLOAD, oldCursorTime))
                .now();
          }
        });
    assertThat(RegistryCursor.load(Registry.get("tld"), RDE_UPLOAD)).hasValue(oldCursorTime);
  }
}
