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

package google.registry.whois;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Strings.isNullOrEmpty;
import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.common.base.Joiner;
import google.registry.model.eppcommon.Address;
import google.registry.util.Idn;
import google.registry.xml.UtcDateTimeAdapter;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import javax.annotation.Nullable;
import org.joda.time.DateTime;

/** Base class for responses to WHOIS queries. */
abstract class WhoisResponseImpl implements WhoisResponse {

  /** Field name for ICANN problem reporting URL appended to all WHOIS responses. */
  private static final String ICANN_REPORTING_URL_FIELD =
      "URL of the ICANN Whois Inaccuracy Complaint Form";

  /** ICANN problem reporting URL appended to all WHOIS responses. */
  private static final String ICANN_REPORTING_URL = "https://www.icann.org/wicf/";

  /** The time at which this response was created. */
  private final DateTime timestamp;

  WhoisResponseImpl(DateTime timestamp) {
    this.timestamp = checkNotNull(timestamp, "timestamp");
  }

  @Override
  public DateTime getTimestamp() {
    return timestamp;
  }

  /**
   * Translates a hostname to its unicode representation if desired.
   *
   * @param hostname is assumed to be in its canonical ASCII form from the database.
   */
  static String maybeFormatHostname(String hostname, boolean preferUnicode) {
    return preferUnicode ? Idn.toUnicode(hostname) : hostname;
  }

  static <T> T chooseByUnicodePreference(
      boolean preferUnicode, @Nullable T localized, @Nullable T internationalized) {
    if (preferUnicode) {
      return Optional.ofNullable(localized).orElse(internationalized);
    } else {
      return Optional.ofNullable(internationalized).orElse(localized);
    }
  }

  /** Writer for outputting data in the WHOIS format. */
  abstract static class Emitter<E extends Emitter<E>> {

    private final StringBuilder stringBuilder = new StringBuilder();

    @SuppressWarnings("unchecked")
    private E thisCastToDerived() {
      return (E) this;
    }

    E emitNewline() {
      stringBuilder.append("\r\n");
      return thisCastToDerived();
    }

    /**
     * Helper method that loops over a set of values and calls {@link #emitField}. This method will
     * turn each value into a string using the provided callback and then sort those strings so the
     * textual output is deterministic (which is important for unit tests). The ideal solution would
     * be to use {@link java.util.SortedSet} but that would require reworking the models.
     */
    <T> E emitSet(String title, Set<T> values, Function<T, String> transform) {
      return emitList(title, values.stream().map(transform).sorted().collect(toImmutableList()));
    }

    /** Helper method that loops over a list of values and calls {@link #emitField}. */
    E emitList(String title, Iterable<String> values) {
      for (String value : values) {
        emitField(title, value);
      }
      return thisCastToDerived();
    }

    /** Emit the field name and value followed by a newline, but only if a value exists. */
    E emitFieldIfDefined(String name, @Nullable String value) {
      if (isNullOrEmpty(value)) {
        return thisCastToDerived();
      }
      stringBuilder.append(cleanse(name)).append(':');
      stringBuilder.append(' ').append(cleanse(value));
      return emitNewline();
    }

    /** Emit the field name and value followed by a newline. */
    E emitField(String name, @Nullable String value) {
      stringBuilder.append(cleanse(name)).append(':');
      if (!isNullOrEmpty(value)) {
        stringBuilder.append(' ').append(cleanse(value));
      }
      return emitNewline();
    }

    /** Emit a multi-part field name and value followed by a newline, but only if a value exists. */
    E emitFieldIfDefined(List<String> nameParts, String value) {
      if (isNullOrEmpty(value)) {
        return thisCastToDerived();
      }
      return emitField(nameParts, value);
    }

    /** Emit a multi-part field name and value followed by a newline. */
    E emitField(List<String> nameParts, String value) {
      return emitField(Joiner.on(' ').join(nameParts), value);
    }

    /** Emit a contact address. */
    E emitAddress(@Nullable String prefix, @Nullable Address address, boolean fullOutput) {
      prefix = isNullOrEmpty(prefix) ? "" : prefix + " ";
      if (address != null) {
        if (fullOutput) {
          emitList(prefix + "Street", address.getStreet());
          emitField(prefix + "City", address.getCity());
        }
        emitField(prefix + "State/Province", address.getState());
        if (fullOutput) {
          emitField(prefix + "Postal Code", address.getZip());
        }
        emitField(prefix + "Country", address.getCountryCode());
      }
      return thisCastToDerived();
    }

    /** Emit Whois Inaccuracy Complaint Form link. Only used for domain queries. */
    E emitWicfLink() {
      emitField(ICANN_REPORTING_URL_FIELD, ICANN_REPORTING_URL);
      return thisCastToDerived();
    }

    /** Returns raw text that should be appended to the end of ALL WHOIS responses. */
    E emitLastUpdated(DateTime timestamp) {
      // We are assuming that our WHOIS database is always completely up to date, since it's
      // querying the live backend Datastore.
      stringBuilder
          .append(">>> Last update of WHOIS database: ")
          .append(UtcDateTimeAdapter.getFormattedString(timestamp))
          .append(" <<<\r\n\r\n");
      return thisCastToDerived();
    }

    /** Returns raw text that should be appended to the end of ALL WHOIS responses. */
    E emitFooter(String disclaimer) {
      stringBuilder.append(disclaimer.replaceAll("\r?\n", "\r\n").trim()).append("\r\n");
      return thisCastToDerived();
    }

    /** Emits a string directly, followed by a newline. */
    protected E emitRawLine(String string) {
      stringBuilder.append(string);
      return emitNewline();
    }

    /** Remove ASCII control characters like {@code \n} which could be used to forge output. */
    private String cleanse(String value) {
      return value.replaceAll("[\\x00-\\x1f]", " ");
    }

    @Override
    public String toString() {
      return stringBuilder.toString();
    }
  }

  /** An emitter that needs no special logic. */
  static class BasicEmitter extends Emitter<BasicEmitter> {}
}
