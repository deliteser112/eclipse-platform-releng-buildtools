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

package google.registry.export;

import static com.google.appengine.tools.cloudstorage.GcsServiceFactory.createGcsService;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;
import static google.registry.testing.DatastoreHelper.createTld;
import static google.registry.testing.DatastoreHelper.persistActiveDomain;
import static google.registry.testing.DatastoreHelper.persistActiveDomainApplication;
import static google.registry.testing.DatastoreHelper.persistDeletedDomain;
import static google.registry.testing.DatastoreHelper.persistResource;
import static google.registry.testing.GcsTestingUtils.readGcsFile;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.appengine.tools.cloudstorage.GcsFilename;
import com.google.appengine.tools.cloudstorage.GcsService;
import com.google.appengine.tools.cloudstorage.ListOptions;
import com.google.appengine.tools.cloudstorage.ListResult;
import com.google.common.base.Splitter;
import google.registry.model.registry.Registry;
import google.registry.model.registry.Registry.TldType;
import google.registry.testing.FakeResponse;
import google.registry.testing.mapreduce.MapreduceTestCase;
import java.io.FileNotFoundException;
import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link ExportDomainListsAction}. */
@RunWith(JUnit4.class)
public class ExportDomainListsActionTest extends MapreduceTestCase<ExportDomainListsAction> {

  private GcsService gcsService;

  @Before
  public void init() {
    createTld("tld");
    createTld("testtld");
    persistResource(Registry.get("testtld").asBuilder().setTldType(TldType.TEST).build());

    action = new ExportDomainListsAction();
    action.mrRunner = makeDefaultRunner();
    action.response = new FakeResponse();
    action.gcsBucket = "outputbucket";
    action.gcsBufferSize = 500;
    gcsService = createGcsService();
  }

  private void runMapreduce() throws Exception {
    action.run();
    executeTasksUntilEmpty("mapreduce");
  }

  @Test
  public void test_outputsOnlyActiveDomains() throws Exception {
    persistActiveDomain("onetwo.tld");
    persistActiveDomain("rudnitzky.tld");
    persistDeletedDomain("mortuary.tld", DateTime.parse("2001-03-14T10:11:12Z"));
    runMapreduce();
    GcsFilename existingFile = new GcsFilename("outputbucket", "tld.txt");
    String tlds = new String(readGcsFile(gcsService, existingFile), UTF_8).trim();
    // Check that it only contains the active domains, not the dead one.
    assertThat(Splitter.on('\n').splitToList(tlds)).containsExactly("onetwo.tld", "rudnitzky.tld");
  }

  @Test
  public void test_outputsOnlyDomainsOnRealTlds() throws Exception {
    persistActiveDomain("onetwo.tld");
    persistActiveDomain("rudnitzky.tld");
    persistActiveDomain("wontgo.testtld");
    runMapreduce();
    GcsFilename existingFile = new GcsFilename("outputbucket", "tld.txt");
    String tlds = new String(readGcsFile(gcsService, existingFile), UTF_8).trim();
    // Check that it only contains the domains on the real TLD, and not the test one.
    assertThat(Splitter.on('\n').splitToList(tlds)).containsExactly("onetwo.tld", "rudnitzky.tld");
    // Make sure that the test TLD file wasn't written out.
    GcsFilename nonexistentFile = new GcsFilename("outputbucket", "testtld.txt");
    try {
      readGcsFile(gcsService, nonexistentFile);
      assertWithMessage("Expected FileNotFoundException to be thrown").fail();
    } catch (FileNotFoundException e) {
      ListResult ls = gcsService.list("outputbucket", ListOptions.DEFAULT);
      assertThat(ls.next().getName()).isEqualTo("tld.txt");
      // Make sure that no other files were written out.
      assertThat(ls.hasNext()).isFalse();
    }
  }

  @Test
  public void test_outputsDomainsFromDifferentTldsToMultipleFiles() throws Exception {
    createTld("tldtwo");
    // You'd think this test was written around Christmas, but it wasn't.
    persistActiveDomain("dasher.tld");
    persistActiveDomain("prancer.tld");
    persistActiveDomain("rudolph.tldtwo");
    persistActiveDomain("santa.tldtwo");
    persistActiveDomain("buddy.tldtwo");
    runMapreduce();
    GcsFilename firstTldFile = new GcsFilename("outputbucket", "tld.txt");
    String tlds = new String(readGcsFile(gcsService, firstTldFile), UTF_8).trim();
    assertThat(Splitter.on('\n').splitToList(tlds)).containsExactly("dasher.tld", "prancer.tld");
    GcsFilename secondTldFile = new GcsFilename("outputbucket", "tldtwo.txt");
    String moreTlds = new String(readGcsFile(gcsService, secondTldFile), UTF_8).trim();
    assertThat(Splitter.on('\n').splitToList(moreTlds))
        .containsExactly("rudolph.tldtwo", "santa.tldtwo", "buddy.tldtwo");
  }

  @Test
  public void test_doesntOutputDomainApplications() throws Exception {
    persistActiveDomain("chilipepper.tld");
    persistActiveDomainApplication("nagajolokia.tld");
    runMapreduce();
    GcsFilename firstTldFile = new GcsFilename("outputbucket", "tld.txt");
    String tlds = new String(readGcsFile(gcsService, firstTldFile), UTF_8).trim();
    // Check that it didn't output nagajolokia.tld.
    assertThat(Splitter.on('\n').splitToList(tlds)).containsExactly("chilipepper.tld");
  }
}
