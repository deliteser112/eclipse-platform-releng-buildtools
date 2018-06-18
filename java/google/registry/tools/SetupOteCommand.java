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
import static google.registry.tools.CommandUtilities.promptForYes;
import static google.registry.util.X509Utils.loadCertificate;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSortedMap;
import com.google.re2j.Pattern;
import google.registry.config.RegistryEnvironment;
import google.registry.model.registrar.Registrar;
import google.registry.model.registry.Registry.TldState;
import google.registry.tools.Command.RemoteApiCommand;
import google.registry.tools.params.PathParameter;
import google.registry.util.StringGenerator;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Named;
import org.joda.money.CurrencyUnit;
import org.joda.money.Money;
import org.joda.time.DateTime;
import org.joda.time.Duration;

/** Composite command to set up OT&E TLDs and accounts. */
@Parameters(separators = " =", commandDescription = "Set up OT&E TLDs and registrars")
final class SetupOteCommand extends ConfirmingCommand implements RemoteApiCommand {

  // Regex: 3-14 alphanumeric characters or hyphens, the first of which must be a letter.
  private static final Pattern REGISTRAR_PATTERN = Pattern.compile("^[a-z][-a-z0-9]{2,13}$");
  private static final int PASSWORD_LENGTH = 16;

  // Durations are short so that registrars can test with quick transfer (etc.) turnaround.
  private static final Duration SHORT_ADD_GRACE_PERIOD = Duration.standardMinutes(60);
  private static final Duration SHORT_REDEMPTION_GRACE_PERIOD = Duration.standardMinutes(10);
  private static final Duration SHORT_PENDING_DELETE_LENGTH = Duration.standardMinutes(5);

  private static final ImmutableSortedMap<DateTime, Money> EAP_FEE_SCHEDULE =
      ImmutableSortedMap.of(
          new DateTime(0),
          Money.of(CurrencyUnit.USD, 0),
          DateTime.parse("2018-03-01T00:00:00Z"),
          Money.of(CurrencyUnit.USD, 100),
          DateTime.parse("2022-03-01T00:00:00Z"),
          Money.of(CurrencyUnit.USD, 0));

  private static final String DEFAULT_PREMIUM_LIST = "default_sandbox_list";

  @Inject
  @Named("dnsWriterNames")
  Set<String> validDnsWriterNames;

  @Parameter(
    names = {"-r", "--registrar"},
    description =
        "must 1) consist of only lowercase letters, numbers, or hyphens, "
            + "2) start with a letter, and 3) be between 3 and 14 characters (inclusive). "
            + "We require 1 and 2 since the registrar name will be used to create TLDs,"
            + "and we require 3 since we append \"-[1234]\" to the name to create client"
            + "IDs which are required by the EPP XML schema to be between 3-16 chars.",
    required = true
  )
  private String registrar;

  @Parameter(
    names = {"-w", "--ip_whitelist"},
    description = "comma separated list of IP addreses or CIDR ranges",
    required = true
  )
  private List<String> ipWhitelist = new ArrayList<>();

  @Parameter(
    names = {"-c", "--certfile"},
    description = "full path to cert file in PEM format (best if on local storage)",
    validateWith = PathParameter.InputFile.class
  )
  private Path certFile;

  @Parameter(
    names = {"-h", "--certhash"},
    description =
        "Hash of client certificate (SHA256 base64 no padding). Do not use this unless "
            + "you want to store ONLY the hash and not the full certificate"
  )
  private String certHash;

  @Parameter(
    names = {"--dns_writers"},
    description = "comma separated list of DNS writers to use on all TLDs",
    required = true
  )
  private List<String> dnsWriters;

  @Parameter(
    names = {"--premium_list"},
    description = "premium list to apply to all TLDs"
  )
  private String premiumList = DEFAULT_PREMIUM_LIST;

  // TODO: (b/74079782) remove this flag once OT&E for .app is complete.
  @Parameter(
    names = {"--eap_only"},
    description = "whether to only create EAP TLD and registrar"
  )
  private boolean eapOnly = false;

  @Inject StringGenerator passwordGenerator;

  /**
   * Long registrar names are truncated and then have an incrementing digit appended at the end so
   * that unique ROID suffixes can be generated for all TLDs for the registrar.
   */
  private int roidSuffixCounter = 0;

  /** Constructs and runs a CreateTldCommand. */
  private void createTld(
      String tldName,
      TldState initialTldState,
      Duration addGracePeriod,
      Duration redemptionGracePeriod,
      Duration pendingDeleteLength,
      boolean isEarlyAccess)
      throws Exception {
    CreateTldCommand command = new CreateTldCommand();
    command.addGracePeriod = addGracePeriod;
    command.dnsWriters = dnsWriters;
    command.validDnsWriterNames = validDnsWriterNames;
    command.force = force;
    command.initialTldState = initialTldState;
    command.mainParameters = ImmutableList.of(tldName);
    command.pendingDeleteLength = pendingDeleteLength;
    command.premiumListName = Optional.of(premiumList);
    String tldNameAlphaNumerical = tldName.replaceAll("[^a-z0-9]", "");
    command.roidSuffix =
        String.format(
            "%S%X",
            tldNameAlphaNumerical.substring(0, Math.min(tldNameAlphaNumerical.length(), 7)),
            roidSuffixCounter++);
    command.redemptionGracePeriod = redemptionGracePeriod;
    if (isEarlyAccess) {
      command.eapFeeSchedule = EAP_FEE_SCHEDULE;
    }
    command.run();
  }

  /** Constructs and runs a CreateRegistrarCommand */
  private void createRegistrar(String registrarName, String password, String tld) throws Exception {
    CreateRegistrarCommand command = new CreateRegistrarCommand();
    command.mainParameters = ImmutableList.of(registrarName);
    command.createGoogleGroups = false; // Don't create Google Groups for OT&E registrars.
    command.allowedTlds = ImmutableList.of(tld);
    command.registrarName = registrarName;
    command.registrarType = Registrar.Type.OTE;
    command.password = password;
    command.clientCertificateFilename = certFile;
    command.clientCertificateHash = certHash;
    command.ipWhitelist = ipWhitelist;
    command.street = ImmutableList.of("e-street");
    command.city = "Neverland";
    command.state = "ofmind";
    command.countryCode = "US";
    command.zip = "55555";
    command.email = Optional.of("foo@neverland.com");
    command.fax = Optional.of("+1.2125550100");
    command.phone = Optional.of("+1.2125550100");
    command.icannReferralEmail = "nightmare@registrar.test";
    command.force = force;
    command.run();
  }

  /** Run any pre-execute command checks */
  @Override
  protected boolean checkExecutionState() throws Exception {
    checkArgument(
        REGISTRAR_PATTERN.matcher(registrar).matches(),
        "Registrar name is invalid (see usage text for requirements).");

    boolean warned = false;
    if (RegistryEnvironment.get() != RegistryEnvironment.SANDBOX
        && RegistryEnvironment.get() != RegistryEnvironment.UNITTEST) {
      System.err.printf(
          "WARNING: Running against %s environment. Are "
              + "you sure you didn\'t mean to run this against sandbox (e.g. \"-e SANDBOX\")?%n",
          RegistryEnvironment.get());
      warned = true;
    }

    if (warned && !promptForYes("Proceed despite warnings?")) {
      System.out.println("Command aborted.");
      return false;
    }

    checkArgument(
        certFile == null ^ certHash == null,
        "Must specify exactly one of client certificate file or client certificate hash.");

    // Don't wait for create_registrar to fail if it's a bad certificate file.
    if (certFile != null) {
      loadCertificate(certFile.toAbsolutePath());
    }
    return true;
  }

  @Override
  protected String prompt() {
    // Each underlying command will confirm its own operation as well, so just provide
    // a summary of the steps in this command.
    if (eapOnly) {
      return "Creating TLD:\n"
          + "    " + registrar + "-eap\n"
          + "Creating registrar:\n"
          + "    " + registrar + "-5 (access to TLD " + registrar + "-eap)";
    } else {
      return "Creating TLDs:\n"
          + "    " + registrar + "-sunrise\n"
          + "    " + registrar + "-landrush\n"
          + "    " + registrar + "-ga\n"
          + "    " + registrar + "-eap\n"
          + "Creating registrars:\n"
          + "    " + registrar + "-1 (access to TLD " + registrar + "-sunrise)\n"
          + "    " + registrar + "-2 (access to TLD " + registrar + "-landrush)\n"
          + "    " + registrar + "-3 (access to TLD " + registrar + "-ga)\n"
          + "    " + registrar + "-4 (access to TLD " + registrar + "-ga)\n"
          + "    " + registrar + "-5 (access to TLD " + registrar + "-eap)";
    }
  }

  @Override
  public String execute() throws Exception {
    if (!eapOnly) {
      createTld(registrar + "-sunrise", TldState.START_DATE_SUNRISE, null, null, null, false);
      createTld(registrar + "-landrush", TldState.LANDRUSH, null, null, null, false);
      createTld(
          registrar + "-ga",
          TldState.GENERAL_AVAILABILITY,
          SHORT_ADD_GRACE_PERIOD,
          SHORT_REDEMPTION_GRACE_PERIOD,
          SHORT_PENDING_DELETE_LENGTH,
          false);
    } else {
      // Increase ROID suffix counter to not collide with existing TLDs.
      roidSuffixCounter = roidSuffixCounter + 3;
    }
    createTld(
        registrar + "-eap",
        TldState.GENERAL_AVAILABILITY,
        SHORT_ADD_GRACE_PERIOD,
        SHORT_REDEMPTION_GRACE_PERIOD,
        SHORT_PENDING_DELETE_LENGTH,
        true);

    // Storing names and credentials in a list of tuples for later play-back.
    List<List<String>> registrars = new ArrayList<>();
    if (!eapOnly) {
      registrars.add(
          ImmutableList.of(
              registrar + "-1",
              passwordGenerator.createString(PASSWORD_LENGTH),
              registrar + "-sunrise"));
      registrars.add(
          ImmutableList.of(
              registrar + "-2",
              passwordGenerator.createString(PASSWORD_LENGTH),
              registrar + "-landrush"));
      registrars.add(
          ImmutableList.of(
              registrar + "-3",
              passwordGenerator.createString(PASSWORD_LENGTH),
              registrar + "-ga"));
      registrars.add(
          ImmutableList.of(
              registrar + "-4",
              passwordGenerator.createString(PASSWORD_LENGTH),
              registrar + "-ga"));
    }
    registrars.add(
        ImmutableList.of(
            registrar + "-5", passwordGenerator.createString(PASSWORD_LENGTH), registrar + "-eap"));

    for (List<String> r : registrars) {
      createRegistrar(r.get(0), r.get(1), r.get(2));
    }

    StringBuilder output = new StringBuilder();

    output.append("Copy these usernames/passwords back into the onboarding bug:\n\n");

    for (List<String> r : registrars) {
      output.append("Login: " + r.get(0) + "\n");
      output.append("Password: " + r.get(1) + "\n");
      output.append("TLD: " + r.get(2) + "\n\n");
    }

    return output.toString();
  }
}
