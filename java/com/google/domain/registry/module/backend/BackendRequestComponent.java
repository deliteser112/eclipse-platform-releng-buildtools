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

package com.google.domain.registry.module.backend;

import com.google.domain.registry.backup.BackupModule;
import com.google.domain.registry.backup.CommitLogCheckpointAction;
import com.google.domain.registry.backup.DeleteOldCommitLogsAction;
import com.google.domain.registry.backup.ExportCommitLogDiffAction;
import com.google.domain.registry.backup.RestoreCommitLogsAction;
import com.google.domain.registry.cron.CommitLogFanoutAction;
import com.google.domain.registry.cron.CronModule;
import com.google.domain.registry.cron.TldFanoutAction;
import com.google.domain.registry.dns.DnsModule;
import com.google.domain.registry.dns.PublishDnsUpdatesAction;
import com.google.domain.registry.dns.ReadDnsQueueAction;
import com.google.domain.registry.dns.RefreshDns;
import com.google.domain.registry.dns.WriteDnsTask;
import com.google.domain.registry.export.BigqueryPollJobAction;
import com.google.domain.registry.export.ExportRequestModule;
import com.google.domain.registry.export.ExportReservedTermsTask;
import com.google.domain.registry.export.SyncGroupMembersTask;
import com.google.domain.registry.export.sheet.SheetModule;
import com.google.domain.registry.export.sheet.SyncRegistrarsSheetTask;
import com.google.domain.registry.flows.async.AsyncFlowsModule;
import com.google.domain.registry.flows.async.DeleteContactResourceAction;
import com.google.domain.registry.flows.async.DeleteHostResourceAction;
import com.google.domain.registry.flows.async.DnsRefreshForHostRenameAction;
import com.google.domain.registry.mapreduce.MapreduceModule;
import com.google.domain.registry.rde.BrdaCopyTask;
import com.google.domain.registry.rde.RdeModule;
import com.google.domain.registry.rde.RdeReportTask;
import com.google.domain.registry.rde.RdeReporter;
import com.google.domain.registry.rde.RdeStagingAction;
import com.google.domain.registry.rde.RdeUploadTask;
import com.google.domain.registry.request.RequestModule;
import com.google.domain.registry.request.RequestScope;
import com.google.domain.registry.tmch.NordnUploadAction;
import com.google.domain.registry.tmch.NordnVerifyAction;
import com.google.domain.registry.tmch.TmchCrlTask;
import com.google.domain.registry.tmch.TmchDnlTask;
import com.google.domain.registry.tmch.TmchModule;
import com.google.domain.registry.tmch.TmchSmdrlTask;

import dagger.Subcomponent;

/** Dagger component with per-request lifetime for "backend" App Engine module. */
@RequestScope
@Subcomponent(
    modules = {
        AsyncFlowsModule.class,
        BackendModule.class,
        BackupModule.class,
        CronModule.class,
        DnsModule.class,
        ExportRequestModule.class,
        MapreduceModule.class,
        RdeModule.class,
        RequestModule.class,
        SheetModule.class,
        TmchModule.class,
    })
interface BackendRequestComponent {
  BigqueryPollJobAction bigqueryPollJobAction();
  BrdaCopyTask brdaCopyTask();
  CommitLogCheckpointAction commitLogCheckpointAction();
  CommitLogFanoutAction commitLogFanoutAction();
  DeleteContactResourceAction deleteContactResourceAction();
  DeleteHostResourceAction deleteHostResourceAction();
  DeleteOldCommitLogsAction deleteOldCommitLogsAction();
  DnsRefreshForHostRenameAction dnsRefreshForHostRenameAction();
  ExportCommitLogDiffAction exportCommitLogDiffAction();
  ExportReservedTermsTask exportReservedTermsTask();
  NordnUploadAction nordnUploadAction();
  NordnVerifyAction nordnVerifyAction();
  PublishDnsUpdatesAction publishDnsUpdatesAction();
  ReadDnsQueueAction readDnsQueueAction();
  RdeReportTask rdeReportTask();
  RdeStagingAction rdeStagingAction();
  RdeUploadTask rdeUploadTask();
  RdeReporter rdeReporter();
  RefreshDns refreshDns();
  RestoreCommitLogsAction restoreCommitLogsAction();
  SyncGroupMembersTask syncGroupMembersTask();
  SyncRegistrarsSheetTask syncRegistrarsSheetTask();
  TldFanoutAction tldFanoutAction();
  TmchCrlTask tmchCrlTask();
  TmchDnlTask tmchDnlTask();
  TmchSmdrlTask tmchSmdrlTask();
  WriteDnsTask writeDnsTask();
}
