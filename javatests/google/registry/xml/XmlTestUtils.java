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

package google.registry.xml;

import static com.google.common.base.MoreObjects.firstNonNull;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.truth.Truth.assert_;
import static google.registry.util.DiffUtils.prettyPrintDeepDiff;
import static org.joda.time.DateTimeZone.UTC;

import com.google.common.collect.ImmutableSet;
import com.google.common.net.InetAddresses;
import com.google.common.net.InternetDomainName;

import org.joda.time.format.ISODateTimeFormat;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.XML;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

/** Helper class for unit tests that need XML. */
public class XmlTestUtils {

  public static void assertXmlEquals(
      String expected, String actual, String... ignoredPaths) throws Exception {
    assertXmlEqualsWithMessage(expected, actual, "", ignoredPaths);
  }

  public static void assertXmlEqualsWithMessage(
      String expected, String actual, String message, String... ignoredPaths) throws Exception {
    if (!actual.startsWith("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")) {
      assert_().fail("XML declaration not found at beginning:\n%s", actual);
    }
    Map<String, Object> expectedMap = toComparableJson(expected, ignoredPaths);
    Map<String, Object> actualMap = toComparableJson(actual, ignoredPaths);
    if (!expectedMap.equals(actualMap)) {
      assert_().fail(String.format(
          "%s: Expected:\n%s\n\nActual:\n%s\n\nDiff:\n%s\n\n",
          message,
          expected,
          actual,
          prettyPrintDeepDiff(expectedMap, actualMap, null)));
    }
  }

  /** Deeply explore the object and normalize values so that things we consider equal compare so. */
  private static Object normalize(Object obj, @Nullable String path, Set<String> ignoredPaths)
      throws Exception {
    if (obj instanceof JSONObject) {
      JSONObject jsonObject = (JSONObject) obj;
      Map<String, Object> map = new HashMap<>();
      // getNames helpfully returns null rather than empty when there are no names.
      for (String key : firstNonNull(JSONObject.getNames(jsonObject), new String[0])) {
        // Recursively transform json maps, remove namespaces and the "xmlns" key.
        if (key.startsWith("xmlns")) {
          continue;
        }
        String simpleKey = key.replaceAll(".*:", "");
        String newPath = path == null ? simpleKey : path + "." + simpleKey;
        Object value;
        if (ignoredPaths.contains(newPath)) {
          // Set ignored fields to a value that will compare equal.
          value = "IGNORED";
        } else {
          value = normalize(jsonObject.get(key), newPath, ignoredPaths);
        }
        map.put(simpleKey, value);
      }
      // If a node has both text content and attributes, the text content will end up under a key
      // called "content". If that's the only thing left (which will only happen if there was an
      // "xmlns:*" key that we removed), treat the node as just text and recurse.
      if (map.size() == 1 && map.get("content") != null) {
        return normalize(jsonObject.get("content"), path, ignoredPaths);
      }
      // The conversion to JSON converts <a/> into "" and the semantically equivalent <a></a> into
      // an empty map, so normalize that here.
      return map.isEmpty() ? "" : map;
    }
    if (obj instanceof JSONArray) {
      Set<Object> set = new HashSet<>();
      for (int i = 0; i < ((JSONArray) obj).length(); ++i) {
        set.add(normalize(((JSONArray) obj).get(i), path, ignoredPaths));
      }
      return set;
    }
    if (obj instanceof Number) {
      return obj.toString();
    }
    if (obj instanceof Boolean) {
      return ((Boolean) obj) ? "1" : "0";
    }
    if (obj instanceof String) {
      // Turn stringified booleans into integers. Both are acceptable as xml boolean values, but
      // we use "true" and "false" whereas the samples use "1" and "0".
      if (obj.equals("true")) {
        return "1";
      }
      if (obj.equals("false")) {
        return "0";
      }
      String string = obj.toString();
      // We use a slightly different datetime format (both legal) than the samples, so normalize
      // both into Datetime objects.
      try {
        return ISODateTimeFormat.dateTime().parseDateTime(string).toDateTime(UTC);
      } catch (IllegalArgumentException e) {
        // It wasn't a DateTime.
      }
      try {
        return ISODateTimeFormat.dateTimeNoMillis().parseDateTime(string).toDateTime(UTC);
      } catch (IllegalArgumentException e) {
        // It wasn't a DateTime.
      }
      try {
        if (!InternetDomainName.isValid(string)) {
          // It's not a domain name, but it is an InetAddress. Ergo, it's an ip address.
          return InetAddresses.forString(string);
        }
      } catch (IllegalArgumentException e) {
        // Not an ip address.
      }
      return string;
    }
    return checkNotNull(obj);
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> toComparableJson(
      String xml, String... ignoredPaths) throws Exception {
    return (Map<String, Object>) normalize(
        XML.toJSONObject(xml), null, ImmutableSet.copyOf(ignoredPaths));
  }
}
