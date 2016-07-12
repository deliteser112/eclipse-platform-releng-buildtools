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

import com.google.common.util.concurrent.ListenableFuture;
import google.registry.bigquery.BigqueryConnection.DestinationTable;
import java.util.concurrent.ExecutionException;

/** Container class for static utility methods for Bigquery commands. */
final class BigqueryCommandUtilities {

  /**
   * Handler that takes a DestinationTable future and waits on its completion, printing generic
   * success/failure messages and wrapping any exception thrown in a TableCreationException.
   */
  static void handleTableCreation(
      String tableDescription,
      ListenableFuture<DestinationTable> tableFuture) throws TableCreationException {
    System.err.printf("Creating %s...\n", tableDescription);
    try {
      DestinationTable table = tableFuture.get();
      System.err.printf(" - Success: created %s.\n", table.getStringReference());
    } catch (Exception e) {
      Throwable error = e;
      if (e instanceof ExecutionException) {
        error = e.getCause();
      }
      String errorMessage =
          String.format("Failed to create %s: %s", tableDescription, error.getMessage());
      System.err.printf(" - %s\n", errorMessage);
      throw new TableCreationException(errorMessage, error);
    }
  }

  /** Exception thrown if any error occurs during a table creation stage. */
  static class TableCreationException extends Exception {
    TableCreationException(String message, Throwable cause) {
      super(message, cause);
    }
  }
}
