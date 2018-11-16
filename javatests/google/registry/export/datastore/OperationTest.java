// Copyright 2018 The Nomulus Authors. All Rights Reserved.
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

package google.registry.export.datastore;

import static com.google.common.truth.Truth.assertThat;

import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import google.registry.export.datastore.Operation.CommonMetadata;
import google.registry.export.datastore.Operation.Metadata;
import google.registry.export.datastore.Operation.Progress;
import google.registry.testing.TestDataHelper;
import java.io.IOException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for unmarshalling {@link Operation} and its member types. */
@RunWith(JUnit4.class)
public class OperationTest {
  private static final JsonFactory JSON_FACTORY = JacksonFactory.getDefaultInstance();

  @Test
  public void testCommonMetadata_unmarshall() throws IOException {
    CommonMetadata commonMetadata = loadJson("common_metadata.json", CommonMetadata.class);
    assertThat(commonMetadata.getState()).isEqualTo("SUCCESSFUL");
    assertThat(commonMetadata.getOperationType()).isEqualTo("EXPORT_ENTITIES");
  }

  @Test
  public void testProgress_unmarshall() throws IOException {
    Progress progress = loadJson("progress.json", Progress.class);
    assertThat(progress.getWorkCompleted()).isEqualTo(51797);
    assertThat(progress.getWorkEstimated()).isEqualTo(54513);
  }

  @Test
  public void testMetadata_unmarshall() throws IOException {
    Metadata metadata = loadJson("metadata.json", Metadata.class);
    assertThat(metadata.getCommonMetadata().getOperationType()).isEqualTo("EXPORT_ENTITIES");
    assertThat(metadata.getCommonMetadata().getState()).isEqualTo("SUCCESSFUL");
  }

  @Test
  public void testOperation_unmarshall() throws IOException {
    Operation operation = loadJson("operation.json", Operation.class);
    assertThat(operation.getName())
        .startsWith("projects/domain-registry-alpha/operations/ASAzNjMwOTEyNjUJ");
    assertThat(operation.isProcessing()).isTrue();
    assertThat(operation.isSuccessful()).isFalse();
    assertThat(operation.isDone()).isFalse();
  }

  @Test
  public void testOperationList_unmarshall() throws IOException {
    Operation.OperationList operationList =
        loadJson("operation_list.json", Operation.OperationList.class);
    assertThat(operationList.toList()).hasSize(2);
  }

  private static <T> T loadJson(String fileName, Class<T> type) throws IOException {
    return JSON_FACTORY.fromString(TestDataHelper.loadFile(OperationTest.class, fileName), type);
  }
}
