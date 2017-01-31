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

package google.registry.model.poll;

import static google.registry.model.ofy.ObjectifyService.ofy;

import com.google.common.base.Converter;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableBiMap;
import com.googlecode.objectify.Key;
import google.registry.model.EppResource;
import google.registry.model.contact.ContactResource;
import google.registry.model.domain.DomainBase;
import google.registry.model.host.HostResource;
import google.registry.model.reporting.HistoryEntry;
import java.util.List;

/**
 * A converter between external key strings for {@link PollMessage}s (i.e. what registrars use to
 * identify and ACK them) and Datastore keys to the resource.
 *
 * <p>The format of the key string is A-B-C-D-E as follows:
 *
 * <pre>
 *   A = EppResource.typeId (decimal)
 *   B = EppResource.repoId prefix (STRING)
 *   C = EppResource.repoId suffix (STRING)
 *   D = HistoryEntry.id (decimal)
 *   E = PollMessage.id (decimal)
 * </pre>
 */
public class PollMessageExternalKeyConverter extends Converter<Key<PollMessage>, String> {

  /** An exception thrown when an external key cannot be parsed. */
  public static class PollMessageExternalKeyParseException extends RuntimeException {}

  /**
   * A map of IDs used in external keys corresponding to which EppResource class the poll message
   * belongs to.
   */
  public static final ImmutableBiMap<Class<? extends EppResource>, Long> EXTERNAL_KEY_CLASS_ID_MAP =
      ImmutableBiMap.<Class<? extends EppResource>, Long>of(
          DomainBase.class, 1L,
          ContactResource.class, 2L,
          HostResource.class, 3L);

  @Override
  protected String doForward(Key<PollMessage> key) {
    @SuppressWarnings("unchecked")
    Key<EppResource> ancestorResource =
        (Key<EppResource>) (Key<?>) key.getParent().getParent();
    long externalKeyClassId = EXTERNAL_KEY_CLASS_ID_MAP.get(
        ofy().factory().getMetadata(ancestorResource.getKind()).getEntityClass());
    return String.format("%d-%s-%d-%d",
        externalKeyClassId,
        ancestorResource.getName(),
        key.getParent().getId(),
        key.getId());
  }

  /**
   * Returns an Objectify Key to a PollMessage corresponding with the external key string.
   *
   * @throws PollMessageExternalKeyParseException if the external key has an invalid format.
   */
  @Override
  protected Key<PollMessage> doBackward(String externalKey) {
    List<String> idComponents = Splitter.on('-').splitToList(externalKey);
    if (idComponents.size() != 5) {
      throw new PollMessageExternalKeyParseException();
    }
    try {
      Class<?> resourceClazz =
          EXTERNAL_KEY_CLASS_ID_MAP.inverse().get(Long.parseLong(idComponents.get(0)));
      if (resourceClazz == null) {
        throw new PollMessageExternalKeyParseException();
      }
      return Key.create(
          Key.create(
              Key.create(
                  null,
                  resourceClazz,
                  String.format("%s-%s", idComponents.get(1), idComponents.get(2))),
              HistoryEntry.class,
              Long.parseLong(idComponents.get(3))),
          PollMessage.class,
          Long.parseLong(idComponents.get(4)));
    } catch (NumberFormatException e) {
      throw new PollMessageExternalKeyParseException();
    }
  }
}

