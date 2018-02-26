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

package google.registry.export;

import static com.google.common.truth.Truth.assertThat;
import static google.registry.export.PublishDetailReportAction.DETAIL_REPORT_NAME_PARAM;
import static google.registry.export.PublishDetailReportAction.GCS_BUCKET_PARAM;
import static google.registry.export.PublishDetailReportAction.GCS_FOLDER_PREFIX_PARAM;
import static google.registry.export.PublishDetailReportAction.REGISTRAR_ID_PARAM;
import static google.registry.testing.DatastoreHelper.loadRegistrar;
import static google.registry.testing.DatastoreHelper.persistResource;
import static google.registry.testing.JUnitBackports.assertThrows;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.appengine.tools.cloudstorage.GcsFileOptions;
import com.google.appengine.tools.cloudstorage.GcsFilename;
import com.google.appengine.tools.cloudstorage.GcsService;
import com.google.appengine.tools.cloudstorage.GcsServiceFactory;
import com.google.common.collect.ImmutableMap;
import com.google.common.net.MediaType;
import google.registry.gcs.GcsUtils;
import google.registry.request.HttpException.BadRequestException;
import google.registry.request.HttpException.InternalServerErrorException;
import google.registry.storage.drive.DriveConnection;
import google.registry.testing.AppEngineRule;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Map;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link PublishDetailReportAction}. */
@RunWith(JUnit4.class)
public class PublishDetailReportActionTest {
  @Rule
  public final AppEngineRule appEngine = AppEngineRule.builder()
      .withDatastore()
      .build();

  private final DriveConnection driveConnection = mock(DriveConnection.class);

  private final PublishDetailReportAction action = new PublishDetailReportAction();
  private final GcsService gcsService = GcsServiceFactory.createGcsService();
  private final GcsUtils gcsUtils = new GcsUtils(gcsService, 1024);

  @Before
  public void setUp() throws Exception {
    action.driveConnection = driveConnection;
    action.gcsUtils = gcsUtils;

    when(driveConnection.createFile(
        anyString(), any(MediaType.class), anyString(), any(byte[].class)))
            .thenReturn("drive-id-123");

    persistResource(loadRegistrar("TheRegistrar").asBuilder().setDriveFolderId("0B-12345").build());

    // Persist an empty GCS file to the local GCS service so that failure tests won't fail
    // prematurely on the file not existing.
    gcsService.createOrReplace(
        new GcsFilename("mah-buckit", "some/folder/detail_report.csv"),
        GcsFileOptions.getDefaultInstance(),
        ByteBuffer.allocate(0));
  }

  @Test
  public void testSuccess() throws Exception {
    // Create a dummy file in the local GCS service to read in the servlet.
    gcsService.createOrReplace(
        new GcsFilename("mah-buckit", "some/folder/detail_report.csv"),
        GcsFileOptions.getDefaultInstance(),
        ByteBuffer.wrap("one,two,three\n".getBytes(UTF_8)));

    Map<String, Object> response =
        action.handleJsonRequest(ImmutableMap.of(
            REGISTRAR_ID_PARAM, "TheRegistrar",
            GCS_BUCKET_PARAM, "mah-buckit",
            GCS_FOLDER_PREFIX_PARAM, "some/folder/",
            DETAIL_REPORT_NAME_PARAM, "detail_report.csv"));

    verify(driveConnection).createFile(
        "detail_report.csv", MediaType.CSV_UTF_8, "0B-12345", "one,two,three\n".getBytes(UTF_8));
    assertThat(response).containsEntry("driveId", "drive-id-123");
  }

  @Test
  public void testFailure_noRegistrarParameter() throws Exception {
    BadRequestException thrown =
        assertThrows(
            BadRequestException.class,
            () ->
                action.handleJsonRequest(
                    ImmutableMap.of(
                        GCS_BUCKET_PARAM, "mah-buckit",
                        GCS_FOLDER_PREFIX_PARAM, "some/folder/",
                        DETAIL_REPORT_NAME_PARAM, "detail_report.csv")));
    assertThat(thrown).hasMessageThat().contains(REGISTRAR_ID_PARAM);
  }

  @Test
  public void testFailure_noGcsBucketParameter() throws Exception {
    BadRequestException thrown =
        assertThrows(
            BadRequestException.class,
            () ->
                action.handleJsonRequest(
                    ImmutableMap.of(
                        REGISTRAR_ID_PARAM, "TheRegistrar",
                        GCS_FOLDER_PREFIX_PARAM, "some/folder/",
                        DETAIL_REPORT_NAME_PARAM, "detail_report.csv")));
    assertThat(thrown).hasMessageThat().contains(GCS_BUCKET_PARAM);
  }

  @Test
  public void testFailure_noGcsFolderPrefixParameter() throws Exception {
    BadRequestException thrown =
        assertThrows(
            BadRequestException.class,
            () ->
                action.handleJsonRequest(
                    ImmutableMap.of(
                        REGISTRAR_ID_PARAM, "TheRegistrar",
                        GCS_BUCKET_PARAM, "mah-buckit",
                        DETAIL_REPORT_NAME_PARAM, "detail_report.csv")));
    assertThat(thrown).hasMessageThat().contains(GCS_FOLDER_PREFIX_PARAM);
  }

  @Test
  public void testFailure_noReportNameParameter() throws Exception {
    BadRequestException thrown =
        assertThrows(
            BadRequestException.class,
            () ->
                action.handleJsonRequest(
                    ImmutableMap.of(
                        REGISTRAR_ID_PARAM, "TheRegistrar",
                        GCS_BUCKET_PARAM, "mah-buckit",
                        GCS_FOLDER_PREFIX_PARAM, "some/folder/")));
    assertThat(thrown).hasMessageThat().contains(DETAIL_REPORT_NAME_PARAM);
  }

  @Test
  public void testFailure_registrarNotFound() throws Exception {
    BadRequestException thrown =
        assertThrows(
            BadRequestException.class,
            () ->
                action.handleJsonRequest(
                    ImmutableMap.of(
                        REGISTRAR_ID_PARAM, "FakeRegistrar",
                        GCS_BUCKET_PARAM, "mah-buckit",
                        GCS_FOLDER_PREFIX_PARAM, "some/folder/",
                        DETAIL_REPORT_NAME_PARAM, "detail_report.csv")));
    assertThat(thrown).hasMessageThat().contains("FakeRegistrar");
  }

  @Test
  public void testFailure_registrarHasNoDriveFolder() throws Exception {
    persistResource(
        loadRegistrar("TheRegistrar").asBuilder().setDriveFolderId(null).build());
    BadRequestException thrown =
        assertThrows(
            BadRequestException.class,
            () ->
                action.handleJsonRequest(
                    ImmutableMap.of(
                        REGISTRAR_ID_PARAM, "TheRegistrar",
                        GCS_BUCKET_PARAM, "mah-buckit",
                        GCS_FOLDER_PREFIX_PARAM, "some/folder/",
                        DETAIL_REPORT_NAME_PARAM, "detail_report.csv")));
    assertThat(thrown).hasMessageThat().contains("drive folder");
  }

  @Test
  public void testFailure_gcsBucketNotFound() throws Exception {
    BadRequestException thrown =
        assertThrows(
            BadRequestException.class,
            () ->
                action.handleJsonRequest(
                    ImmutableMap.of(
                        REGISTRAR_ID_PARAM, "TheRegistrar",
                        GCS_BUCKET_PARAM, "fake-buckit",
                        GCS_FOLDER_PREFIX_PARAM, "some/folder/",
                        DETAIL_REPORT_NAME_PARAM, "detail_report.csv")));
    assertThat(thrown).hasMessageThat().contains("fake-buckit");
  }

  @Test
  public void testFailure_gcsFileNotFound() throws Exception {
    BadRequestException thrown =
        assertThrows(
            BadRequestException.class,
            () ->
                action.handleJsonRequest(
                    ImmutableMap.of(
                        REGISTRAR_ID_PARAM, "TheRegistrar",
                        GCS_BUCKET_PARAM, "mah-buckit",
                        GCS_FOLDER_PREFIX_PARAM, "some/folder/",
                        DETAIL_REPORT_NAME_PARAM, "fake_file.csv")));
    assertThat(thrown).hasMessageThat().contains("some/folder/fake_file.csv");
  }

  @Test
  public void testFailure_driveApiThrowsException() throws Exception {
    when(driveConnection.createFile(
        anyString(), any(MediaType.class), anyString(), any(byte[].class)))
            .thenThrow(new IOException("Drive is down"));
    InternalServerErrorException thrown =
        assertThrows(
            InternalServerErrorException.class,
            () ->
                action.handleJsonRequest(
                    ImmutableMap.of(
                        REGISTRAR_ID_PARAM, "TheRegistrar",
                        GCS_BUCKET_PARAM, "mah-buckit",
                        GCS_FOLDER_PREFIX_PARAM, "some/folder/",
                        DETAIL_REPORT_NAME_PARAM, "detail_report.csv")));
    assertThat(thrown).hasMessageThat().contains("Drive is down");
  }
}
