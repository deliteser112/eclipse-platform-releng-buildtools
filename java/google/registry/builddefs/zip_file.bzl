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



load('//java/google/registry/builddefs:defs.bzl', 'ZIPPER', 'runpath')

def _impl(ctx):
  """Implementation of zip_file() rule."""
  for s, d in ctx.attr.mappings.items():
    if (s.startswith('/') or s.endswith('/') or
        d.startswith('/') or d.endswith('/')):
      fail('mappings should not begin or end with slash')
  mapped = _map_sources(ctx.files.srcs, ctx.attr.mappings)
  cmd = [
      'set -e',
      'repo="$(pwd)"',
      'zipper="${repo}/%s"' % ctx.file._zipper.path,
      'archive="${repo}/%s"' % ctx.outputs.out.path,
      'tmp="$(mktemp -d "${TMPDIR:-/tmp}/zip_file.XXXXXXXXXX")"',
      'cd "${tmp}"',
  ]
  cmd += ['"${zipper}" x "${repo}/%s"' % dep.zip_file.path
          for dep in ctx.attr.deps]
  cmd += ['mkdir -p "${tmp}/%s"' % zip_path
          for zip_path in set(
              [zip_path[:zip_path.rindex('/')]
               for _, zip_path in mapped if '/' in zip_path])]
  cmd += ['ln -sf "${repo}/%s" "${tmp}/%s"' % (path, zip_path)
          for path, zip_path in mapped]
  cmd += [
      ('find . | sed 1d | cut -c 3- | LC_ALL=C sort' +
       ' | xargs "${zipper}" cC "${archive}"'),
      'cd "${repo}"',
      'rm -rf "${tmp}"',
  ]
  inputs = [ctx.file._zipper]
  inputs += [dep.zip_file for dep in ctx.attr.deps]
  inputs += ctx.files.srcs
  ctx.action(
      inputs=inputs,
      outputs=[ctx.outputs.out],
      command='\n'.join(cmd),
      mnemonic='zip',
      progress_message='Creating zip with %d inputs %s' % (
          len(inputs), ctx.label))
  return struct(files=set([ctx.outputs.out]),
                zip_file=ctx.outputs.out)

def _map_sources(srcs, mappings):
  """Calculates paths in zip file for srcs."""
  # order mappings with more path components first
  mappings = sorted([(-len(source.split('/')), source, dest)
                     for source, dest in mappings.items()])
  # get rid of the integer part of tuple used for sorting
  mappings = [(source, dest) for _, source, dest in mappings]
  mappings_indexes = range(len(mappings))
  used = {i: False for i in mappings_indexes}
  mapped = []
  for file_ in srcs:
    short_path = runpath(file_)
    zip_path = None
    for i in mappings_indexes:
      source = mappings[i][0]
      dest = mappings[i][1]
      if not source:
        if dest:
          zip_path = dest + '/' + short_path
        else:
          zip_path = short_path
      elif source == short_path:
        if dest:
          zip_path = dest
        else:
          zip_path = short_path
      elif short_path.startswith(source + '/'):
        if dest:
          zip_path = dest + short_path[len(source):]
        else:
          zip_path = short_path[len(source) + 1:]
      else:
        continue
      used[i] = True
      break
    if not zip_path:
      fail('no mapping matched: ' + short_path)
    mapped += [(file_.path, zip_path)]
  for i in mappings_indexes:
    if not used[i]:
      fail('superfluous mapping: "%s" -> "%s"' % mappings[i])
  return mapped

zip_file = rule(
    implementation=_impl,
    output_to_genfiles = True,
    attrs={
        'out': attr.output(mandatory=True),
        'srcs': attr.label_list(allow_files=True),
        'deps': attr.label_list(providers=['zip_file']),
        'mappings': attr.string_dict(),
        '_zipper': attr.label(default=Label(ZIPPER), single_file=True),
    })
