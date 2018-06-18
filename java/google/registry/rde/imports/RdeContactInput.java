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

package google.registry.rde.imports;

import static com.google.common.math.IntMath.divide;
import static java.math.RoundingMode.CEILING;
import static java.math.RoundingMode.FLOOR;

import com.google.appengine.tools.cloudstorage.GcsFilename;
import com.google.appengine.tools.cloudstorage.GcsService;
import com.google.appengine.tools.cloudstorage.GcsServiceFactory;
import com.google.appengine.tools.cloudstorage.RetryParams;
import com.google.appengine.tools.mapreduce.Input;
import com.google.appengine.tools.mapreduce.InputReader;
import com.google.common.collect.ImmutableList;
import google.registry.config.RegistryConfig.ConfigModule;
import google.registry.gcs.GcsUtils;
import google.registry.model.contact.ContactResource;
import google.registry.rde.imports.RdeParser.RdeHeader;
import google.registry.xjc.JaxbFragment;
import google.registry.xjc.rdecontact.XjcRdeContactElement;
import java.io.InputStream;
import java.util.List;
import java.util.Optional;

/**
 * A MapReduce {@link Input} that imports {@link ContactResource} objects from an escrow file.
 *
 * <p>If a mapShards parameter has been specified, up to that many readers will be created
 * so that each map shard has one reader. If a mapShards parameter has not been specified, a
 * default number of readers will be created.
 */
public class RdeContactInput extends Input<JaxbFragment<XjcRdeContactElement>> {

  private static final long serialVersionUID = -366966393494008712L;
  private static final GcsService GCS_SERVICE =
      GcsServiceFactory.createGcsService(RetryParams.getDefaultInstance());

  /**
   * Default number of readers if map shards are not specified.
   */
  private static final int DEFAULT_READERS = 50;

  /**
   * Minimum number of records per reader.
   */
  private static final int MINIMUM_RECORDS_PER_READER = 100;

  /**
   * Optional argument to explicitly specify the number of readers.
   */
  private final int numReaders;
  private final String importBucketName;
  private final String importFileName;

  public RdeContactInput(Optional<Integer> mapShards, String importBucketName,
      String importFileName) {
    this.numReaders = mapShards.orElse(DEFAULT_READERS);
    this.importBucketName = importBucketName;
    this.importFileName = importFileName;
  }

  @Override
  public List<? extends InputReader<JaxbFragment<XjcRdeContactElement>>> createReaders() {
    int numReaders = this.numReaders;
    RdeHeader header = newParser().getHeader();
    int numberOfContacts = header.getContactCount().intValue();
    if (numberOfContacts / numReaders < MINIMUM_RECORDS_PER_READER) {
      numReaders = divide(numberOfContacts, MINIMUM_RECORDS_PER_READER, FLOOR);
      // use at least one reader
      numReaders = Math.max(numReaders, 1);
    }
    ImmutableList.Builder<RdeContactReader> builder = new ImmutableList.Builder<>();
    int contactsPerReader =
        Math.max(MINIMUM_RECORDS_PER_READER, divide(numberOfContacts, numReaders, CEILING));
    int offset = 0;
    for (int i = 0; i < numReaders; i++) {
      builder = builder.add(newReader(offset, contactsPerReader));
      offset += contactsPerReader;
    }
    return builder.build();
  }

  private RdeContactReader newReader(int offset, int maxResults) {
    return new RdeContactReader(importBucketName, importFileName, offset, maxResults);
  }

  private RdeParser newParser() {
    GcsUtils utils = new GcsUtils(GCS_SERVICE, ConfigModule.provideGcsBufferSize());
    GcsFilename filename = new GcsFilename(importBucketName, importFileName);
    try (InputStream xmlInput = utils.openInputStream(filename)) {
      return new RdeParser(xmlInput);
    } catch (Exception e) {
      throw new InitializationException(
          String.format("Error opening rde file %s/%s", importBucketName, importFileName), e);
    }
  }

  /**
   * Thrown when the input cannot initialize properly.
   */
  private static class InitializationException extends RuntimeException {

    public InitializationException(String message, Throwable cause) {
      super(message, cause);
    }
  }
}
