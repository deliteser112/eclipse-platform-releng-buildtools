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

package google.registry.rde.imports;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;

import google.registry.model.eppcommon.Trid;

/**
 * Collection of test utility logic for RDE import.
 */
public class RdeImportTestUtils {

  /**
   * Verifies that the {@link Trid} <code>clientTransactionId</code> meets system requirements.
   *
   * <ol>
   *  <li><code>clientTransactionId</code> cannot be null
   *  <li><code>clientTransactionId</code> must be a token between 3 and 64 characters long
   *  <li><code>clientTransactionId</code> must be prefixed with <code>Import__</code>
   * </ol>
   */
  public static void checkTrid(Trid trid) {
    assertThat(trid).isNotNull();
    assertThat(trid.getClientTransactionId()).isPresent();
    String clTrid = trid.getClientTransactionId().get();
    assertThat(clTrid.length()).isAtLeast(3);
    assertThat(clTrid.length()).isAtMost(64);
    assertThat(clTrid).startsWith("Import_");
  }
}
