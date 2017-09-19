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

"""Convert App Engine Java datastore-indexes.xml file to index.yaml format.

Pass the name of a datastore-indexes.xml file. The output will be dumped to
stdout.

The resulting file can be used to call the gcloud cleanup-indexes command,
which interactively removes indexes which are defined on an App Engine
project but not present in the file. The syntax for that command is:

    gcloud datastore cleanup-indexes {index.yaml file} --project={project-id}
"""

import sys
from xml.etree import ElementTree


def main(argv):
  if len(argv) < 2:
    print 'Usage: command {datastore-indexes.xml file}'
    return 1

  root = ElementTree.parse(argv[1]).getroot()
  print 'indexes:'
  for index in root:
    print ''
    print '- kind: %s' % index.attrib['kind']
    if index.attrib['ancestor'] != 'false':
      print '  ancestor: %s' % ('yes' if (index.attrib['ancestor'] == 'true')
                                else 'no')
    print '  properties:'
    for index_property in index:
      print '  - name: %s' % index_property.attrib['name']
      if index_property.attrib['direction'] != 'asc':
        print '    direction: %s' % index_property.attrib['direction']

if __name__ == '__main__':
  main(sys.argv)
