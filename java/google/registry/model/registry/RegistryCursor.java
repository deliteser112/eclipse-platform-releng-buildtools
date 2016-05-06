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

package google.registry.model.registry;

import static google.registry.model.ofy.ObjectifyService.ofy;

import com.google.common.base.Optional;

import com.googlecode.objectify.Key;
import com.googlecode.objectify.annotation.Entity;
import com.googlecode.objectify.annotation.Id;
import com.googlecode.objectify.annotation.Parent;

import google.registry.model.ImmutableObject;
import google.registry.model.common.Cursor;

import org.joda.time.DateTime;

/** Shared entity for per-TLD date cursors. */
@Entity
public class RegistryCursor extends ImmutableObject {

  // TODO(b/28386088): Drop this class once all registry cursors have been saved in parallel as
  // new-style Cursors (either through business-as-usual operations or UpdateCursorsCommand).

  /** The types of cursors, used as the string id field for each cursor in datastore. */
  public enum CursorType {
    /** Cursor for ensuring rolling transactional isolation of BRDA staging operation. */
    BRDA,

    /** Cursor for ensuring rolling transactional isolation of RDE report operation. */
    RDE_REPORT,

    /** Cursor for ensuring rolling transactional isolation of RDE staging operation. */
    RDE_STAGING,

    /** Cursor for ensuring rolling transactional isolation of RDE upload operation. */
    RDE_UPLOAD,

    /**
     * Cursor that tracks the last time we talked to the escrow provider's SFTP server for a given
     * TLD.
     *
     * <p>Our escrow provider has an odd feature where separate deposits uploaded within two hours
     * of each other will be merged into a single deposit. This is problematic in situations where
     * the cursor might be a few days behind and is trying to catch up.
     *
     * <p>The way we solve this problem is by having {@code RdeUploadAction} check this cursor
     * before performing an upload for a given TLD. If the cursor is less than two hours old, the
     * action will fail with a status code above 300 and App Engine will keep retrying the action
     * until it's ready.
     */
    RDE_UPLOAD_SFTP;
  }

  @Parent
  Key<Registry> registry;

  @Id
  String cursorType;

  DateTime date;

  /** Convenience shortcut to load a cursor for a given registry and cursor type. */
  public static Optional<DateTime> load(Registry registry, CursorType cursorType) {
    Key<RegistryCursor> key =
        Key.create(Key.create(registry), RegistryCursor.class, cursorType.name());
    RegistryCursor cursor = ofy().load().key(key).now();
    return Optional.fromNullable(cursor == null ? null : cursor.date);
  }

  /** Convenience shortcut to save a cursor. */
  public static void save(Registry registry, CursorType cursorType, DateTime value) {
    ofy().save().entity(create(registry, cursorType, value));
    // In parallel, save the new cursor type alongside the old.
    ofy()
        .save()
        .entity(Cursor.create(Cursor.CursorType.valueOf(cursorType.name()), value, registry));
  }

  /** Creates a new cursor instance. */
  public static RegistryCursor create(Registry registry, CursorType cursorType, DateTime date) {
    RegistryCursor instance = new RegistryCursor();
    instance.registry = Key.create(registry);
    instance.cursorType = cursorType.name();
    instance.date = date;
    return instance;
  }
}
