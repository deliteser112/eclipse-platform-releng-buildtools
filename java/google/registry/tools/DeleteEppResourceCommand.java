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

package google.registry.tools;

import static google.registry.model.ofy.ObjectifyService.ofy;
import static google.registry.util.DateTimeUtils.isBeforeOrAt;
import static org.joda.time.DateTimeZone.UTC;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableList;
import com.googlecode.objectify.Key;
import google.registry.model.EppResource;
import google.registry.model.index.ForeignKeyIndex;
import google.registry.model.reporting.HistoryEntry;
import google.registry.tools.params.EppResourceTypeParameter;
import java.util.List;
import java.util.Map;
import org.joda.time.DateTime;

/** Command to soft-delete a list of EppResources of a given type specified by ROIDs. */
@Parameters(separators = " =", commandDescription = "Soft-delete EPP resources.")
final class DeleteEppResourceCommand extends MutatingCommand {

  private static final String DEFAULT_DELETION_REASON = "Deleted using registry_tool.";

  @Parameter(
      description = "List of EppResource ROIDs to soft-delete.",
      required = true)
  private List<String> roids;

  @Parameter(
      names = {"-t", "--type"},
      description = "EPP resource type.",
      required = true)
  private EppResourceTypeParameter resourceType;

  @Parameter(
      names = {"-r", "--reason"},
      description = "Deletion reason message.")
  private String reason;

  @Override
  protected void init() throws Exception {
    DateTime now = DateTime.now(UTC);
    ImmutableList.Builder<Key<EppResource>> builder = new ImmutableList.Builder<>();
    for (String roid : roids) {
      builder.add(Key.create(resourceType.getType(), roid));
    }
    ImmutableList<Key<EppResource>> keys = builder.build();
    Map<Key<EppResource>, EppResource> resources = ofy().load().keys(keys);
    for (Key<EppResource> key : keys) {
      if (resources.containsKey(key)) {
        EppResource resource = resources.get(key);
        if (isBeforeOrAt(resource.getDeletionTime(), now)) {
          System.out.printf("Resource already deleted: %s\n", key);
        } else {
          stageEntityChange(resource, resource.asBuilder().setDeletionTime(now).build());
          HistoryEntry deletionRecord = new HistoryEntry.Builder()
              .setModificationTime(now)
              .setBySuperuser(true)
              .setClientId(resource.getCurrentSponsorClientId())
              .setParent(key)
              .setReason(MoreObjects.firstNonNull(reason, DEFAULT_DELETION_REASON))
              .setType(HistoryEntry.Type.SYNTHETIC)
              .build();
          stageEntityChange(null, deletionRecord);
          handleForeignKeyIndex(key, resource, now);
          flushTransaction();
        }
      } else {
        System.out.printf("Resource does not exist: %s\n", key);
      }
    }
  }

  private void handleForeignKeyIndex(Key<EppResource> key, EppResource resource, DateTime now) {
    ForeignKeyIndex<?> fki = ofy().load().key(ForeignKeyIndex.createKey(resource)).now();
    if (fki == null) {
      System.out.printf("Creating non-existent ForeignKeyIndex for: %s\n", key);
      stageEntityChange(null, ForeignKeyIndex.create(resource, now));
    } else {
      if (fki.getResourceKey().equals(key)) {
        if (isBeforeOrAt(fki.getDeletionTime(), now)) {
          System.out.printf("ForeignKeyIndex already deleted for: %s\n", key);
        } else {
          // Theoretically this is the only code path that should ever be taken, as there should
          // always be exactly one non-soft-deleted FKI for every non-soft-deleted EppResource. The
          // other paths exist to correctly handle potential situations caused by bad data.
          stageEntityChange(fki, ForeignKeyIndex.create(resource, now));
        }
      } else {
        System.out.printf("Found ForeignKeyIndex pointing to different resource for: %s\n", key);
        System.out.printf("It was: %s\n", fki);
      }
    }
  }
}
