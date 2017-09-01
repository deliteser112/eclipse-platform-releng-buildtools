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

package google.registry.tools;

import static com.google.common.truth.Truth.assertThat;
import static google.registry.tools.LevelDbLogReader.BLOCK_SIZE;

import com.google.appengine.api.datastore.Entity;
import com.google.appengine.api.datastore.EntityTranslator;
import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableSet;
import com.google.storage.onestore.v3.OnestoreEntity.EntityProto;
import google.registry.testing.AppEngineRule;
import google.registry.tools.LevelDbLogReader.ChunkType;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class RecordAccumulatorTest {

  private static final int BASE_ID = 1001;
  private static final String TEST_ENTITY_KIND = "TestEntity";

  @Rule public final TemporaryFolder tempFs = new TemporaryFolder();
  @Rule public final AppEngineRule appEngine = AppEngineRule.builder().withDatastore().build();

  @Test
  public void testReadDirectory() throws IOException {
    File subdir = tempFs.newFolder("folder");
    LevelDbFileBuilder builder = new LevelDbFileBuilder(new File(subdir, "data1"));

    // Note that we need to specify property values as "Long" for property comparisons to work
    // correctly because that's how they are deserialized from protos.
    ComparableEntity e1 =
        builder.addEntityProto(
            BASE_ID,
            Property.create("eeny", 100L),
            Property.create("meeny", 200L),
            Property.create("miney", 300L));
    ComparableEntity e2 =
        builder.addEntityProto(
            BASE_ID + 1,
            Property.create("eeny", 100L),
            Property.create("meeny", 200L),
            Property.create("miney", 300L));
    builder.build();

    builder = new LevelDbFileBuilder(new File(subdir, "data2"));

    // Duplicate of the record in the other file.
    builder.addEntityProto(
        BASE_ID,
        Property.create("eeny", 100L),
        Property.create("meeny", 200L),
        Property.create("miney", 300L));

    ComparableEntity e3 =
        builder.addEntityProto(
            BASE_ID + 2,
            Property.create("moxy", 100L),
            Property.create("fruvis", 200L),
            Property.create("cortex", 300L));
    builder.build();

    ImmutableSet<ComparableEntity> entities =
        new RecordAccumulator().readDirectory(subdir).getComparableEntitySet();
    assertThat(entities).containsExactly(e1, e2, e3);
  }

  /** Utility class for building a leveldb logfile. */
  private static final class LevelDbFileBuilder {
    private final FileOutputStream out;
    private byte[] currentBlock = new byte[BLOCK_SIZE];

    // Write position in the current block.
    private int currentPos = 0;

    LevelDbFileBuilder(File file) throws FileNotFoundException {
      out = new FileOutputStream(file);
    }

    /**
     * Adds a record containing a new entity protobuf to the file.
     *
     * <p>Returns the ComparableEntity object rather than "this" so that we can check for the
     * presence of the entity in the result set.
     */
    private ComparableEntity addEntityProto(int id, Property... properties) throws IOException {
      Entity entity = new Entity(TEST_ENTITY_KIND, id);
      for (Property prop : properties) {
        entity.setProperty(prop.name(), prop.value());
      }
      EntityProto proto = EntityTranslator.convertToPb(entity);
      byte[] protoBytes = proto.toByteArray();
      if (protoBytes.length > BLOCK_SIZE - currentPos) {
        out.write(currentBlock);
        currentBlock = new byte[BLOCK_SIZE];
      }

      currentPos = LevelDbUtil.addRecord(currentBlock, currentPos, ChunkType.FULL, protoBytes);
      return new ComparableEntity(entity);
    }

    /** Writes all remaining data and closes the block. */
    void build() throws IOException {
      out.write(currentBlock);
      out.close();
    }
  }

  @AutoValue
  abstract static class Property {
    static Property create(String name, Object value) {
      return new AutoValue_RecordAccumulatorTest_Property(name, value);
    }

    abstract String name();

    abstract Object value();
  }
}
