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
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.monitoring.metrics.IncrementableMetric;
import com.google.monitoring.metrics.LabelDescriptor;
import com.google.monitoring.metrics.Metric;
import com.google.monitoring.metrics.MetricPoint;
import com.google.monitoring.metrics.MetricRegistryImpl;
import com.google.monitoring.metrics.MetricWriter;
import google.registry.model.ofy.ObjectifyService;
import google.registry.tools.params.ParameterFactory;
import java.io.IOException;
import java.security.Security;
import java.util.Map;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

/** Container class to create and run remote commands against a Datastore instance. */
@Parameters(separators = " =", commandDescription = "Command-line interface to the registry")
final class RegistryCli implements AutoCloseable, CommandRunner {

  // The environment parameter is parsed twice: once here, and once with {@link
  // RegistryToolEnvironment#parseFromArgs} in the {@link RegistryTool#main} or {@link
  // GtechTool#main} functions.
  //
  // The flag names must be in sync between the two, and also - this is ugly and we should feel bad.
  @Parameter(
      names = {"-e", "--environment"},
      description = "Sets the default environment to run the command.")
  private RegistryToolEnvironment environment = RegistryToolEnvironment.PRODUCTION;

  @Parameter(
      names = {"-c", "--commands"},
      description = "Returns all command names.")
  private boolean showAllCommands;

  @VisibleForTesting
  boolean uploadMetrics = true;

  // Do not make this final - compile-time constant inlining may interfere with JCommander.
  @ParametersDelegate
  private AppEngineConnectionFlags appEngineConnectionFlags =
      new AppEngineConnectionFlags();


  // Do not make this final - compile-time constant inlining may interfere with JCommander.
  @ParametersDelegate
  private LoggingParameters loggingParams = new LoggingParameters();

  RegistryToolComponent component;

  // These are created lazily on first use.
  private AppEngineConnection connection;
  private RemoteApiInstaller installer;

  // The "shell" command should only exist on first use - so that we can't run "shell" inside
  // "shell".
  private boolean isFirstUse = true;

  private static final ImmutableSet<LabelDescriptor> LABEL_DESCRIPTORS_FOR_COMMANDS =
      ImmutableSet.of(
          LabelDescriptor.create("program", "The program used - e.g. nomulus or gtech_tool"),
          LabelDescriptor.create("environment", "The environment used - e.g. sandbox"),
          LabelDescriptor.create("command", "The command used"),
          LabelDescriptor.create("success", "Whether the command succeeded"),
          LabelDescriptor.create("shell", "Whether the command was called from the nomulus shell"));

  private static final IncrementableMetric commandsCalledCount =
      MetricRegistryImpl.getDefault()
          .newIncrementableMetric(
              "/tools/commands_called",
              "Count of tool commands called",
              "count",
              LABEL_DESCRIPTORS_FOR_COMMANDS);

  private MetricWriter metricWriter = null;

  Map<String, ? extends Class<? extends Command>> commands;
  String programName;

  RegistryCli(
      String programName, ImmutableMap<String, ? extends Class<? extends Command>> commands) {
    this.programName = programName;
    this.commands = commands;

    Security.addProvider(new BouncyCastleProvider());

    component = DaggerRegistryToolComponent.builder()
        .flagsModule(new AppEngineConnectionFlags.FlagsModule(appEngineConnectionFlags))
        .build();
  }

  // The <? extends Class<? extends Command>> wildcard looks a little funny, but is needed so that
  // we can accept maps with value types that are subtypes of Class<? extends Command> rather than
  // literally that type.  For more explanation, see:
  //   http://www.angelikalanger.com/GenericsFAQ/FAQSections/TypeArguments.html#FAQ104
  @Override
  public void run(String[] args) throws Exception {
    boolean inShell = !isFirstUse;
    isFirstUse = false;

    // Create the JCommander instance.
    // If we're in the shell, we don't want to update the RegistryCli's parameters (so we give a
    // dummy object to update)
    JCommander jcommander = new JCommander(inShell ? new Object() : this);
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
    if (!inShell) {
      // If we aren't inside a shell, then we want to add the shell command.
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

    boolean success = false;
    try {
      runCommand(command);
      success = true;
    } catch (AuthModule.LoginRequiredException ex) {
      System.err.println("===================================================================");
      System.err.println("You must login using 'nomulus login' prior to running this command.");
      System.err.println("===================================================================");
    } finally {
      commandsCalledCount.increment(
          programName,
          environment.toString(),
          command.getClass().getSimpleName(),
          String.valueOf(success),
          String.valueOf(inShell));
      exportMetrics();
    }
  }

  @Override
  public void close() {
    exportMetrics();
    if (metricWriter != null) {
      metricWriter = null;
    }
    if (installer != null) {
      installer.uninstall();
      installer = null;
    }
  }

  private AppEngineConnection getConnection() {
    // Get the App Engine connection, advise the user if they are not currently logged in..
    if (connection == null) {
      connection = component.appEngineConnection();
    }
    return connection;
  }

  private void runCommand(Command command) throws Exception {
    injectReflectively(RegistryToolComponent.class, component, command);
    if (metricWriter == null && uploadMetrics) {
      try {
        metricWriter = component.metricWriter();
      } catch (Exception e) {
        System.err.format("Failed to get metricWriter. Got error:\n%s\n\n", e);
        uploadMetrics = false;
      }
    }

    if (command instanceof CommandWithConnection) {
      ((CommandWithConnection) command).setConnection(getConnection());
    }

    // CommandWithRemoteApis need to have the remote api installed to work.
    if (command instanceof CommandWithRemoteApi) {
      if (installer == null) {
        installer = new RemoteApiInstaller();
        RemoteApiOptions options = new RemoteApiOptions();
        options.server(
            getConnection().getServer().getHost(), getConnection().getServer().getPort());
        if (getConnection().isLocalhost()) {
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
    }

    command.run();
  }

  private void exportMetrics() {
    if (metricWriter == null) {
      return;
    }
    try {
      for (Metric<?> metric : MetricRegistryImpl.getDefault().getRegisteredMetrics()) {
        for (MetricPoint<?> point : metric.getTimestampedValues()) {
          metricWriter.write(point);
        }
      }
      metricWriter.flush();
    } catch (IOException e) {
      System.err.format("Failed to export metrics. Got error:\n%s\n\n", e);
      System.err.println("Maybe you need to login? Try calling:");
      System.err.println("  gcloud auth application-default login");
    }
  }

  @VisibleForTesting
  void setEnvironment(RegistryToolEnvironment environment) {
    this.environment = environment;
  }
}
