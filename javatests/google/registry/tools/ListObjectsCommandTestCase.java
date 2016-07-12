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

import static google.registry.request.JsonResponse.JSON_SAFETY_PREFIX;
import static google.registry.testing.DatastoreHelper.createTld;
import static google.registry.tools.server.ListObjectsAction.FIELDS_PARAM;
import static google.registry.tools.server.ListObjectsAction.FULL_FIELD_NAMES_PARAM;
import static google.registry.tools.server.ListObjectsAction.PRINT_HEADER_ROW_PARAM;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyMapOf;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableMap;
import com.google.common.net.MediaType;
import google.registry.tools.ServerSideCommand.Connection;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

/** Abstract base class for unit tests of commands that list object data using a back-end task. */
public abstract class ListObjectsCommandTestCase<C extends ListObjectsCommand>
    extends CommandTestCase<C> {

  @Mock
  Connection connection;

  /**
   * Where to find the servlet task; set by the subclass.
   */
  abstract String getTaskPath();

  /**
   * The TLD to be used (for those subclasses that use TLDs; may be null).
   */
  abstract String getTld();

  /**
   * The TLD argument to be passed on the command line; null if not needed.
   */
  String tldArgumentString;

  @Before
  public void init() throws Exception {
    String tld = getTld();
    if (tld == null) {
      tldArgumentString = null;
    } else {
      createTld(tld);
      tldArgumentString = "--tld=" + tld;
    }
    command.setConnection(connection);
    when(
        connection.send(
            eq(getTaskPath()),
            anyMapOf(String.class, Object.class),
            eq(MediaType.PLAIN_TEXT_UTF_8),
            any(byte[].class)))
        .thenReturn(JSON_SAFETY_PREFIX + "{\"status\":\"success\",\"lines\":[]}");
  }

  private void verifySent(
      String fields,
      Optional<Boolean> printHeaderRow,
      Optional<Boolean> fullFieldNames) throws Exception {

    ImmutableMap.Builder<String, Object> params = ImmutableMap.<String, Object>builder();
    if (fields != null) {
      params.put(FIELDS_PARAM, fields);
    }
    if (printHeaderRow.isPresent()) {
      params.put(PRINT_HEADER_ROW_PARAM, printHeaderRow.get());
    }
    if (fullFieldNames.isPresent()) {
      params.put(FULL_FIELD_NAMES_PARAM, fullFieldNames.get());
    }
    String tld = getTld();
    if (tld != null) {
      params.put("tld", tld);
    }
    verify(connection).send(
        eq(getTaskPath()),
        eq(params.build()),
        eq(MediaType.PLAIN_TEXT_UTF_8),
        eq(new byte[0]));
  }

  @Test
  public void testRun_noFields() throws Exception {
    if (tldArgumentString == null) {
      runCommand();
    } else {
      runCommand(tldArgumentString);
    }
    verifySent(null, Optional.<Boolean>absent(), Optional.<Boolean>absent());
  }

  @Test
  public void testRun_oneField() throws Exception {
    if (tldArgumentString == null) {
      runCommand("--fields=fieldName");
    } else {
      runCommand("--fields=fieldName", tldArgumentString);
    }
    verifySent("fieldName", Optional.<Boolean>absent(), Optional.<Boolean>absent());
  }

  @Test
  public void testRun_wildcardField() throws Exception {
    if (tldArgumentString == null) {
      runCommand("--fields=*");
    } else {
      runCommand("--fields=*", tldArgumentString);
    }
    verifySent("*", Optional.<Boolean>absent(), Optional.<Boolean>absent());
  }

  @Test
  public void testRun_header() throws Exception {
    if (tldArgumentString == null) {
      runCommand("--fields=fieldName", "--header=true");
    } else {
      runCommand("--fields=fieldName", "--header=true", tldArgumentString);
    }
    verifySent("fieldName", Optional.of(Boolean.TRUE), Optional.<Boolean>absent());
  }

  @Test
  public void testRun_noHeader() throws Exception {
    if (tldArgumentString == null) {
      runCommand("--fields=fieldName", "--header=false");
    } else {
      runCommand("--fields=fieldName", "--header=false", tldArgumentString);
    }
    verifySent("fieldName", Optional.of(Boolean.FALSE), Optional.<Boolean>absent());
  }

  @Test
  public void testRun_fullFieldNames() throws Exception {
    if (tldArgumentString == null) {
      runCommand("--fields=fieldName", "--full_field_names");
    } else {
      runCommand("--fields=fieldName", "--full_field_names", tldArgumentString);
    }
    verifySent("fieldName", Optional.<Boolean>absent(), Optional.of(Boolean.TRUE));
  }

  @Test
  public void testRun_allParameters() throws Exception {
    if (tldArgumentString == null) {
      runCommand("--fields=fieldName,otherFieldName,*", "--header=true", "--full_field_names");
    } else {
      runCommand(
          "--fields=fieldName,otherFieldName,*",
          "--header=true",
          "--full_field_names",
          tldArgumentString);
    }
    verifySent(
        "fieldName,otherFieldName,*", Optional.of(Boolean.TRUE), Optional.of(Boolean.TRUE));
  }
}
