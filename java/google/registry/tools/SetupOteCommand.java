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

package google.registry.tools;

import static com.google.common.base.Preconditions.checkArgument;
import static google.registry.tools.CommandUtilities.promptForYes;
import static google.registry.util.X509Utils.loadCertificate;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.re2j.Pattern;
import google.registry.config.RegistryEnvironment;
import google.registry.model.registrar.Registrar;
import google.registry.model.registry.Registry.TldState;
import google.registry.tools.Command.GtechCommand;
import google.registry.tools.Command.RemoteApiCommand;
import google.registry.tools.params.PathParameter;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import org.joda.time.Duration;

/** Composite command to set up OT&E TLDs and accounts. */
@Parameters(
    separators = " =",
    commandDescription = "Set up OT&E TLDs and registrars")
final class SetupOteCommand extends ConfirmingCommand implements RemoteApiCommand, GtechCommand {

  // Regex: 3-14 alphanumeric characters or hyphens, the first of which must be a letter.
  private static final Pattern REGISTRAR_PATTERN = Pattern.compile("^[a-z][-a-z0-9]{2,13}$");
  private static final int PASSWORD_LENGTH = 16;

  // Durations are short so that registrars can test with quick transfer (etc.) turnaround.
  private static final Duration SHORT_ADD_GRACE_PERIOD = Duration.standardMinutes(60);
  private static final Duration SHORT_REDEMPTION_GRACE_PERIOD = Duration.standardMinutes(10);
  private static final Duration SHORT_PENDING_DELETE_LENGTH = Duration.standardMinutes(5);

  private static final String DEFAULT_PREMIUM_LIST = "default_sandbox_list";

  @Parameter(
      names = {"-r", "--registrar"},
      description = "must 1) consist of only lowercase letters, numbers, or hyphens, "
          + "2) start with a letter, and 3) be between 3 and 14 characters (inclusive). "
          + "We require 1 and 2 since the registrar name will be used to create TLDs,"
          + "and we require 3 since we append \"-[1234]\" to the name to create client"
          + "IDs which are required by the EPP XML schema to be between 3-16 chars.",
      required = true)
  private String registrar;

  @Parameter(
      names = {"-w", "--ip_whitelist"},
      description = "comma separated list of IP addreses or CIDR ranges",
      required = true)
  private List<String> ipWhitelist = new ArrayList<>();

  @Parameter(
      names = {"-c", "--certfile"},
      description = "full path to cert file in PEM format (best if on local storage)",
      required = true,
      validateWith = PathParameter.InputFile.class)
  private Path certFile;

  @Parameter(
      names = {"--premium_list"},
      description = "premium list to apply to all TLDs")
  private String premiumList = DEFAULT_PREMIUM_LIST;

  @Inject
  PasswordGenerator passwordGenerator;

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
      Duration pendingDeleteLength) throws Exception {
    CreateTldCommand command = new CreateTldCommand();
    command.initialTldState = initialTldState;
    command.mainParameters = ImmutableList.of(tldName);
    command.roidSuffix = String.format(
        "%S%X", tldName.replaceAll("[^a-z0-9]", "").substring(0, 7), roidSuffixCounter++);
    command.addGracePeriod = addGracePeriod;
    command.redemptionGracePeriod = redemptionGracePeriod;
    command.pendingDeleteLength = pendingDeleteLength;
    command.premiumListName = Optional.of(premiumList);
    command.force = force;
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
    checkArgument(REGISTRAR_PATTERN.matcher(registrar).matches(),
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

    // Don't wait for create_registrar to fail if it's a bad certificate file.
    loadCertificate(certFile.toAbsolutePath());
    return true;
  }

  @Override
  protected String prompt() throws Exception {
    // Each underlying command will confirm its own operation as well, so just provide
    // a summary of the steps in this command.
    return "Creating TLDs:\n"
        + "    " + registrar + "-sunrise\n"
        + "    " + registrar + "-landrush\n"
        + "    " + registrar + "-ga\n"
        + "Creating registrars:\n"
        + "    " + registrar + "-1 (access to TLD " + registrar + "-sunrise)\n"
        + "    " + registrar + "-2 (access to TLD " + registrar + "-landrush)\n"
        + "    " + registrar + "-3 (access to TLD " + registrar + "-ga)\n"
        + "    " + registrar + "-4 (access to TLD " + registrar + "-ga)";
  }

  @Override
  public String execute() throws Exception {
    createTld(registrar + "-sunrise", TldState.SUNRISE, null, null, null);
    createTld(registrar + "-landrush", TldState.LANDRUSH, null, null, null);
    createTld(
        registrar + "-ga",
        TldState.GENERAL_AVAILABILITY,
        SHORT_ADD_GRACE_PERIOD,
        SHORT_REDEMPTION_GRACE_PERIOD,
        SHORT_PENDING_DELETE_LENGTH);

    // Storing names and credentials in a list of tuples for later play-back.
    List<List<String>> registrars = new ArrayList<>();
    registrars.add(ImmutableList.<String>of(
        registrar + "-1", passwordGenerator.createPassword(PASSWORD_LENGTH),
        registrar + "-sunrise"));
    registrars.add(ImmutableList.<String>of(
        registrar + "-2", passwordGenerator.createPassword(PASSWORD_LENGTH),
        registrar + "-landrush"));
    registrars.add(ImmutableList.<String>of(
        registrar + "-3", passwordGenerator.createPassword(PASSWORD_LENGTH),
        registrar + "-ga"));
    registrars.add(ImmutableList.<String>of(
        registrar + "-4", passwordGenerator.createPassword(PASSWORD_LENGTH),
        registrar + "-ga"));

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
