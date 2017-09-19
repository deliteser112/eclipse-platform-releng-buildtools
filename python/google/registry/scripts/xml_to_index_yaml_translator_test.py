# Copyright 2017 The Nomulus Authors. All Rights Reserved.
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

"""Tests for xml_to_index_yaml_translator.py."""

import contextlib
import StringIO
import sys
import unittest
from google.registry.scripts import xml_to_index_yaml_translator


@contextlib.contextmanager
def _RedirectStdout():
  orig_stdout = sys.stdout
  sio = StringIO.StringIO()
  sys.stdout = sio
  try:
    yield sio
  finally:
    sys.stdout = orig_stdout


class XmlToIndexYamlTranslatorTest(unittest.TestCase):

  def testSuccess(self):
    with _RedirectStdout() as sio:
      xml_to_index_yaml_translator.main(['test',
                                         'python/'
                                         'google/registry/scripts/testdata/'
                                         'datastore-indexes.xml'])
    actual = sio.getvalue()
    expectedfile = open('python/google/registry/'
                        'scripts/testdata/index.yaml')
    expected = expectedfile.read()
    self.assertEqual(actual, expected)


if __name__ == '__main__':
  unittest.main()
