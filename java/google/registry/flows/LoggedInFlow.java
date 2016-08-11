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

package google.registry.flows;

import static com.google.common.base.Verify.verifyNotNull;
import static com.google.common.collect.Sets.difference;
import static com.google.common.collect.Sets.intersection;
import static google.registry.model.domain.fee.Fee.FEE_EXTENSION_URIS;
import static google.registry.model.registry.Registries.getTlds;
import static google.registry.util.CollectionUtils.nullToEmpty;

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableSet;
import google.registry.flows.EppException.CommandUseErrorException;
import google.registry.flows.EppException.SyntaxErrorException;
import google.registry.flows.EppException.UnimplementedExtensionException;
import google.registry.model.eppcommon.ProtocolDefinition;
import google.registry.model.eppinput.EppInput.CommandExtension;
import google.registry.model.registrar.Registrar;
import google.registry.util.FormattingLogger;
import java.util.Set;

/** A flow that requires being logged in. */
public abstract class LoggedInFlow extends Flow {

  static final FormattingLogger logger = FormattingLogger.getLoggerForCallerClass();

  /**
   * A blacklist of service extension URIs that will cause an error if they are used without being
   * declared on login.
   */
  private static final ImmutableSet<String> UNDECLARED_URIS_BLACKLIST = FEE_EXTENSION_URIS;

  /**
   * The TLDs on which the logged-in registrar is allowed access domains.
   */
  private ImmutableSet<String> allowedTlds;

  protected ImmutableSet<String> getAllowedTlds() {
    return allowedTlds;
  }

  @Override
  public final void initFlow() throws EppException {
    if (getClientId() == null) {
      throw new NotLoggedInException();
    }
    // Validate that the extensions in the input match what this flow expects.
    ImmutableSet<Class<? extends CommandExtension>> extensionClasses = FluentIterable
        .from(eppInput.getCommandWrapper().getExtensions())
        .transform(new Function<CommandExtension, Class<? extends CommandExtension>>() {
            @Override
            public Class<? extends CommandExtension> apply(CommandExtension extension) {
              return extension.getClass();
            }})
        .toSet();
    if (extensionClasses.size() != eppInput.getCommandWrapper().getExtensions().size()) {
      throw new UnsupportedRepeatedExtensionException();
    }
    // Validate that we did not receive any undeclared extensions.
    ImmutableSet<String> extensionUris = FluentIterable
        .from(extensionClasses)
        .transform(new Function<Class<? extends CommandExtension>, String>() {
            @Override
            public String apply(Class<? extends CommandExtension> clazz) {
              return ProtocolDefinition.ServiceExtension.getCommandExtensionUri(clazz);
            }})
        .toSet();
    Set<String> undeclaredUris = difference(
        extensionUris, nullToEmpty(sessionMetadata.getServiceExtensionUris()));
    if (!undeclaredUris.isEmpty()) {
      Set<String> undeclaredUrisThatError = intersection(undeclaredUris, UNDECLARED_URIS_BLACKLIST);
      if (!undeclaredUrisThatError.isEmpty()) {
        throw new UndeclaredServiceExtensionException(undeclaredUrisThatError);
      } else {
        logger.infofmt(
            "Client (%s) is attempting to run flow (%s) without declaring URIs %s on login",
            getClientId(), getClass().getSimpleName(), undeclaredUris);
      }
    }
    if (isSuperuser) {
      allowedTlds = getTlds();
    } else {
      Registrar registrar = verifyNotNull(
          Registrar.loadByClientId(sessionMetadata.getClientId()),
          "Could not load registrar %s", sessionMetadata.getClientId());
      allowedTlds = registrar.getAllowedTlds();
    }
    initLoggedInFlow();
    if (!difference(extensionClasses, getValidRequestExtensions()).isEmpty()) {
      throw new UnimplementedExtensionException();
    }
  }

  @SuppressWarnings("unused")
  protected void initLoggedInFlow() throws EppException {}

  /** Registrar is not logged in. */
  public static class NotLoggedInException extends CommandUseErrorException {
    public NotLoggedInException() {
      super("Registrar is not logged in.");
    }
  }

  /** Unsupported repetition of an extension. */
  static class UnsupportedRepeatedExtensionException extends SyntaxErrorException {
    public UnsupportedRepeatedExtensionException() {
      super("Unsupported repetition of an extension");
    }
  }

  /** Service extension(s) must be declared at login. */
  public static class UndeclaredServiceExtensionException extends CommandUseErrorException {
    public UndeclaredServiceExtensionException(Set<String> undeclaredUris) {
      super(String.format("Service extension(s) must be declared at login: %s",
            Joiner.on(", ").join(undeclaredUris)));
    }
  }
}
