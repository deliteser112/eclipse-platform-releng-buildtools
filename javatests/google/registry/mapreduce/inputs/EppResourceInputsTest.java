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

package google.registry.mapreduce.inputs;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assert_;
import static google.registry.mapreduce.inputs.EppResourceInputs.createEntityInput;
import static google.registry.mapreduce.inputs.EppResourceInputs.createKeyInput;
import static google.registry.model.index.EppResourceIndexBucket.getBucketKey;
import static google.registry.testing.DatastoreHelper.createTld;
import static google.registry.testing.DatastoreHelper.newContactResource;
import static google.registry.testing.DatastoreHelper.newDomainApplication;
import static google.registry.testing.DatastoreHelper.newDomainResource;
import static google.registry.testing.DatastoreHelper.newHostResource;
import static google.registry.testing.DatastoreHelper.persistActiveContact;
import static google.registry.testing.DatastoreHelper.persistEppResourceInFirstBucket;
import static google.registry.testing.DatastoreHelper.persistResource;
import static google.registry.testing.DatastoreHelper.persistSimpleResource;

import com.google.appengine.tools.mapreduce.InputReader;
import com.googlecode.objectify.Key;
import google.registry.model.EppResource;
import google.registry.model.contact.ContactResource;
import google.registry.model.domain.DomainApplication;
import google.registry.model.domain.DomainBase;
import google.registry.model.domain.DomainResource;
import google.registry.model.host.HostResource;
import google.registry.model.index.EppResourceIndex;
import google.registry.testing.AppEngineRule;
import google.registry.testing.ExceptionRule;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.HashSet;
import java.util.NoSuchElementException;
import java.util.Set;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests {@link EppResourceInputs} */
@RunWith(JUnit4.class)
public class EppResourceInputsTest {

  private static final double EPSILON = 0.0001;

  @Rule
  public final AppEngineRule appEngine = AppEngineRule.builder().withDatastore().build();

  @Rule
  public final ExceptionRule thrown = new ExceptionRule();

  @SuppressWarnings("unchecked")
  private <T> T serializeAndDeserialize(T obj) throws Exception {
    try (ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
        ObjectOutputStream objectOut = new ObjectOutputStream(byteOut)) {
      objectOut.writeObject(obj);
      try (ByteArrayInputStream byteIn = new ByteArrayInputStream(byteOut.toByteArray());
          ObjectInputStream objectIn = new ObjectInputStream(byteIn)) {
        return (T) objectIn.readObject();
      }
    }
  }

  @Test
  public void testSuccess_keyInputType_polymorphicBaseType() throws Exception {
    createKeyInput(DomainBase.class);
  }

  @Test
  public void testFailure_keyInputType_polymorphicSubclass() throws Exception {
    thrown.expect(IllegalArgumentException.class, "non-polymorphic");
    createKeyInput(DomainResource.class);
  }

  @Test
  public void testFailure_keyInputType_noInheritanceBetweenTypes_eppResource() throws Exception {
    thrown.expect(IllegalArgumentException.class, "inheritance");
    createKeyInput(EppResource.class, DomainBase.class);
  }

  @Test
  public void testSuccess_entityInputTypesMayBePolymorphic() throws Exception {
    // Both polymorphic and not should work.
    createEntityInput(DomainBase.class);
    createEntityInput(DomainResource.class);
  }

  @Test
  public void testFailure_entityInputType_noInheritanceBetweenTypes_eppResource() throws Exception {
    thrown.expect(IllegalArgumentException.class, "inheritance");
    createEntityInput(EppResource.class, DomainResource.class);
  }

  @Test
  public void testFailure_entityInputType_noInheritanceBetweenTypes_subclasses() throws Exception {
    thrown.expect(IllegalArgumentException.class, "inheritance");
    createEntityInput(DomainBase.class, DomainResource.class);
  }

  @Test
  public void testReaderCountMatchesBucketCount() throws Exception {
    assertThat(createKeyInput(DomainBase.class).createReaders()).hasSize(3);
    assertThat(createEntityInput(DomainBase.class).createReaders()).hasSize(3);
  }

  @Test
  public void testKeyInput_oneReaderPerBucket() throws Exception {
    createTld("tld");
    Set<Key<DomainResource>> domains = new HashSet<>();
    for (int i = 1; i <= 3; i++) {
      Key<DomainResource> key = Key.create(newDomainResource(i + ".tld"));
      domains.add(key);
      persistResource(EppResourceIndex.create(getBucketKey(i), key));
    }
    Set<Key<DomainBase>> seen = new HashSet<>();
    for (InputReader<Key<DomainBase>> reader : createKeyInput(DomainBase.class).createReaders()) {
      reader.beginShard();
      reader.beginSlice();
      seen.add(reader.next());
      try {
        reader.next();
        assert_().fail("Unexpected element");
      } catch (NoSuchElementException expected) {
      }
    }
    assertThat(seen).containsExactlyElementsIn(domains);
  }

  @Test
  public void testEntityInput_oneReaderPerBucket() throws Exception {
    createTld("tld");
    Set<DomainResource> domains = new HashSet<>();
    for (int i = 1; i <= 3; i++) {
      // Persist the domain as a simple resource so that it doesn't automatically get an ERI.
      DomainResource domain = persistSimpleResource(newDomainResource(i + ".tld"));
      domains.add(domain);
      persistResource(EppResourceIndex.create(getBucketKey(i), Key.create(domain)));
    }
    Set<DomainResource> seen = new HashSet<>();
    for (InputReader<DomainResource> reader
        : createEntityInput(DomainResource.class).createReaders()) {
      reader.beginShard();
      reader.beginSlice();
      seen.add(reader.next());
      try {
        reader.next();
        assert_().fail("Unexpected element");
      } catch (NoSuchElementException expected) {
      }
    }
    assertThat(seen).containsExactlyElementsIn(domains);
  }

  @Test
  public void testSuccess_keyReader_survivesAcrossSerialization() throws Exception {
    createTld("tld");
    DomainResource domainA = persistEppResourceInFirstBucket(newDomainResource("a.tld"));
    DomainResource domainB = persistEppResourceInFirstBucket(newDomainResource("b.tld"));
    // Should be ignored. We'll know if it isn't because the progress counts will be off.
    persistActiveContact("contact");
    Set<Key<DomainBase>> seen = new HashSet<>();
    InputReader<Key<DomainBase>> reader = createKeyInput(DomainBase.class).createReaders().get(0);
    reader.beginShard();
    reader.beginSlice();
    assertThat(reader.getProgress()).isWithin(EPSILON).of(0);
    seen.add(reader.next());
    assertThat(reader.getProgress()).isWithin(EPSILON).of(0.5);
    reader.endSlice();
    reader = serializeAndDeserialize(reader);
    reader.beginSlice();
    assertThat(reader.getProgress()).isWithin(EPSILON).of(0.5);
    seen.add(reader.next());
    assertThat(reader.getProgress()).isWithin(EPSILON).of(1);
    assertThat(seen).containsExactly(Key.create(domainA), Key.create(domainB));
    thrown.expect(NoSuchElementException.class);
    reader.next();
  }

  @Test
  public void testSuccess_entityReader_survivesAcrossSerialization() throws Exception {
    createTld("tld");
    DomainResource domainA = persistEppResourceInFirstBucket(newDomainResource("a.tld"));
    DomainResource domainB = persistEppResourceInFirstBucket(newDomainResource("b.tld"));
    // Should be ignored. We'll know if it isn't because the progress counts will be off.
    persistActiveContact("contact");
    Set<DomainResource> seen = new HashSet<>();
    InputReader<DomainResource> reader =
        createEntityInput(DomainResource.class).createReaders().get(0);
    reader.beginShard();
    reader.beginSlice();
    assertThat(reader.getProgress()).isWithin(EPSILON).of(0);
    seen.add(reader.next());
    assertThat(reader.getProgress()).isWithin(EPSILON).of(0.5);
    reader.endSlice();
    reader = serializeAndDeserialize(reader);
    reader.beginSlice();
    assertThat(reader.getProgress()).isWithin(EPSILON).of(0.5);
    seen.add(reader.next());
    assertThat(reader.getProgress()).isWithin(EPSILON).of(1);
    reader.endSlice();
    reader.endShard();
    assertThat(seen).containsExactly(domainA, domainB);
    thrown.expect(NoSuchElementException.class);
    reader.next();
  }

  @Test
  public void testSuccess_entityReader_allowsPolymorphicMatches() throws Exception {
    createTld("tld");
    DomainResource domain = persistEppResourceInFirstBucket(newDomainResource("a.tld"));
    DomainApplication application = persistEppResourceInFirstBucket(newDomainApplication("b.tld"));
    Set<DomainBase> seen = new HashSet<>();
    InputReader<DomainBase> reader = createEntityInput(DomainBase.class).createReaders().get(0);
    reader.beginShard();
    reader.beginSlice();
    assertThat(reader.getProgress()).isWithin(EPSILON).of(0);
    seen.add(reader.next());
    assertThat(reader.getProgress()).isWithin(EPSILON).of(0.5);
    seen.add(reader.next());
    assertThat(reader.getProgress()).isWithin(EPSILON).of(1.0);
    assertThat(seen).containsExactly(domain, application);
    thrown.expect(NoSuchElementException.class);
    reader.next();
  }

  @Test
  public void testSuccess_entityReader_skipsPolymorphicMismatches() throws Exception {
    createTld("tld");
    persistEppResourceInFirstBucket(newDomainApplication("b.tld"));
    DomainResource domainA = persistEppResourceInFirstBucket(newDomainResource("a.tld"));
    InputReader<DomainResource> reader =
        createEntityInput(DomainResource.class).createReaders().get(0);
    reader.beginShard();
    reader.beginSlice();
    assertThat(reader.getProgress()).isWithin(EPSILON).of(0);
    assertThat(reader.next()).isEqualTo(domainA);
    // We can't reliably assert getProgress() here, since it counts before the postfilter that weeds
    // out polymorphic mismatches, and so depending on whether the domain or the application was
    // seen first it will be 0.5 or 1.0. However, there should be nothing left when we call next().
    thrown.expect(NoSuchElementException.class);
    reader.next();
  }

  @Test
  public void testSuccess_entityReader_filtersOnMultipleTypes() throws Exception {
    createTld("tld");
    DomainResource domain = persistEppResourceInFirstBucket(newDomainResource("a.tld"));
    HostResource host = persistEppResourceInFirstBucket(newHostResource("ns1.example.com"));
    persistEppResourceInFirstBucket(newContactResource("contact"));
    Set<EppResource> seen = new HashSet<>();
    InputReader<EppResource> reader =
        EppResourceInputs.<EppResource>createEntityInput(
              DomainResource.class, HostResource.class).createReaders().get(0);
    reader.beginShard();
    reader.beginSlice();
    assertThat(reader.getProgress()).isWithin(EPSILON).of(0);
    seen.add(reader.next());
    assertThat(reader.getProgress()).isWithin(EPSILON).of(0.5);
    seen.add(reader.next());
    assertThat(reader.getProgress()).isWithin(EPSILON).of(1.0);
    assertThat(seen).containsExactly(domain, host);
    thrown.expect(NoSuchElementException.class);
    reader.next();
  }

  @Test
  public void testSuccess_entityReader_noFilteringWhenUsingEppResource() throws Exception {
    createTld("tld");
    ContactResource contact = persistEppResourceInFirstBucket(newContactResource("contact"));
    // Specify the contact since persistActiveDomain{Application} creates a hidden one.
    DomainResource domain = persistEppResourceInFirstBucket(newDomainResource("a.tld", contact));
    DomainApplication application =
        persistEppResourceInFirstBucket(newDomainApplication("b.tld", contact));
    HostResource host = persistEppResourceInFirstBucket(newHostResource("ns1.example.com"));
    Set<EppResource> seen = new HashSet<>();
    InputReader<EppResource> reader = createEntityInput(EppResource.class).createReaders().get(0);
    reader.beginShard();
    reader.beginSlice();
    assertThat(reader.getProgress()).isWithin(EPSILON).of(0);
    seen.add(reader.next());
    assertThat(reader.getProgress()).isWithin(EPSILON).of(0.25);
    seen.add(reader.next());
    assertThat(reader.getProgress()).isWithin(EPSILON).of(0.5);
    seen.add(reader.next());
    assertThat(reader.getProgress()).isWithin(EPSILON).of(0.75);
    seen.add(reader.next());
    assertThat(reader.getProgress()).isWithin(EPSILON).of(1.0);
    assertThat(seen).containsExactly(domain, host, application, contact);
    thrown.expect(NoSuchElementException.class);
    reader.next();
  }
}
