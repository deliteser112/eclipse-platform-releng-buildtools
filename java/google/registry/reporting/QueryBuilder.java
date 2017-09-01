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

package google.registry.reporting;

import com.google.common.collect.ImmutableMap;
import java.io.IOException;

/** Interface defining the necessary methods to construct ICANN reporting SQL queries. */
public interface QueryBuilder {

  /** Returns a map from an intermediary view's table name to the query that generates it. */
  ImmutableMap<String, String> getViewQueryMap() throws IOException;

  /** Returns a query that retrieves the overall report from the previously generated view. */
  String getReportQuery() throws IOException;
}
