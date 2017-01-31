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

package google.registry.export.sheet;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.gdata.client.spreadsheet.SpreadsheetService;
import com.google.gdata.data.spreadsheet.CustomElementCollection;
import com.google.gdata.data.spreadsheet.ListEntry;
import com.google.gdata.data.spreadsheet.ListFeed;
import com.google.gdata.data.spreadsheet.SpreadsheetEntry;
import com.google.gdata.data.spreadsheet.WorksheetEntry;
import google.registry.testing.FakeClock;
import google.registry.testing.FakeSleeper;
import google.registry.util.Retrier;
import java.net.URL;
import org.joda.time.DateTime;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link SheetSynchronizer}. */
@RunWith(JUnit4.class)
public class SheetSynchronizerTest {

  private static final int MAX_RETRIES = 3;

  private final SpreadsheetService spreadsheetService = mock(SpreadsheetService.class);
  private final SpreadsheetEntry spreadsheet = mock(SpreadsheetEntry.class);
  private final WorksheetEntry worksheet = mock(WorksheetEntry.class);
  private final ListFeed listFeed = mock(ListFeed.class);
  private final SheetSynchronizer sheetSynchronizer = new SheetSynchronizer();
  private final FakeSleeper sleeper =
      new FakeSleeper(new FakeClock(DateTime.parse("2000-01-01TZ")));

  @Before
  public void before() throws Exception {
    sheetSynchronizer.spreadsheetService = spreadsheetService;
    sheetSynchronizer.retrier = new Retrier(sleeper, MAX_RETRIES);
    when(spreadsheetService.getEntry(any(URL.class), eq(SpreadsheetEntry.class)))
        .thenReturn(spreadsheet);
    when(spreadsheet.getWorksheets()).thenReturn(ImmutableList.of(worksheet));
    when(worksheet.getListFeedUrl()).thenReturn(new URL("http://example.com/spreadsheet"));
    when(spreadsheetService.getFeed(any(URL.class), eq(ListFeed.class))).thenReturn(listFeed);
    when(worksheet.update()).thenReturn(worksheet);
  }

  @After
  public void after() throws Exception {
    verify(spreadsheetService)
        .getEntry(
            new URL("https://spreadsheets.google.com/feeds/spreadsheets/foobar"),
            SpreadsheetEntry.class);
    verify(spreadsheet).getWorksheets();
    verify(worksheet).getListFeedUrl();
    verify(spreadsheetService).getFeed(new URL("http://example.com/spreadsheet"), ListFeed.class);
    verify(listFeed).getEntries();
    verifyNoMoreInteractions(spreadsheetService, spreadsheet, worksheet, listFeed);
  }

  @Test
  public void testSynchronize_bothEmpty_doNothing() throws Exception {
    when(listFeed.getEntries()).thenReturn(ImmutableList.<ListEntry>of());
    sheetSynchronizer.synchronize("foobar", ImmutableList.<ImmutableMap<String, String>>of());
    verify(worksheet).setRowCount(1);
    verify(worksheet).update();
  }

  @Test
  public void testSynchronize_bothContainSameRow_doNothing() throws Exception {
    ListEntry entry = makeListEntry(ImmutableMap.of("key", "value"));
    when(listFeed.getEntries()).thenReturn(ImmutableList.of(entry));
    sheetSynchronizer.synchronize("foobar", ImmutableList.of(ImmutableMap.of("key", "value")));
    verify(worksheet).setRowCount(2);
    verify(worksheet).update();
    verify(entry, atLeastOnce()).getCustomElements();
    verifyNoMoreInteractions(entry);
  }

  @Test
  public void testSynchronize_cellIsDifferent_updateRow() throws Exception {
    ListEntry entry = makeListEntry(ImmutableMap.of("key", "value"));
    when(listFeed.getEntries()).thenReturn(ImmutableList.of(entry));
    sheetSynchronizer.synchronize("foobar", ImmutableList.of(ImmutableMap.of("key", "new value")));
    verify(entry.getCustomElements()).setValueLocal("key", "new value");
    verify(entry).update();
    verify(worksheet).setRowCount(2);
    verify(worksheet).update();
    verify(entry, atLeastOnce()).getCustomElements();
    verifyNoMoreInteractions(entry);
  }

  @Test
  public void testSynchronize_cellIsDifferent_updateRow_retriesOnException() throws Exception {
    ListEntry entry = makeListEntry(ImmutableMap.of("key", "value"));
    when(listFeed.getEntries()).thenReturn(ImmutableList.of(entry));
    when(entry.update())
        .thenThrow(new RuntimeException())
        .thenThrow(new RuntimeException())
        .thenReturn(entry);
    sheetSynchronizer.synchronize("foobar", ImmutableList.of(ImmutableMap.of("key", "new value")));
    verify(entry.getCustomElements()).setValueLocal("key", "new value");
    verify(entry, times(3)).update();
    verify(worksheet).setRowCount(2);
    verify(worksheet).update();
    verify(entry, atLeastOnce()).getCustomElements();
    verifyNoMoreInteractions(entry);
  }

  @Test
  public void testSynchronize_spreadsheetMissingRow_insertRow() throws Exception {
    ListEntry entry = makeListEntry(ImmutableMap.<String, String>of());
    when(listFeed.getEntries()).thenReturn(ImmutableList.<ListEntry>of());
    when(listFeed.createEntry()).thenReturn(entry);
    sheetSynchronizer.synchronize("foobar", ImmutableList.of(ImmutableMap.of("key", "value")));
    verify(entry.getCustomElements()).setValueLocal("key", "value");
    verify(listFeed).insert(entry);
    verify(worksheet).setRowCount(2);
    verify(worksheet).update();
    verify(listFeed).createEntry();
    verify(entry, atLeastOnce()).getCustomElements();
    verifyNoMoreInteractions(entry);
  }

  @Test
  public void testSynchronize_spreadsheetMissingRow_insertRow_retriesOnException()
      throws Exception {
    ListEntry entry = makeListEntry(ImmutableMap.<String, String>of());
    when(listFeed.getEntries()).thenReturn(ImmutableList.<ListEntry>of());
    when(listFeed.createEntry()).thenReturn(entry);
    when(listFeed.insert(entry))
        .thenThrow(new RuntimeException())
        .thenThrow(new RuntimeException())
        .thenReturn(entry);
    sheetSynchronizer.synchronize("foobar", ImmutableList.of(ImmutableMap.of("key", "value")));
    verify(entry.getCustomElements()).setValueLocal("key", "value");
    verify(listFeed, times(3)).insert(entry);
    verify(worksheet).setRowCount(2);
    verify(worksheet).update();
    verify(listFeed).createEntry();
    verify(entry, atLeastOnce()).getCustomElements();
    verifyNoMoreInteractions(entry);
  }

  @Test
  public void testSynchronize_spreadsheetRowNoLongerInData_deleteRow() throws Exception {
    ListEntry entry = makeListEntry(ImmutableMap.of("key", "value"));
    when(listFeed.getEntries()).thenReturn(ImmutableList.of(entry));
    sheetSynchronizer.synchronize("foobar", ImmutableList.<ImmutableMap<String, String>>of());
    verify(worksheet).setRowCount(1);
    verify(worksheet).update();
    verifyNoMoreInteractions(entry);
  }

  private static ListEntry makeListEntry(ImmutableMap<String, String> values) {
    CustomElementCollection collection = mock(CustomElementCollection.class);
    for (ImmutableMap.Entry<String, String> entry : values.entrySet()) {
      when(collection.getValue(eq(entry.getKey()))).thenReturn(entry.getValue());
    }
    ListEntry listEntry = mock(ListEntry.class);
    when(listEntry.getCustomElements()).thenReturn(collection);
    return listEntry;
  }
}
