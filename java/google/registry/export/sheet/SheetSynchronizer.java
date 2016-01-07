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

package google.registry.export.sheet;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.gdata.client.spreadsheet.SpreadsheetService;
import com.google.gdata.data.spreadsheet.CustomElementCollection;
import com.google.gdata.data.spreadsheet.ListEntry;
import com.google.gdata.data.spreadsheet.ListFeed;
import com.google.gdata.data.spreadsheet.SpreadsheetEntry;
import com.google.gdata.data.spreadsheet.WorksheetEntry;
import com.google.gdata.util.ServiceException;
import java.io.IOException;
import java.net.URL;
import java.util.List;
import javax.inject.Inject;

/** Generic data synchronization utility for Google Spreadsheets. */
class SheetSynchronizer {

  private static final String SPREADSHEET_URL_PREFIX =
      "https://spreadsheets.google.com/feeds/spreadsheets/";

  @Inject SpreadsheetService spreadsheetService;
  @Inject SheetSynchronizer() {}

  /**
   * Replace the contents of a Google Spreadsheet with {@code data}.
   *
   * <p>In order for this to work, you must create a spreadsheet with a header row, each containing
   * the column name, without any spaces. All subsequent rows are considered data, so long as
   * they're not blank. If you have a blank row in the middle of your data, you're going to have
   * problems. You must also make sure that the spreadsheet has been shared with the API client
   * credential email address.
   *
   * <p>The algorithm works by first assuming that the spreadsheet is sorted in the same way that
   * {@code data} is sorted. It then iterates through the existing rows and comparing them to the
   * items in {@code data}. Iteration continues until we either run out of rows, or items in
   * {@code data}. If there's any rows remaining, they'll be deleted. If instead, items remain in
   * data, they'll be inserted.
   *
   * @param spreadsheetId The ID of your spreadsheet. This can be obtained by opening the Google
   *     spreadsheet in your browser and copying the ID from the URL.
   * @param data This should be a <i>sorted</i> list of rows containing the enterity of the
   *     spreadsheet. Each row is a map, where the key must be exactly the same as the column header
   *     cell in the spreadsheet, and value is an arbitrary object which will be converted to a
   *     string before storing it in the spreadsheet.
   * @throws IOException error communicating with the GData service.
   * @throws ServiceException if a system error occurred when retrieving the entry.
   * @throws com.google.gdata.util.ParseException error parsing the returned entry.
   * @throws com.google.gdata.util.ResourceNotFoundException if an entry URL is not valid.
   * @throws com.google.gdata.util.ServiceForbiddenException if the GData service cannot get the
   *     entry resource due to access constraints.
   * @see "https://developers.google.com/google-apps/spreadsheets/"
   */
  void synchronize(String spreadsheetId, ImmutableList<ImmutableMap<String, String>> data)
      throws IOException, ServiceException {
    URL url = new URL(SPREADSHEET_URL_PREFIX + spreadsheetId);
    SpreadsheetEntry spreadsheet = spreadsheetService.getEntry(url, SpreadsheetEntry.class);
    WorksheetEntry worksheet = spreadsheet.getWorksheets().get(0);
    worksheet.setRowCount(data.size());
    worksheet = worksheet.update();
    ListFeed listFeed = spreadsheetService.getFeed(worksheet.getListFeedUrl(), ListFeed.class);
    List<ListEntry> entries = listFeed.getEntries();
    int commonSize = Math.min(entries.size(), data.size());
    for (int i = 0; i < commonSize; i++) {
      ListEntry entry = entries.get(i);
      CustomElementCollection elements = entry.getCustomElements();
      boolean mutated = false;
      for (ImmutableMap.Entry<String, String> cell : data.get(i).entrySet()) {
        if (!cell.getValue().equals(elements.getValue(cell.getKey()))) {
          mutated = true;
          elements.setValueLocal(cell.getKey(), cell.getValue());
        }
      }
      if (mutated) {
        entry.update();
      }
    }
    if (data.size() > entries.size()) {
      for (int i = entries.size(); i < data.size(); i++) {
        ListEntry entry = listFeed.createEntry();
        CustomElementCollection elements = entry.getCustomElements();
        for (ImmutableMap.Entry<String, String> cell : data.get(i).entrySet()) {
          elements.setValueLocal(cell.getKey(), cell.getValue());
        }
        listFeed.insert(entry);
      }
    }
  }
}
