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
