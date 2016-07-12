// Copyright 2016 The Domain Registry Authors. All Rights Reserved.
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

package google.registry.export.sheet;

import static com.google.appengine.api.taskqueue.QueueFactory.getQueue;
import static com.google.appengine.api.taskqueue.TaskOptions.Builder.withUrl;
import static com.google.common.net.MediaType.PLAIN_TEXT_UTF_8;
import static google.registry.request.Action.Method.POST;
import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST;
import static javax.servlet.http.HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
import static javax.servlet.http.HttpServletResponse.SC_NO_CONTENT;
import static javax.servlet.http.HttpServletResponse.SC_OK;

import com.google.appengine.api.modules.ModulesService;
import com.google.appengine.api.modules.ModulesServiceFactory;
import com.google.appengine.api.taskqueue.TaskHandle;
import com.google.appengine.api.taskqueue.TaskOptions.Method;
import com.google.common.base.Optional;
import com.google.gdata.util.ServiceException;
import google.registry.config.ConfigModule.Config;
import google.registry.model.server.Lock;
import google.registry.request.Action;
import google.registry.request.Parameter;
import google.registry.request.Response;
import google.registry.util.FormattingLogger;
import google.registry.util.NonFinalForTesting;
import java.io.IOException;
import java.util.concurrent.Callable;
import javax.annotation.Nullable;
import javax.inject.Inject;
import org.joda.time.Duration;

/**
 * Action for synchronizing the registrars spreadsheet.
 *
 * <p>You can specify the spreadsheet ID by passing the "id" parameter. If this parameter is not
 * specified, then the spreadsheet ID will be obtained from the registry configuration.
 *
 * <p>Cron will run this action hourly. So in order to minimize Google Spreadsheets I/O, this action
 * will iterate through all registrars and check if any entries were modified in the past hour. If
 * no modifications were made, the action will exit without performing any syncing.
 *
 * <p><b>Note:</b> Setting the "id" parameter will disable the registrar update check.
 *
 * <p>Before using this service, you should make sure all the column headers listed in this source
 * file are present. You also need to share the spreadsheet with the email address from the JSON
 * credential file and give it edit permission.
 *
 * @see SyncRegistrarsSheet
 */
@Action(path = SyncRegistrarsSheetAction.PATH, method = POST)
public class SyncRegistrarsSheetAction implements Runnable {

  private enum Result {
    OK(SC_OK, "Sheet successfully updated."),
    NOTMODIFIED(SC_OK, "Registrars table hasn't been modified in past hour."),
    LOCKED(SC_NO_CONTENT, "Another task is currently writing to this sheet; dropping task."),
    MISSINGNO(SC_BAD_REQUEST, "No sheet ID specified or configured; dropping task.") {
      @Override
      protected void log(Exception cause) {
        logger.warningfmt(cause, "%s", message);
      }},
    FAILED(SC_INTERNAL_SERVER_ERROR, "Spreadsheet synchronization failed") {
      @Override
      protected void log(Exception cause) {
        logger.severefmt(cause, "%s", message);
      }};

    private final int statusCode;
    protected final String message;

    private Result(int statusCode, String message) {
      this.statusCode = statusCode;
      this.message = message;
    }

    /** Log an error message. Results that use log levels other than info should override this. */
    protected void log(@Nullable Exception cause) {
      logger.infofmt(cause, "%s", message);
    }

    private void send(Response response, @Nullable Exception cause) {
      log(cause);
      response.setStatus(statusCode);
      response.setContentType(PLAIN_TEXT_UTF_8);
      response.setPayload(String.format("%s %s\n", name(), message));
    }
  }

  public static final String PATH = "/_dr/task/syncRegistrarsSheet";
  private static final String QUEUE = "sheet";
  private static final String LOCK_NAME = "Synchronize registrars sheet";
  private static final FormattingLogger logger = FormattingLogger.getLoggerForCallerClass();

  @NonFinalForTesting
  private static ModulesService modulesService = ModulesServiceFactory.getModulesService();

  @Inject Response response;
  @Inject SyncRegistrarsSheet syncRegistrarsSheet;
  @Inject @Config("sheetLockTimeout") Duration timeout;
  @Inject @Config("sheetRegistrarId") Optional<String> idConfig;
  @Inject @Config("sheetRegistrarInterval") Duration interval;
  @Inject @Parameter("id") Optional<String> idParam;
  @Inject SyncRegistrarsSheetAction() {}

  @Override
  public void run() {
    final Optional<String> sheetId = idParam.or(idConfig);
    if (!sheetId.isPresent()) {
      Result.MISSINGNO.send(response, null);
      return;
    }
    if (!idParam.isPresent()) {
      // TODO(b/19082368): Use a cursor.
      if (!syncRegistrarsSheet.wasRegistrarsModifiedInLast(interval)) {
        Result.NOTMODIFIED.send(response, null);
        return;
      }
    }
    String sheetLockName = String.format("%s: %s", LOCK_NAME, sheetId.get());
    Callable<Void> runner = new Callable<Void>() {
      @Nullable
      @Override
      public Void call() throws IOException {
        try {
          syncRegistrarsSheet.run(sheetId.get());
          Result.OK.send(response, null);
        } catch (IOException | ServiceException e) {
          Result.FAILED.send(response, e);
        }
        return null;
      }
    };
    if (!Lock.executeWithLocks(runner, getClass(), "", timeout, sheetLockName)) {
      // If we fail to acquire the lock, it probably means lots of updates are happening at once, in
      // which case it should be safe to not bother. The task queue definition should *not* specify
      // max-concurrent-requests for this very reason.
      Result.LOCKED.send(response, null);
    }
  }

  /** Creates, enqueues, and returns a new backend task to sync registrar spreadsheets. */
  public static TaskHandle enqueueBackendTask() {
    String hostname = modulesService.getVersionHostname("backend", null);
    return getQueue(QUEUE).add(withUrl(PATH).method(Method.GET).header("Host", hostname));
  }
}
