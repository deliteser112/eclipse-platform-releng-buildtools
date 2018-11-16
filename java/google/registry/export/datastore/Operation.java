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

import static com.google.common.base.Preconditions.checkState;

import com.google.api.client.json.GenericJson;
import com.google.api.client.util.Key;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import google.registry.export.datastore.DatastoreAdmin.Get;
import java.util.List;

/** Model object that describes the details of an export or import operation in Cloud Datastore. */
public class Operation extends GenericJson {

  private static final String STATE_SUCCESS = "SUCCESSFUL";
  private static final String STATE_PROCESSING = "PROCESSING";

  @Key private String name;
  @Key private Metadata metadata;
  @Key private boolean done;

  /** For JSON deserialization. */
  public Operation() {}

  /** Returns the name of this operation, which may be used in a {@link Get} request. */
  public String getName() {
    checkState(name != null, "Name must not be null.");
    return name;
  }

  public boolean isDone() {
    return done;
  }

  public String getState() {
    checkState(metadata != null, "Response metadata missing.");
    return metadata.getCommonMetadata().getState();
  }

  public boolean isSuccessful() {
    checkState(metadata != null, "Response metadata missing.");
    return getState().equals(STATE_SUCCESS);
  }

  public boolean isProcessing() {
    checkState(metadata != null, "Response metadata missing.");
    return getState().equals(STATE_PROCESSING);
  }

  /** Models the common metadata properties of all operations. */
  public static class CommonMetadata extends GenericJson {

    @Key private String operationType;
    @Key private String state;

    public CommonMetadata() {}

    String getOperationType() {
      checkState(!Strings.isNullOrEmpty(operationType), "operationType may not be null or empty");
      return operationType;
    }

    String getState() {
      checkState(!Strings.isNullOrEmpty(state), "state may not be null or empty");
      return state;
    }
  }

  /** Models the metadata of a Cloud Datatore export or import operation. */
  public static class Metadata extends GenericJson {
    @Key("common")
    private CommonMetadata commonMetadata;

    @Key private Progress progressEntities;
    @Key private Progress progressBytes;
    @Key private EntityFilter entityFilter;
    @Key private String outputUrlPrefix;

    public Metadata() {}

    CommonMetadata getCommonMetadata() {
      checkState(commonMetadata != null, "CommonMetadata field is null.");
      return commonMetadata;
    }

    public Progress getProgressEntities() {
      return progressEntities;
    }

    public Progress getProgressBytes() {
      return progressBytes;
    }

    public EntityFilter getEntityFilter() {
      return entityFilter;
    }

    public String getOutputUrlPrefix() {
      return outputUrlPrefix;
    }
  }

  /** Progress of an export or import operation. */
  public static class Progress extends GenericJson {
    @Key private long workCompleted;
    @Key private long workEstimated;

    public Progress() {}

    long getWorkCompleted() {
      return workCompleted;
    }

    public long getWorkEstimated() {
      return workEstimated;
    }
  }

  /** List of {@link Operation Operations}. */
  public static class OperationList extends GenericJson {
    @Key private List<Operation> operations;

    /** For JSON deserialization. */
    public OperationList() {}

    ImmutableList<Operation> toList() {
      return ImmutableList.copyOf(operations);
    }
  }
}
