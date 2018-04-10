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

import com.google.appengine.tools.remoteapi.RemoteApiInstaller;
import com.google.appengine.tools.remoteapi.RemoteApiOptions;

import static com.google.common.base.Preconditions.checkState;
import static google.registry.model.ofy.ObjectifyService.ofy;
import static google.registry.tools.Injector.injectReflectively;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;
import com.beust.jcommander.Parameters;
import com.beust.jcommander.ParametersDelegate;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import google.registry.model.ofy.ObjectifyService;
import google.registry.tools.Command.RemoteApiCommand;
import google.registry.tools.params.ParameterFactory;
import java.security.Security;
import java.util.Map;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

/** Container class to create and run remote commands against a Datastore instance. */
@Parameters(separators = " =", commandDescription = "Command-line interface to the registry")
final class RegistryCli implements AutoCloseable, CommandRunner {

  @Parameter(
      names = {"-e", "--environment"},
      description = "Sets the default environment to run the command.")
  private RegistryToolEnvironment environment = RegistryToolEnvironment.PRODUCTION;

  @Parameter(
      names = {"-c", "--commands"},
      description = "Returns all command names.")
  private boolean showAllCommands;

  // Do not make this final - compile-time constant inlining may interfere with JCommander.
  @ParametersDelegate
  private AppEngineConnectionFlags appEngineConnectionFlags =
      new AppEngineConnectionFlags();


  // Do not make this final - compile-time constant inlining may interfere with JCommander.
  @ParametersDelegate
  private LoggingParameters loggingParams = new LoggingParameters();

  // These are created lazily on first use.
  private AppEngineConnection connection;
  private RemoteApiInstaller installer;

  // The "shell" command should only exist on first use - so that we can't run "shell" inside
  // "shell".
  private boolean isFirstUse = true;

  Map<String, ? extends Class<? extends Command>> commands;
  String programName;

  RegistryCli(
      String programName, ImmutableMap<String, ? extends Class<? extends Command>> commands) {
    this.programName = programName;
    this.commands = commands;

    Security.addProvider(new BouncyCastleProvider());
  }

  // The <? extends Class<? extends Command>> wildcard looks a little funny, but is needed so that
  // we can accept maps with value types that are subtypes of Class<? extends Command> rather than
  // literally that type.  For more explanation, see:
  //   http://www.angelikalanger.com/GenericsFAQ/FAQSections/TypeArguments.html#FAQ104
  @Override
  public void run(String[] args) throws Exception {

    // Create the JCommander instance.
    JCommander jcommander = new JCommander(this);
    jcommander.addConverterFactory(new ParameterFactory());
    jcommander.setProgramName(programName);

    // Create all command instances. It would be preferrable to do this in the constructor, but
    // JCommander mutates the command instances and doesn't reset them so we have to do it for every
    // run.
    try {
      for (Map.Entry<String, ? extends Class<? extends Command>> entry : commands.entrySet()) {
        Command command = entry.getValue().getDeclaredConstructor().newInstance();
        jcommander.addCommand(entry.getKey(), command);
      }
    } catch (ReflectiveOperationException e) {
      throw new RuntimeException(e);
    }

    // Create the "help" and "shell" commands (these are special in that they don't have a default
    // constructor).
    jcommander.addCommand("help", new HelpCommand(jcommander));
    if (isFirstUse) {
      isFirstUse = false;
      ShellCommand shellCommand = new ShellCommand(this);
      // We have to build the completions based on the jcommander *before* we add the "shell"
      // command - to avoid completion for the "shell" command itself.
      shellCommand.buildCompletions(jcommander);
      jcommander.addCommand("shell", shellCommand);
    }

    try {
      jcommander.parse(args);
    } catch (ParameterException e) {
      // If we failed to fully parse the command but at least found a valid command name, show only
      // the usage for that command. Otherwise, show full usage. Either way, rethrow the error.
      if (jcommander.getParsedCommand() == null) {
        jcommander.usage();
      } else {
        jcommander.usage(jcommander.getParsedCommand());
      }
      // Don't rethrow if we said: nomulus command --help
      if ("Unknown option: --help".equals(e.getMessage())) {
        return;
      }
      throw e;
    }

    if (showAllCommands) {
      commands.keySet().forEach(System.out::println);
      return;
    }

    checkState(RegistryToolEnvironment.get() == environment,
        "RegistryToolEnvironment argument pre-processing kludge failed.");

    // JCommander stores sub-commands as nested JCommander objects containing a list of user objects
    // to be populated.  Extract the subcommand by getting the JCommander wrapper and then
    // retrieving the first (and, by virtue of our usage, only) object from it.
    Command command =
        (Command)
            Iterables.getOnlyElement(
                jcommander.getCommands().get(jcommander.getParsedCommand()).getObjects());
    loggingParams.configureLogging();  // Must be called after parameters are parsed.

    try {
      runCommand(command);
    } catch (AuthModule.LoginRequiredException ex) {
      System.err.println("===================================================================");
      System.err.println("You must login using 'nomulus login' prior to running this command.");
      System.err.println("===================================================================");
    }
  }

  @Override
  public void close() {
    if (installer != null) {
      installer.uninstall();
      installer = null;
    }
  }

  private void runCommand(Command command) throws Exception {
    // Create the main component and use it to inject the command class.
    RegistryToolComponent component = DaggerRegistryToolComponent.builder()
        .flagsModule(new AppEngineConnectionFlags.FlagsModule(appEngineConnectionFlags))
        .build();
    injectReflectively(RegistryToolComponent.class, component, command);

    if (!(command instanceof RemoteApiCommand)) {
      // TODO(mmuller): this should be in the try/catch LoginRequiredException in case future
      // commands use our credential.
      command.run();
      return;
    }

    // Get the App Engine connection, advise the user if they are not currently logged in..
    if (connection == null) {
      connection = component.appEngineConnection();
    }

    if (command instanceof ServerSideCommand) {
      ((ServerSideCommand) command).setConnection(connection);
    }

    // RemoteApiCommands need to have the remote api installed to work.
    if (installer == null) {
      installer = new RemoteApiInstaller();
      RemoteApiOptions options = new RemoteApiOptions();
      options.server(connection.getServer().getHost(), connection.getServer().getPort());
      if (connection.isLocalhost()) {
        // Use dev credentials for localhost.
        options.useDevelopmentServerCredential();
      } else {
        options.useApplicationDefaultCredential();
      }
      installer.install(options);
    }

    // Ensure that all entity classes are loaded before command code runs.
    ObjectifyService.initOfy();
    // Make sure we start the command with a clean cache, so that any previous command won't
    // interfere with this one.
    ofy().clearSessionCache();

    command.run();
  }

  void setEnvironment(RegistryToolEnvironment environment) {
    this.environment = environment;
  }
}
