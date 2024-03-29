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

package google.registry.tools.params;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.beust.jcommander.ParameterException;
import java.io.File;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermissions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Unit tests for {@link PathParameter}. */
class PathParameterTest {

  @TempDir Path tmpDir;

  // ================================ Test Convert ==============================================

  private final PathParameter vanilla = new PathParameter();

  private static final Boolean isRoot = "root".equals(System.getProperty("user.name"));

  @Test
  void testConvert_etcPasswd_returnsPath() {
    assertThat((Object) vanilla.convert("/etc/passwd")).isEqualTo(Paths.get("/etc/passwd"));
  }

  @Test
  void testConvert_null_throws() {
    assertThrows(NullPointerException.class, () -> vanilla.convert(null));
  }

  @Test
  void testConvert_empty_throws() {
    assertThrows(IllegalArgumentException.class, () -> vanilla.convert(""));
  }

  @Test
  void testConvert_relativePath_returnsOriginalFile() {
    Path currentDirectory = Paths.get("").toAbsolutePath();
    Path file = Paths.get(tmpDir.resolve("tmp.file").toString());
    Path relative = file.relativize(currentDirectory);
    assumeFalse(relative.equals(file));
    assumeTrue(relative.toString().startsWith("../"));
    Path converted = vanilla.convert(file.toString());
    assertThat((Object) converted).isEqualTo(file);
  }

  @Test
  void testConvert_extraSlash_returnsWithoutSlash() {
    Path file = Paths.get(tmpDir.resolve("file.new").toString());
    assertThat((Object) vanilla.convert(file + "/")).isEqualTo(file);
  }

  @Test
  void testConvert_uriNotProvided() {
    assertThrows(FileSystemNotFoundException.class, () -> vanilla.convert("bog://bucket/lolcat"));
  }

  // =========================== Test InputFile Validate ========================================

  private final PathParameter inputFile = new PathParameter.InputFile();

  @Test
  void testInputFileValidate_normalFile_works() throws Exception {
    inputFile.validate("input", Files.createFile(tmpDir.resolve("tmpfile.txt")).toString());
  }

  @Test
  void testInputFileValidate_missingFile_throws() {
    ParameterException thrown =
        assertThrows(
            ParameterException.class,
            () -> inputFile.validate("input", tmpDir.resolve("foo").toString()));
    assertThat(thrown).hasMessageThat().contains("not found");
  }

  @Test
  void testInputFileValidate_directory_throws() {
    ParameterException thrown =
        assertThrows(
            ParameterException.class, () -> inputFile.validate("input", tmpDir.toString()));
    assertThat(thrown).hasMessageThat().contains("is a directory");
  }

  @Test
  void testInputFileValidate_unreadableFile_throws() throws Exception {
    Path file = Files.createFile(tmpDir.resolve("tmpfile.txt"));
    Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("-w-------"));
    // This test doesn't take into account the fact that root user has access to read/write the
    // file even if posix permissions disallow it.
    // For the environmnent that run strictly under root, we will ignore the test results.
    if (!isRoot) {
      ParameterException thrown =
          assertThrows(
              ParameterException.class, () -> inputFile.validate("input", file.toString()));
      assertThat(thrown).hasMessageThat().contains("not readable");
    }
  }

  // =========================== Test OutputFile Validate ========================================

  private final PathParameter outputFile = new PathParameter.OutputFile();

  @Test
  void testOutputFileValidate_normalFile_works() {
    outputFile.validate("input", tmpDir.resolve("testfile").toString());
  }

  @Test
  void testInputFileValidate_characterDeviceBehindSymbolicLinks_works() {
    assumeTrue(Files.exists(Paths.get("/dev/stdin")));
    outputFile.validate("input", "/dev/stdin");
  }

  @Test
  void testOutputFileValidate_missingFile_works() {
    outputFile.validate("input", new File(tmpDir.toFile(), "foo").toString());
  }

  @Test
  void testOutputFileValidate_directory_throws() {
    ParameterException thrown =
        assertThrows(
            ParameterException.class, () -> outputFile.validate("input", tmpDir.toString()));
    assertThat(thrown).hasMessageThat().contains("is a directory");
  }

  @Test
  void testOutputFileValidate_notWritable_throws() throws Exception {
    Path file = Files.createFile(tmpDir.resolve("newFile.dat"));
    Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("r--------"));
    // This test doesn't take into account the fact that root user has access to read/write the
    // file even if posix permissions disallow it.
    // For the environmnent that run strictly under root, we will ignore the test results.
    if (!isRoot) {
      ParameterException thrown =
          assertThrows(
              ParameterException.class, () -> outputFile.validate("input", file.toString()));
      assertThat(thrown).hasMessageThat().contains("not writable");
    }
  }

  @Test
  void testOutputFileValidate_parentDirMissing_throws() {
    Path file = Paths.get(tmpDir.toString(), "MISSINGNO", "foo.txt");
    ParameterException thrown =
        assertThrows(ParameterException.class, () -> outputFile.validate("input", file.toString()));
    assertThat(thrown).hasMessageThat().contains("parent dir doesn't exist");
  }

  @Test
  void testOutputFileValidate_parentDirIsFile_throws() throws Exception {
    Path file = Paths.get(Files.createFile(tmpDir.resolve("foo.file")).toString(), "foo.txt");
    ParameterException thrown =
        assertThrows(ParameterException.class, () -> outputFile.validate("input", file.toString()));
    assertThat(thrown).hasMessageThat().contains("parent is non-directory");
  }
}
