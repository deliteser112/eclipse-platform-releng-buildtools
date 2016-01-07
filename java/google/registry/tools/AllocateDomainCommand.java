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

package google.registry.tools;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.base.Strings.emptyToNull;
import static com.google.common.collect.Iterables.transform;
import static com.google.common.io.BaseEncoding.base16;
import static google.registry.flows.EppXmlTransformer.unmarshal;
import static google.registry.model.ofy.ObjectifyService.ofy;
import static google.registry.tools.CommandUtilities.addHeader;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.google.common.base.Ascii;
import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableMap;
import com.google.template.soy.data.SoyMapData;
import com.googlecode.objectify.Key;
import com.googlecode.objectify.VoidWork;
import com.googlecode.objectify.Work;
import google.registry.flows.EppException;
import google.registry.model.domain.DesignatedContact;
import google.registry.model.domain.DomainApplication;
import google.registry.model.domain.DomainCommand;
import google.registry.model.domain.Period;
import google.registry.model.domain.launch.ApplicationStatus;
import google.registry.model.domain.launch.LaunchNotice;
import google.registry.model.domain.secdns.DelegationSignerData;
import google.registry.model.eppinput.EppInput;
import google.registry.model.eppinput.EppInput.ResourceCommandWrapper;
import google.registry.model.reporting.HistoryEntry;
import google.registry.model.smd.SignedMark;
import google.registry.tools.soy.DomainAllocateSoyInfo;
import java.util.ArrayList;
import java.util.List;

/** Command to allocated a domain from a domain application. */
@Parameters(separators = " =", commandDescription = "Allocate a domain application")
final class AllocateDomainCommand extends MutatingEppToolCommand {

  @Parameter(
      names = "--ids",
      description = "Comma-delimited list of application IDs to update.",
      required = true)
  String ids;

  private final List<Key<DomainApplication>> applicationKeys = new ArrayList<>();

  @Override
  protected String postExecute() throws Exception {
    StringBuilder builder = new StringBuilder();
    // Check to see that we allocated everything.
    return builder.append(ofy().transactNewReadOnly(new Work<String>() {
      @Override
      public String run() {
        String failureMessage = FluentIterable
            .from(ofy().load().keys(applicationKeys).values())
            .transform(new Function<DomainApplication, String>() {
                @Override
                public String apply(DomainApplication application) {
                  return application.getApplicationStatus() == ApplicationStatus.ALLOCATED
                       ? null : application.getFullyQualifiedDomainName();
                }})
            .join(Joiner.on('\n').skipNulls());
        return failureMessage.isEmpty() ? "ALL SUCCEEDED" : addHeader("FAILURES", failureMessage);
      }})).toString();
  }

  /** Extract the registration period from the XML used to create the domain application. */
  private static Period extractPeriodFromXml(byte[] xmlBytes) throws EppException {
    EppInput eppInput = unmarshal(EppInput.class, xmlBytes);
    return ((DomainCommand.Create)
        ((ResourceCommandWrapper) eppInput.getCommandWrapper().getCommand())
            .getResourceCommand()).getPeriod();
  }

  @Override
  protected void initMutatingEppToolCommand() {
    checkArgument(superuser, "This command MUST be run as --superuser.");
    setSoyTemplate(DomainAllocateSoyInfo.getInstance(), DomainAllocateSoyInfo.CREATE);
    ofy().transactNewReadOnly(new VoidWork() {
      @Override
      public void vrun() {
        Iterable<Key<DomainApplication>> keys = transform(
            Splitter.on(',').split(ids),
            new Function<String, Key<DomainApplication>>() {
              @Override
              public Key<DomainApplication> apply(String applicationId) {
                return Key.create(DomainApplication.class, applicationId);
              }});
        for (DomainApplication application : ofy().load().keys(keys).values()) {
          // If the application is already allocated print a warning but do not fail.
          if (application.getApplicationStatus() == ApplicationStatus.ALLOCATED) {
            System.err.printf(
                "Application %s has already been allocated\n", application.getRepoId());
            continue;
          }
          // Ensure domain doesn't already have a final status which it shouldn't be updated from.
          checkState(
              !application.getApplicationStatus().isFinalStatus(),
              "Application has final status %s",
              application.getApplicationStatus());
          try {
            HistoryEntry history = checkNotNull(
                ofy().load()
                    .type(HistoryEntry.class)
                    .ancestor(checkNotNull(application))
                    .order("modificationTime")
                    .first()
                    .now(),
                "Could not find any history entries for domain application %s",
                application.getRepoId());
            String clientTransactionId =
                emptyToNull(history.getTrid().getClientTransactionId());
            Period period = checkNotNull(extractPeriodFromXml(history.getXmlBytes()));
            checkArgument(period.getUnit() == Period.Unit.YEARS);
            ImmutableMap.Builder<String, String> contactsMapBuilder = new ImmutableMap.Builder<>();
            for (DesignatedContact contact : application.getContacts()) {
              contactsMapBuilder.put(
                  Ascii.toLowerCase(contact.getType().toString()),
                  ofy().load().key(contact.getContactKey()).now().getForeignKey());
            }
            LaunchNotice launchNotice = application.getLaunchNotice();
            addSoyRecord(application.getCurrentSponsorClientId(), new SoyMapData(
                "name", application.getFullyQualifiedDomainName(),
                "period", period.getValue(),
                "nameservers", application.loadNameserverFullyQualifiedHostNames(),
                "registrant", ofy().load().key(application.getRegistrant()).now().getForeignKey(),
                "contacts", contactsMapBuilder.build(),
                "authInfo", application.getAuthInfo().getPw().getValue(),
                "smdId", application.getEncodedSignedMarks().isEmpty()
                    ? null
                    : unmarshal(
                        SignedMark.class,
                        application.getEncodedSignedMarks().get(0).getBytes()).getId(),
                "applicationRoid", application.getRepoId(),
                "applicationTime", application.getCreationTime().toString(),
                "launchNotice", launchNotice == null ? null : ImmutableMap.of(
                    "noticeId", launchNotice.getNoticeId().getTcnId(),
                    "expirationTime", launchNotice.getExpirationTime().toString(),
                    "acceptedTime", launchNotice.getAcceptedTime().toString()),
                "dsRecords", FluentIterable.from(application.getDsData())
                    .transform(new Function<DelegationSignerData, ImmutableMap<String, ?>>() {
                        @Override
                        public ImmutableMap<String, ?> apply(DelegationSignerData dsData) {
                          return ImmutableMap.of(
                              "keyTag", dsData.getKeyTag(),
                              "algorithm", dsData.getAlgorithm(),
                              "digestType", dsData.getDigestType(),
                              "digest", base16().encode(dsData.getDigest()));
                        }})
                    .toList(),
                "clTrid", clientTransactionId));
            applicationKeys.add(Key.create(application));
          } catch (EppException e) {
            throw new RuntimeException(e);
          }
        }
      }
    });
  }
}
