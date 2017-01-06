// Copyright 2016 The Nomulus Authors. All Rights Reserved.
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

package google.registry.tmch;

import static com.google.appengine.api.taskqueue.QueueFactory.getQueue;
import static com.google.appengine.api.taskqueue.TaskOptions.Builder.withUrl;
import static com.google.appengine.api.urlfetch.FetchOptions.Builder.validateCertificate;
import static com.google.appengine.api.urlfetch.HTTPMethod.POST;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.net.HttpHeaders.LOCATION;
import static com.google.common.net.MediaType.CSV_UTF_8;
import static google.registry.tmch.LordnTask.COLUMNS_CLAIMS;
import static google.registry.tmch.LordnTask.COLUMNS_SUNRISE;
import static google.registry.tmch.LordnTask.convertTasksToCsv;
import static google.registry.util.UrlFetchUtils.getHeaderFirst;
import static google.registry.util.UrlFetchUtils.setPayloadMultipart;
import static javax.servlet.http.HttpServletResponse.SC_ACCEPTED;

import com.google.appengine.api.taskqueue.Queue;
import com.google.appengine.api.taskqueue.TaskHandle;
import com.google.appengine.api.taskqueue.TaskOptions;
import com.google.appengine.api.urlfetch.HTTPRequest;
import com.google.appengine.api.urlfetch.HTTPResponse;
import com.google.appengine.api.urlfetch.URLFetchService;
import com.google.common.base.Optional;
import google.registry.config.RegistryConfig.Config;
import google.registry.request.Action;
import google.registry.request.Parameter;
import google.registry.request.RequestParameters;
import google.registry.util.Clock;
import google.registry.util.FormattingLogger;
import google.registry.util.UrlFetchException;
import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.Random;
import javax.inject.Inject;
import org.joda.time.DateTime;
import org.joda.time.Duration;

/**
 * Action that reads the NORDN pull queues, uploads claims and sunrise marks data to TMCH, and
 * enqueues subsequent upload verification tasks.  A unique actionLogId is generated and passed
 * along to the verify action so that connected verify tasks can be identified by looking at logs.
 *
 * @see NordnVerifyAction
 */
@Action(path = NordnUploadAction.PATH, method = Action.Method.POST, automaticallyPrintOk = true)
public final class NordnUploadAction implements Runnable {

  static final String PATH = "/_dr/task/nordnUpload";
  static final String LORDN_PHASE_PARAM = "lordn-phase";

  private static final FormattingLogger logger = FormattingLogger.getLoggerForCallerClass();

  /**
   * A unique (enough) id that is outputted in log lines to make it clear which log lines are
   * associated with a given invocation of the NordnUploadAction in the event that multiple
   * instances execute simultaneously.
   */
  private final String actionLogId = String.valueOf(1000000000 + new Random().nextInt(1000000000));

  @Inject Clock clock;
  @Inject LordnRequestInitializer lordnRequestInitializer;
  @Inject URLFetchService fetchService;
  @Inject @Config("tmchMarksdbUrl") String tmchMarksdbUrl;
  @Inject @Parameter(LORDN_PHASE_PARAM) String phase;
  @Inject @Parameter(RequestParameters.PARAM_TLD) String tld;
  @Inject NordnUploadAction() {}

  /**
   * These LORDN parameter names correspond to the relative paths in LORDN URLs and cannot be
   * changed on our end.
   */
  private static final String PARAM_LORDN_PHASE_SUNRISE = "sunrise";
  private static final String PARAM_LORDN_PHASE_CLAIMS = "claims";

  /** How long to wait before attempting to verify an upload by fetching the log. */
  private static final Duration VERIFY_DELAY = Duration.standardMinutes(30);

  @Override
  public void run() {
    try {
      processLordnTasks();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private void processLordnTasks() throws IOException {
    checkArgument(phase.equals(PARAM_LORDN_PHASE_SUNRISE)
        || phase.equals(PARAM_LORDN_PHASE_CLAIMS),
        "Invalid phase specified to Nordn servlet: %s.", phase);
    DateTime now = clock.nowUtc();
    Queue queue = getQueue(
        phase.equals(PARAM_LORDN_PHASE_SUNRISE) ? LordnTask.QUEUE_SUNRISE : LordnTask.QUEUE_CLAIMS);
    String columns = phase.equals(PARAM_LORDN_PHASE_SUNRISE) ? COLUMNS_SUNRISE : COLUMNS_CLAIMS;
    List<TaskHandle> tasks = LordnTask.loadAllTasks(queue, tld);
    if (!tasks.isEmpty()) {
      String csvData = convertTasksToCsv(tasks, now, columns);
      uploadCsvToLordn(String.format("/LORDN/%s/%s", tld, phase), csvData);
      queue.deleteTask(tasks);
    }
  }

  /**
   * Upload LORDN file to MarksDB.
   *
   * <p>Idempotency: If the exact same LORDN report is uploaded twice, the MarksDB server will
   * return the same confirmation number.
   *
   * @see <a href="http://tools.ietf.org/html/draft-lozano-tmch-func-spec-08#section-6.3">
   *     TMCH functional specifications - LORDN File</a>
   */
  private void uploadCsvToLordn(String urlPath, String csvData) throws IOException {
    String url = tmchMarksdbUrl + urlPath;
    logger.infofmt("LORDN upload task %s: Sending to URL: %s ; data: %s",
        actionLogId, url, csvData);
    HTTPRequest req = new HTTPRequest(new URL(url), POST, validateCertificate().setDeadline(60d));
    lordnRequestInitializer.initialize(req, tld);
    setPayloadMultipart(req, "file", "claims.csv", CSV_UTF_8, csvData);
    HTTPResponse rsp = fetchService.fetch(req);
    logger.infofmt("LORDN upload task %s response: HTTP response code %d, response data: %s",
        actionLogId, rsp.getResponseCode(), rsp.getContent());
    if (rsp.getResponseCode() != SC_ACCEPTED) {
      throw new UrlFetchException(
          String.format("LORDN upload task %s error: Failed to upload LORDN claims to MarksDB",
              actionLogId),
          req, rsp);
    }
    Optional<String> location = getHeaderFirst(rsp, LOCATION);
    if (!location.isPresent()) {
      throw new UrlFetchException(
          String.format("LORDN upload task %s error: MarksDB failed to provide a Location header",
              actionLogId),
          req, rsp);
    }
    getQueue(NordnVerifyAction.QUEUE).add(makeVerifyTask(new URL(location.get()), csvData));
  }

  private TaskOptions makeVerifyTask(URL url, String csvData) {
    // This task doesn't technically need csvData. The only reason it's passed along is in case the
    // upload is rejected, in which case csvData will be logged so that it may be uploaded manually.
    return withUrl(NordnVerifyAction.PATH)
        .header(NordnVerifyAction.URL_HEADER, url.toString())
        .header(NordnVerifyAction.HEADER_ACTION_LOG_ID, actionLogId)
        .param(RequestParameters.PARAM_TLD, tld)
        .param(NordnVerifyAction.PARAM_CSV_DATA, csvData)
        .countdownMillis(VERIFY_DELAY.getMillis());
  }
}
