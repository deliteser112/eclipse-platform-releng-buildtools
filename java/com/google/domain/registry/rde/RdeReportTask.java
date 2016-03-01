// Copyright 2016 Google Inc. All Rights Reserved.
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

package com.google.domain.registry.rde;

import static com.google.common.base.Verify.verify;
import static com.google.common.net.MediaType.PLAIN_TEXT_UTF_8;
import static com.google.domain.registry.model.rde.RdeMode.FULL;
import static com.google.domain.registry.request.Action.Method.POST;
import static com.google.domain.registry.util.DateTimeUtils.START_OF_TIME;

import com.google.appengine.tools.cloudstorage.GcsFilename;
import com.google.common.io.ByteStreams;
import com.google.domain.registry.config.ConfigModule.Config;
import com.google.domain.registry.gcs.GcsUtils;
import com.google.domain.registry.keyring.api.KeyModule.Key;
import com.google.domain.registry.model.rde.RdeNamingUtils;
import com.google.domain.registry.model.registry.Registry;
import com.google.domain.registry.model.registry.RegistryCursor;
import com.google.domain.registry.model.registry.RegistryCursor.CursorType;
import com.google.domain.registry.rde.EscrowTaskRunner.EscrowTask;
import com.google.domain.registry.request.Action;
import com.google.domain.registry.request.HttpException.NoContentException;
import com.google.domain.registry.request.Parameter;
import com.google.domain.registry.request.RequestParameters;
import com.google.domain.registry.request.Response;
import com.google.domain.registry.util.FormattingLogger;

import org.bouncycastle.openpgp.PGPException;
import org.bouncycastle.openpgp.PGPPrivateKey;
import org.joda.time.DateTime;
import org.joda.time.Duration;

import java.io.IOException;
import java.io.InputStream;

import javax.inject.Inject;

/**
 * Uploads a small XML RDE report to ICANN after {@link RdeUploadTask} has finished.
 */
@Action(path = RdeReportTask.PATH, method = POST)
public final class RdeReportTask implements Runnable, EscrowTask {

  static final String PATH = "/_dr/task/rdeReport";

  private static final FormattingLogger logger = FormattingLogger.getLoggerForCallerClass();

  @Inject GcsUtils gcsUtils;
  @Inject Ghostryde ghostryde;
  @Inject EscrowTaskRunner runner;
  @Inject Response response;
  @Inject RdeReporter reporter;
  @Inject @Parameter(RequestParameters.PARAM_TLD) String tld;
  @Inject @Config("rdeBucket") String bucket;
  @Inject @Config("rdeInterval") Duration interval;
  @Inject @Config("rdeReportLockTimeout") Duration timeout;
  @Inject @Key("rdeStagingDecryptionKey") PGPPrivateKey stagingDecryptionKey;
  @Inject RdeReportTask() {}

  @Override
  public void run() {
    runner.lockRunAndRollForward(this, Registry.get(tld), timeout, CursorType.RDE_REPORT, interval);
  }

  @Override
  public void runWithLock(DateTime watermark) throws Exception {
    DateTime stagingCursor =
        RegistryCursor.load(Registry.get(tld), CursorType.RDE_UPLOAD).or(START_OF_TIME);
    if (!stagingCursor.isAfter(watermark)) {
      logger.infofmt("tld=%s reportCursor=%s uploadCursor=%s", tld, watermark, stagingCursor);
      throw new NoContentException("Waiting for RdeUploadTask to complete");
    }
    String prefix = RdeNamingUtils.makeRydeFilename(tld, watermark, FULL, 1, 0);
    GcsFilename reportFilename = new GcsFilename(bucket, prefix + "-report.xml.ghostryde");
    verify(gcsUtils.existsAndNotEmpty(reportFilename), "Missing file: %s", reportFilename);
    reporter.send(readReportFromGcs(reportFilename));
    response.setContentType(PLAIN_TEXT_UTF_8);
    response.setPayload(String.format("OK %s %s\n", tld, watermark));
  }

  /** Reads and decrypts the XML file from cloud storage. */
  private byte[] readReportFromGcs(GcsFilename reportFilename) throws IOException, PGPException {
    try (InputStream gcsInput = gcsUtils.openInputStream(reportFilename);
        Ghostryde.Decryptor decryptor = ghostryde.openDecryptor(gcsInput, stagingDecryptionKey);
        Ghostryde.Decompressor decompressor = ghostryde.openDecompressor(decryptor);
        Ghostryde.Input xmlInput = ghostryde.openInput(decompressor)) {
      return ByteStreams.toByteArray(xmlInput);
    }
  }
}
