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

package google.registry.tools;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.base.Strings.emptyToNull;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.Iterables.transform;
import static com.google.common.io.BaseEncoding.base16;
import static google.registry.model.eppcommon.EppXmlTransformer.unmarshal;
import static google.registry.model.ofy.ObjectifyService.ofy;
import static google.registry.tools.CommandUtilities.addHeader;
import static java.util.stream.Collectors.joining;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.google.common.base.Ascii;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.template.soy.data.SoyMapData;
import com.googlecode.objectify.Key;
import google.registry.model.domain.DesignatedContact;
import google.registry.model.domain.DomainApplication;
import google.registry.model.domain.DomainCommand;
import google.registry.model.domain.Period;
import google.registry.model.domain.launch.ApplicationStatus;
import google.registry.model.domain.launch.LaunchNotice;
import google.registry.model.eppinput.EppInput;
import google.registry.model.eppinput.EppInput.ResourceCommandWrapper;
import google.registry.model.reporting.HistoryEntry;
import google.registry.model.smd.SignedMark;
import google.registry.tools.soy.DomainAllocateSoyInfo;
import google.registry.xml.XmlException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

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
  protected String postExecute() {
    return ofy()
        .transactNewReadOnly(
            () -> {
              String failureMessage =
                  ofy()
                      .load()
                      .keys(applicationKeys)
                      .values()
                      .stream()
                      .map(
                          application ->
                              application.getApplicationStatus()
                                  == ApplicationStatus.ALLOCATED
                                  ? null
                                  : application.getFullyQualifiedDomainName())
                      .filter(Objects::nonNull)
                      .collect(joining("\n"));
              return failureMessage.isEmpty()
                  ? "ALL SUCCEEDED"
                  : addHeader("FAILURES", failureMessage);
            });
  }

  /** Extract the registration period from the XML used to create the domain application. */
  private static Period extractPeriodFromXml(byte[] xmlBytes) throws XmlException {
    EppInput eppInput = unmarshal(EppInput.class, xmlBytes);
    return ((DomainCommand.Create)
        ((ResourceCommandWrapper) eppInput.getCommandWrapper().getCommand())
            .getResourceCommand()).getPeriod();
  }

  @Override
  protected void initMutatingEppToolCommand() {
    checkArgument(superuser, "This command MUST be run as --superuser.");
    setSoyTemplate(DomainAllocateSoyInfo.getInstance(), DomainAllocateSoyInfo.CREATE);
    ofy().transactNewReadOnly(this::initAllocateDomainCommand);
  }

  private void initAllocateDomainCommand() {
    Iterable<Key<DomainApplication>> keys =
        transform(
            Splitter.on(',').split(ids),
            applicationId -> Key.create(DomainApplication.class, applicationId));
    for (DomainApplication application : ofy().load().keys(keys).values()) {
      // If the application is already allocated print a warning but do not fail.
      if (application.getApplicationStatus() == ApplicationStatus.ALLOCATED) {
        System.err.printf("Application %s has already been allocated\n", application.getRepoId());
        continue;
      }
      // Ensure domain doesn't already have a final status which it shouldn't be updated
      // from.
      checkState(
          !application.getApplicationStatus().isFinalStatus(),
          "Application has final status %s",
          application.getApplicationStatus());
      try {
        HistoryEntry history =
            checkNotNull(
                ofy()
                    .load()
                    .type(HistoryEntry.class)
                    .ancestor(checkNotNull(application))
                    .order("modificationTime")
                    .first()
                    .now(),
                "Could not find any history entries for domain application %s",
                application.getRepoId());
        String clientTransactionId =
            emptyToNull(history.getTrid().getClientTransactionId().orElse(null));
        Period period = checkNotNull(extractPeriodFromXml(history.getXmlBytes()));
        checkArgument(period.getUnit() == Period.Unit.YEARS);
        ImmutableMap.Builder<String, String> contactsMapBuilder = new ImmutableMap.Builder<>();
        for (DesignatedContact contact : application.getContacts()) {
          contactsMapBuilder.put(
              Ascii.toLowerCase(contact.getType().toString()),
              ofy().load().key(contact.getContactKey()).now().getForeignKey());
        }
        LaunchNotice launchNotice = application.getLaunchNotice();
        String smdId =
            application.getEncodedSignedMarks().isEmpty()
                ? null
                : unmarshal(SignedMark.class, application.getEncodedSignedMarks().get(0).getBytes())
                    .getId();
        ImmutableMap<String, String> launchNoticeMap =
            (launchNotice == null)
                ? null
                : ImmutableMap.of(
                    "noticeId", launchNotice.getNoticeId().getTcnId(),
                    "expirationTime", launchNotice.getExpirationTime().toString(),
                    "acceptedTime", launchNotice.getAcceptedTime().toString());
        ImmutableList<ImmutableMap<String, ?>> dsRecords =
            application
                .getDsData()
                .stream()
                .map(
                    dsData ->
                        ImmutableMap.of(
                            "keyTag", dsData.getKeyTag(),
                            "algorithm", dsData.getAlgorithm(),
                            "digestType", dsData.getDigestType(),
                            "digest", base16().encode(dsData.getDigest())))
                .collect(toImmutableList());
        addSoyRecord(
            application.getCurrentSponsorClientId(),
            new SoyMapData(
                "name", application.getFullyQualifiedDomainName(),
                "period", period.getValue(),
                "nameservers", application.loadNameserverFullyQualifiedHostNames(),
                "registrant", ofy().load().key(application.getRegistrant()).now().getForeignKey(),
                "contacts", contactsMapBuilder.build(),
                "authInfo", application.getAuthInfo().getPw().getValue(),
                "smdId", smdId,
                "applicationRoid", application.getRepoId(),
                "applicationTime", application.getCreationTime().toString(),
                "launchNotice", launchNoticeMap,
                "dsRecords", dsRecords,
                "clTrid", clientTransactionId));
        applicationKeys.add(Key.create(application));
      } catch (XmlException e) {
        throw new RuntimeException(e);
      }
    }
  }
}
