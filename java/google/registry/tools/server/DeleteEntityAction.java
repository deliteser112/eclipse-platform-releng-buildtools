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

package google.registry.tools.server;

import static com.google.appengine.api.datastore.DatastoreServiceFactory.getDatastoreService;
import static com.googlecode.objectify.Key.create;
import static google.registry.model.ofy.ObjectifyService.ofy;

import com.google.appengine.api.datastore.Entity;
import com.google.appengine.api.datastore.EntityNotFoundException;
import com.google.appengine.api.datastore.Key;
import com.google.appengine.api.datastore.KeyFactory;
import com.google.common.base.Optional;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.googlecode.objectify.VoidWork;
import com.googlecode.objectify.impl.EntityMetadata;
import google.registry.request.Action;
import google.registry.request.HttpException.BadRequestException;
import google.registry.request.Parameter;
import google.registry.request.Response;
import google.registry.util.FormattingLogger;
import javax.inject.Inject;

/**
 * An action to delete entities in Datastore specified by raw key ids, which can be found in
 * Datastore Viewer in the AppEngine console - it's the really long alphanumeric key that is
 * labeled "Entity key" on the page for an individual entity.
 *
 * <p>rawKeys is the only required parameter. It is a comma-delimited list of Strings.
 *
 * <p><b>WARNING:</b> This servlet can be dangerous if used incorrectly as it can bypass checks on
 * deletion (including whether the entity is referenced by other entities) and it does not write
 * commit log entries for non-registered types. It should mainly be used for deleting testing or
 * malformed data that cannot be properly deleted using existing tools. Generally, if there already
 * exists an entity-specific deletion command, then use that one instead.
 */
@Action(path = DeleteEntityAction.PATH)
public class DeleteEntityAction implements Runnable {

  private static final FormattingLogger logger = FormattingLogger.getLoggerForCallerClass();

  public static final String PATH = "/_dr/admin/deleteEntity";
  public static final String PARAM_RAW_KEYS = "rawKeys";

  @Inject @Parameter(PARAM_RAW_KEYS) String rawKeys;
  @Inject Response response;
  @Inject DeleteEntityAction() {}

  @Override
  public void run() {
    // Get raw key strings from request
    ImmutableList.Builder<Object> ofyDeletionsBuilder = new ImmutableList.Builder<>();
    ImmutableList.Builder<Key> rawDeletionsBuilder = new ImmutableList.Builder<>();
    for (String rawKeyString : Splitter.on(',').split(rawKeys)) {
      // Convert raw keys string to real keys. Try to load it from Objectify if it's a registered
      // type, and fall back to DatastoreService if its not registered.
      Key rawKey = KeyFactory.stringToKey(rawKeyString);
      Optional<Object> ofyEntity = loadOfyEntity(rawKey);
      if (ofyEntity.isPresent()) {
        ofyDeletionsBuilder.add(ofyEntity.get());
        continue;
      }
      Optional<Entity> rawEntity = loadRawEntity(rawKey);
      if (rawEntity.isPresent()) {
        rawDeletionsBuilder.add(rawEntity.get().getKey());
        continue;
      }
      // The entity could not be found by either Objectify or the datastore service
      throw new BadRequestException("Could not find entity with key " + rawKeyString);
    }
    // Delete raw entities.
    ImmutableList<Key> rawDeletions = rawDeletionsBuilder.build();
    getDatastoreService().delete(rawDeletions);
    // Delete ofy entities.
    final ImmutableList<Object> ofyDeletions = ofyDeletionsBuilder.build();
    ofy().transactNew(new VoidWork() {
        @Override
        public void vrun() {
          ofy().delete().entities(ofyDeletions).now();
        }});
    String message = String.format(
        "Deleted %d raw entities and %d registered entities",
        rawDeletions.size(),
        ofyDeletions.size());
    logger.info(message);
    response.setPayload(message);
  }

  private Optional<Object> loadOfyEntity(Key rawKey) {
    EntityMetadata<Object> metadata = ofy().factory().getMetadata(rawKey.getKind());
    return Optional.fromNullable(metadata == null ? null : ofy().load().key(create(rawKey)).now());
  }

  private Optional<Entity> loadRawEntity(Key rawKey) {
    try {
      return Optional.fromNullable(getDatastoreService().get(rawKey));
    } catch (EntityNotFoundException e) {
      logger.warningfmt(e, "Could not load entity from datastore service with key %s",
          rawKey.toString());
      return Optional.absent();
    }
  }
}
