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

package google.registry.tools.javascrap;

import static google.registry.model.ofy.ObjectifyService.ofy;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

import com.beust.jcommander.Parameters;
import com.googlecode.objectify.Key;

import google.registry.model.common.Cursor;
import google.registry.model.common.EntityGroupRoot;
import google.registry.model.registry.Registry;
import google.registry.model.registry.RegistryCursor;
import google.registry.tools.Command.RemoteApiCommand;

import org.joda.time.DateTime;

import java.util.HashMap;
import java.util.Map.Entry;

/**
 * Scrap command that loads all legacy {@link RegistryCursor} entities and new {@link Cursor}
 * entities, and reports any mismatches (after filtering out new {@code RECURRING_BILLING} cursors).
 */
@Parameters(separators = " =", commandDescription = "Verify dual-written cursors.")
public class VerifyDualWrittenCursorsCommand implements RemoteApiCommand {

  @Override
  public void run() throws Exception {

    Iterable<Cursor> cursors =
        ofy().load().type(Cursor.class).ancestor(EntityGroupRoot.getCrossTldKey());
    Iterable<RegistryCursor> registryCursors =
        ofy().load().type(RegistryCursor.class).ancestor(EntityGroupRoot.getCrossTldKey());

    HashMap<String, Cursor> cursorMap = Maps.newHashMap();
    HashMap<String, RegistryCursor> registryCursorMap = Maps.newHashMap();

    // Build map of key name to new cursor.
    for (Cursor c : cursors) {
      String keyName = Key.create(c).getName();
      if (!keyName.substring(keyName.indexOf('_') + 1).endsWith(
          Cursor.CursorType.RECURRING_BILLING.toString())) {
        cursorMap.put(keyName, c);
      }
    }

    // Build map of key name to old RegistryCursor.
    for (RegistryCursor rc : registryCursors) {
      Key<RegistryCursor> key = Key.create(rc);
      registryCursorMap.put(
          String.format(
              "%s_%s",
              key.getParent().getString(),
              key.getName()),
          rc);
    }

    // Iterate through new cursor map, match to old RegistryCursor and check timestamp if a match
    // exists, or report missing RegistryCursor otherwise.
    for (Entry<String, Cursor> entry : cursorMap.entrySet()) {
      String key = entry.getKey();
      String registryKey = key.substring(0, key.indexOf('_'));
      String cursorType = key.substring(key.indexOf('_') + 1);
      Registry registry = Registry.get(Key.create(registryKey).getName()); 
      RegistryCursor registryCursor = registryCursorMap.get(entry.getKey());
      if (registryCursor == null) {
        System.out.println(
            String.format(
                "RegistryCursor missing: TLD %s, cursorType %s",
                registry.getTldStr(),
                cursorType));
      } else {
        DateTime cursorTime = entry.getValue().getCursorTime();
        DateTime matchingCursorTime =
            RegistryCursor.load(registry, RegistryCursor.CursorType.valueOf(cursorType)).get();
        if (!cursorTime.equals(matchingCursorTime)) {
          System.out.println(
              String.format(
                  "Mismatch for cursor for type %s and tld %s (old cursor: %s, new cursor: %s)",
                  cursorType,
                  registry.getTldStr(),
                  cursorTime,
                  matchingCursorTime));
        }
      }
    }

    // Perform set differences by matching new-style cursor names, report any discrepancies.
    for (String s : Sets.difference(registryCursorMap.keySet(), cursorMap.keySet())) {
      String registryKey = s.substring(0, s.indexOf('_'));
      String cursorType = s.substring(s.indexOf('_') + 1);
      Registry registry = Registry.get(Key.create(registryKey).getName()); 
      System.out.println(
          String.format("Cursor missing: TLD %s, cursorType %s", registry.getTldStr(), cursorType));
    }
  }
}
