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

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.io.IOException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link ActivityReportingQueryBuilder}. */
@RunWith(JUnit4.class)
public class TransactionsReportingQueryBuilderTest {

  private TransactionsReportingQueryBuilder getQueryBuilder() {
    TransactionsReportingQueryBuilder queryBuilder = new TransactionsReportingQueryBuilder();
    queryBuilder.yearMonth = "2017-06";
    queryBuilder.projectId = "domain-registry-alpha";
    return queryBuilder;
  }

  @Test
  public void testAggregateQueryMatch() throws IOException {
    TransactionsReportingQueryBuilder queryBuilder = getQueryBuilder();
    assertThat(queryBuilder.getReportQuery())
        .isEqualTo(
            "#standardSQL\nSELECT * FROM "
                + "`domain-registry-alpha.icann_reporting.transactions_report_aggregation_201706`");
  }

  @Test
  public void testIntermediaryQueryMatch() throws IOException {
    TransactionsReportingQueryBuilder queryBuilder = getQueryBuilder();
    ImmutableList<String> queryNames =
        ImmutableList.of(
            TransactionsReportingQueryBuilder.TRANSACTIONS_REPORT_AGGREGATION,
            TransactionsReportingQueryBuilder.REGISTRAR_IANA_ID,
            TransactionsReportingQueryBuilder.TOTAL_DOMAINS,
            TransactionsReportingQueryBuilder.TOTAL_NAMESERVERS,
            TransactionsReportingQueryBuilder.TRANSACTION_COUNTS,
            TransactionsReportingQueryBuilder.TRANSACTION_TRANSFER_LOSING,
            TransactionsReportingQueryBuilder.ATTEMPTED_ADDS);

    ImmutableMap<String, String> actualQueries = queryBuilder.getViewQueryMap();
    for (String queryName : queryNames) {
      String actualTableName = String.format("%s_201706", queryName);
      String testFilename = String.format("%s_test.sql", queryName);
      assertThat(actualQueries.get(actualTableName))
          .isEqualTo(ReportingTestData.getString(testFilename));
    }
  }
}


