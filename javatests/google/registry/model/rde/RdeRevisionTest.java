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

package google.registry.model.rde;

import static com.google.common.truth.Truth.assertThat;
import static google.registry.model.ofy.ObjectifyService.ofy;
import static google.registry.model.rde.RdeMode.FULL;
import static google.registry.model.rde.RdeRevision.getNextRevision;
import static google.registry.model.rde.RdeRevision.saveRevision;

import com.google.common.base.VerifyException;
import com.googlecode.objectify.VoidWork;
import google.registry.testing.AppEngineRule;
import google.registry.testing.ExceptionRule;
import org.joda.time.DateTime;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link RdeRevision}. */
@RunWith(JUnit4.class)
public class RdeRevisionTest {

  @Rule
  public final AppEngineRule appEngine = AppEngineRule.builder().withDatastore().build();

  @Rule
  public final ExceptionRule thrown = new ExceptionRule();

  @Test
  public void testGetNextRevision_objectDoesntExist_returnsZero() throws Exception {
    assertThat(getNextRevision("torment", DateTime.parse("1984-12-18TZ"), FULL))
        .isEqualTo(0);
  }

  @Test
  public void testGetNextRevision_objectExistsAtZero_returnsOne() throws Exception {
    save("sorrow", DateTime.parse("1984-12-18TZ"), FULL, 0);
    assertThat(getNextRevision("sorrow", DateTime.parse("1984-12-18TZ"), FULL))
        .isEqualTo(1);
  }

  @Test
  public void testSaveRevision_objectDoesntExist_newRevisionIsZero_nextRevIsOne() throws Exception {
    ofy().transact(new VoidWork() {
      @Override
      public void vrun() {
        saveRevision("despondency", DateTime.parse("1984-12-18TZ"), FULL, 0);
      }});
    ofy().transact(new VoidWork() {
      @Override
      public void vrun() {
        assertThat(getNextRevision("despondency", DateTime.parse("1984-12-18TZ"), FULL))
            .isEqualTo(1);
      }});
  }

  @Test
  public void testSaveRevision_objectDoesntExist_newRevisionIsOne_throwsVe() throws Exception {
    thrown.expect(VerifyException.class, "object missing");
    ofy().transact(new VoidWork() {
      @Override
      public void vrun() {
        saveRevision("despondency", DateTime.parse("1984-12-18TZ"), FULL, 1);
      }});
  }

  @Test
  public void testSaveRevision_objectExistsAtZero_newRevisionIsZero_throwsVe() throws Exception {
    save("melancholy", DateTime.parse("1984-12-18TZ"), FULL, 0);
    thrown.expect(VerifyException.class, "object already created");
    ofy().transact(new VoidWork() {
      @Override
      public void vrun() {
        saveRevision("melancholy", DateTime.parse("1984-12-18TZ"), FULL, 0);
      }});
  }

  @Test
  public void testSaveRevision_objectExistsAtZero_newRevisionIsOne_nextRevIsTwo() throws Exception {
    save("melancholy", DateTime.parse("1984-12-18TZ"), FULL, 0);
    ofy().transact(new VoidWork() {
      @Override
      public void vrun() {
        saveRevision("melancholy", DateTime.parse("1984-12-18TZ"), FULL, 1);
      }});
    ofy().transact(new VoidWork() {
      @Override
      public void vrun() {
        assertThat(getNextRevision("melancholy", DateTime.parse("1984-12-18TZ"), FULL))
            .isEqualTo(2);
      }});
  }

  @Test
  public void testSaveRevision_objectExistsAtZero_newRevisionIsTwo_throwsVe() throws Exception {
    save("melancholy", DateTime.parse("1984-12-18TZ"), FULL, 0);
    thrown.expect(VerifyException.class, "should be at 1 ");
    ofy().transact(new VoidWork() {
      @Override
      public void vrun() {
        saveRevision("melancholy", DateTime.parse("1984-12-18TZ"), FULL, 2);
      }});
  }

  @Test
  public void testSaveRevision_negativeRevision_throwsIae() throws Exception {
    thrown.expect(IllegalArgumentException.class, "Negative revision");
    ofy().transact(new VoidWork() {
      @Override
      public void vrun() {
        saveRevision("melancholy", DateTime.parse("1984-12-18TZ"), FULL, -1);
      }});
  }

  @Test
  public void testSaveRevision_callerNotInTransaction_throwsIse() throws Exception {
    thrown.expect(IllegalStateException.class, "transaction");
    saveRevision("frenzy", DateTime.parse("1984-12-18TZ"), FULL, 1);
  }

  public static void save(String tld, DateTime date, RdeMode mode, int revision) {
    String triplet = RdeNamingUtils.makePartialName(tld, date, mode);
    RdeRevision object = new RdeRevision();
    object.id = triplet;
    object.revision = revision;
    ofy().saveWithoutBackup().entity(object).now();
  }
}
