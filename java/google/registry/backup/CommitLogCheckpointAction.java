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

package google.registry.backup;

import static com.google.appengine.api.taskqueue.QueueFactory.getQueue;
import static com.google.appengine.api.taskqueue.TaskOptions.Builder.withUrl;
import static google.registry.backup.ExportCommitLogDiffAction.LOWER_CHECKPOINT_TIME_PARAM;
import static google.registry.backup.ExportCommitLogDiffAction.UPPER_CHECKPOINT_TIME_PARAM;
import static google.registry.model.ofy.ObjectifyService.ofy;
import static google.registry.util.DateTimeUtils.isBeforeOrAt;
import static google.registry.util.FormattingLogger.getLoggerForCallerClass;

import com.googlecode.objectify.VoidWork;
import google.registry.model.ofy.CommitLogCheckpoint;
import google.registry.model.ofy.CommitLogCheckpointRoot;
import google.registry.request.Action;
import google.registry.util.Clock;
import google.registry.util.FormattingLogger;
import google.registry.util.TaskEnqueuer;
import javax.inject.Inject;
import org.joda.time.DateTime;

/**
 * Action that saves commit log checkpoints to datastore and kicks off a diff export task.
 *
 * <p>We separate computing and saving the checkpoint from exporting it because the export to GCS
 * is retryable but should not require the computation of a new checkpoint. Saving the checkpoint
 * and enqueuing the export task are done transactionally, so any checkpoint that is saved will be
 * exported to GCS very soon.
 *
 * <p>This action's supported method is GET rather than POST because it gets invoked via cron.
 */
@Action(
    path = "/_dr/cron/commitLogCheckpoint",
    method = Action.Method.GET,
    automaticallyPrintOk = true)
public final class CommitLogCheckpointAction implements Runnable {

  private static final FormattingLogger logger = getLoggerForCallerClass();

  private static final String QUEUE_NAME = "export-commits";

  @Inject Clock clock;
  @Inject CommitLogCheckpointStrategy strategy;
  @Inject TaskEnqueuer taskEnqueuer;
  @Inject CommitLogCheckpointAction() {}

  @Override
  public void run() {
    final CommitLogCheckpoint checkpoint = strategy.computeCheckpoint();
    logger.info("Generated candidate checkpoint for time " + checkpoint.getCheckpointTime());
    ofy().transact(new VoidWork() {
      @Override
      public void vrun() {
        DateTime lastWrittenTime = CommitLogCheckpointRoot.loadRoot().getLastWrittenTime();
        if (isBeforeOrAt(checkpoint.getCheckpointTime(), lastWrittenTime)) {
          logger.info("Newer checkpoint already written at time: " + lastWrittenTime);
          return;
        }
        ofy().saveWithoutBackup().entities(
            checkpoint,
            CommitLogCheckpointRoot.create(checkpoint.getCheckpointTime()));
        // Enqueue a diff task between previous and current checkpoints.
        taskEnqueuer.enqueue(
            getQueue(QUEUE_NAME),
            withUrl(ExportCommitLogDiffAction.PATH)
                .param(LOWER_CHECKPOINT_TIME_PARAM, lastWrittenTime.toString())
                .param(UPPER_CHECKPOINT_TIME_PARAM, checkpoint.getCheckpointTime().toString()));
      }});
  }
}
