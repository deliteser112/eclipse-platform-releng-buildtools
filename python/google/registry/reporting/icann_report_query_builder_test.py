
# Copyright 2016 The Domain Registry Authors. All Rights Reserved.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

"""Tests for google.registry.reporting.icann_report_query_builder."""

import os
import unittest

from google.registry.reporting import icann_report_query_builder


class IcannReportQueryBuilderTest(unittest.TestCase):

  testdata_path = None

  def setUp(self):
    # Using __file__ is a bit of a hack, but it's the only way that "just works"
    # for internal and external versions of the code, and it's fine for tests.
    self.testdata_path = os.path.join(os.path.dirname(__file__), 'testdata')

  def testActivityQuery_matchesGoldenQuery(self):
    self.maxDiff = None  # Show long diffs
    query_builder = icann_report_query_builder.IcannReportQueryBuilder()
    golden_activity_query_path = os.path.join(self.testdata_path,
                                              'golden_activity_query.sql')
    with open(golden_activity_query_path, 'r') as golden_activity_query:
      self.assertMultiLineEqual(golden_activity_query.read(),
                                query_builder.BuildActivityReportQuery(
                                    month='2016-06',
                                    registrar_count=None))

  def testStringTrailingWhitespaceFromLines(self):
    def do_test(expected, original):
      self.assertEqual(
          expected,
          icann_report_query_builder._StripTrailingWhitespaceFromLines(
              original))
    do_test('foo\nbar\nbaz\n', 'foo\nbar\nbaz\n')
    do_test('foo\nbar\nbaz\n', 'foo   \nbar   \nbaz   \n')
    do_test('foo\nbar\nbaz', 'foo   \nbar   \nbaz   ')
    do_test('\nfoo\nbar\nbaz', '\nfoo\nbar\nbaz')
    do_test('foo\n\n', 'foo\n   \n')
    do_test('foo\n', 'foo\n   ')


if __name__ == '__main__':
  unittest.main()
