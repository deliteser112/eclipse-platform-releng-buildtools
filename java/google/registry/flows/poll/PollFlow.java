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

package com.google.domain.registry.flows.poll;

import static com.google.domain.registry.model.ofy.ObjectifyService.ofy;

import com.google.common.collect.ImmutableList;
import com.google.domain.registry.flows.EppException;
import com.google.domain.registry.flows.LoggedInFlow;
import com.google.domain.registry.model.eppinput.EppInput.Poll;
import com.google.domain.registry.model.eppoutput.EppOutput;
import com.google.domain.registry.model.eppoutput.Response;
import com.google.domain.registry.model.eppoutput.Response.ResponseData;
import com.google.domain.registry.model.eppoutput.Response.ResponseExtension;
import com.google.domain.registry.model.eppoutput.Result;
import com.google.domain.registry.model.poll.MessageQueueInfo;
import com.google.domain.registry.model.poll.PollMessage;

import com.googlecode.objectify.Key;
import com.googlecode.objectify.Work;
import com.googlecode.objectify.cmd.Query;

import java.util.List;

/** Base class of EPP Poll command flows. Mostly provides datastore helper methods. */
public abstract class PollFlow extends LoggedInFlow {

  protected Poll command;

  @Override
  @SuppressWarnings("unchecked")
  protected final void initLoggedInFlow() throws EppException {
    command = (Poll) eppInput.getCommandWrapper().getCommand();
  }

  /**
   * Returns a query for all poll messages for the logged in registrar in the current TLD which are
   * not in the future.
   */
  private Query<PollMessage> getQuery() {
    return ofy().doTransactionless(new Work<Query<PollMessage>>() {
      @Override
      public Query<PollMessage> run() {
        return ofy().load()
            .type(PollMessage.class)
            .filter("clientId", getClientId())
            .filter("eventTime <=", now.toDate());
      }});
  }

  /** Return the length of the message queue for the logged in registrar. */
  protected int getMessageQueueLength() {
    return getQuery().keys().list().size();
  }

  /**
   * Retrieves the Keys of all active PollMessage entities for the current client ordered by
   * eventTime.
   */
  protected List<Key<PollMessage>> getMessageQueueKeysInOrder() {
    return getQuery().order("eventTime").keys().list();
  }

  protected EppOutput createOutput(
      Result.Code code,
      MessageQueueInfo messageQueueInfo,
      ImmutableList<ResponseData> responseData,
      ImmutableList<ResponseExtension> responseExtensions) {
    return EppOutput.create(new Response.Builder()
        .setTrid(trid)
        .setResult(Result.create(code))
        .setMessageQueueInfo(messageQueueInfo)
        .setResData(responseData)
        .setExtensions(responseExtensions)
        .setExecutionTime(now)
        .build());
  }
}
