// Copyright 2016 The Nomulus Authors. All Rights Reserved.
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

import static com.google.appengine.api.datastore.DatastoreServiceFactory.getDatastoreService;
import static com.google.appengine.api.taskqueue.QueueFactory.getQueue;
import static com.google.common.base.Strings.nullToEmpty;

import com.google.appengine.api.datastore.Entity;
import com.google.appengine.api.datastore.Query;
import com.google.appengine.api.modules.ModulesService;
import com.google.appengine.api.modules.ModulesServiceFactory;
import com.google.appengine.api.taskqueue.TaskHandle;
import com.google.appengine.api.taskqueue.TaskOptions;
import com.google.appengine.api.taskqueue.TaskOptions.Method;
import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import google.registry.util.NonFinalForTesting;
import java.util.NoSuchElementException;

/** An object providing methods for starting and querying datastore backups. */
public class DatastoreBackupService {

  /** The internal kind name used for entities storing information about datastore backups. */
  static final String BACKUP_INFO_KIND = "_AE_Backup_Information";

  /** The name of the app version used for hosting the Datastore Admin functionality. */
  static final String DATASTORE_ADMIN_VERSION_NAME = "ah-builtin-python-bundle";

  @NonFinalForTesting
  private static ModulesService modulesService = ModulesServiceFactory.getModulesService();

  /**
   * Returns an instance of this service.
   *
   * <p>This method exists to allow for making the service a singleton object if desired at some
   * future point; the choice is meaningless right now because the service maintains no state.
   * That means its client-facing methods could in theory be static methods, but they are not
   * because that makes it difficult to mock this service in clients.
   */
  public static DatastoreBackupService get() {
    return new DatastoreBackupService();
  }

  /**
   * Generates the TaskOptions needed to trigger an AppEngine datastore backup job.
   *
   * @see "https://developers.google.com/appengine/articles/scheduled_backups"
   */
  private static TaskOptions makeTaskOptions(
      String queue, String name, String gcsBucket, ImmutableSet<String> kinds) {
    String hostname = modulesService.getVersionHostname("default", DATASTORE_ADMIN_VERSION_NAME);
    TaskOptions options = TaskOptions.Builder.withUrl("/_ah/datastore_admin/backup.create")
        .header("Host", hostname)
        .method(Method.GET)
        .param("name", name + "_")  // Add underscore since the name will be used as a prefix.
        .param("filesystem", "gs")
        .param("gs_bucket_name", gcsBucket)
        .param("queue", queue);
    for (String kind : kinds) {
      options.param("kind", kind);
    }
    return options;
  }

  /**
   * Launches a new datastore backup with the given name, GCS bucket, and set of kinds by
   * submitting a task to the given task queue, and returns a handle to that task.
   */
  public TaskHandle launchNewBackup(
      String queue, String name, String gcsBucket, ImmutableSet<String> kinds) {
    return getQueue(queue).add(makeTaskOptions(queue, name, gcsBucket, kinds));
  }

  /** Return an iterable of all datastore backups whose names have the given string prefix. */
  public Iterable<DatastoreBackupInfo> findAllByNamePrefix(final String namePrefix) {
    // Need the raw DatastoreService to access the internal _AE_Backup_Information entities.
    // TODO(b/19081037): make an Objectify entity class for these raw datastore entities instead.
    return FluentIterable
        .from(getDatastoreService().prepare(new Query(BACKUP_INFO_KIND)).asIterable())
        .filter(new Predicate<Entity>() {
            @Override
            public boolean apply(Entity entity) {
              return nullToEmpty((String) entity.getProperty("name")).startsWith(namePrefix);
            }})
        .transform(new Function<Entity, DatastoreBackupInfo>() {
            @Override
            public DatastoreBackupInfo apply(Entity entity) {
              return new DatastoreBackupInfo(entity);
            }});
  }

  /**
   * Return a single DatastoreBackup that uniquely matches this name prefix.  Throws an IAE
   * if no backups match or if more than one backup matches.
   */
  public DatastoreBackupInfo findByName(final String namePrefix) {
    try {
      return Iterables.getOnlyElement(findAllByNamePrefix(namePrefix));
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("More than one backup with name prefix " + namePrefix, e);
    } catch (NoSuchElementException e) {
      throw new IllegalArgumentException("No backup found with name prefix " + namePrefix, e);
    }
  }
}
