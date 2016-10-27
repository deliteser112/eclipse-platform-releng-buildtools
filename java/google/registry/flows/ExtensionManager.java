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

package google.registry.flows;

import static com.google.common.collect.Sets.difference;
import static com.google.common.collect.Sets.intersection;
import static google.registry.model.domain.fee.Fee.FEE_EXTENSION_URIS;
import static google.registry.model.eppcommon.ProtocolDefinition.ServiceExtension.getCommandExtensionUri;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableSet;
import google.registry.flows.EppException.CommandUseErrorException;
import google.registry.flows.EppException.SyntaxErrorException;
import google.registry.flows.EppException.UnimplementedExtensionException;
import google.registry.flows.FlowModule.ClientId;
import google.registry.flows.exceptions.OnlyToolCanPassMetadataException;
import google.registry.model.domain.metadata.MetadataExtension;
import google.registry.model.eppinput.EppInput;
import google.registry.model.eppinput.EppInput.CommandExtension;
import google.registry.util.FormattingLogger;
import java.util.Collection;
import java.util.Set;
import javax.inject.Inject;

/**
 * Helper to validate extensions on an EPP command.
 *
 * <p>This class checks that the declared extension URIs in an EPP request as well as the actually
 * supplied extensions in the XML are compatible with the extensions supported by a flow.
 */
public final class ExtensionManager {

  private static final FormattingLogger logger = FormattingLogger.getLoggerForCallerClass();

  /** Blacklist of extension URIs that cause an error if they are used without being declared. */
  private static final ImmutableSet<String> UNDECLARED_URIS_BLACKLIST = FEE_EXTENSION_URIS;

  private final ImmutableSet.Builder<Class<? extends CommandExtension>> implementedBuilder =
      new ImmutableSet.Builder<>();
  private final ImmutableSet.Builder<ImmutableSet<?>> implementedGroupsBuilder =
      new ImmutableSet.Builder<>();

  @Inject EppInput eppInput;
  @Inject SessionMetadata sessionMetadata;
  @Inject @ClientId String clientId;
  @Inject Class<? extends Flow> flowClass;
  @Inject EppRequestSource eppRequestSource;
  @Inject ExtensionManager() {}

  @SafeVarargs
  public final void register(Class<? extends CommandExtension>... extension) {
    implementedBuilder.add(extension);
  }

  public <T extends CommandExtension> void registerAsGroup(
      Collection<Class<? extends T>> extensions) {
    implementedBuilder.addAll(extensions);
    implementedGroupsBuilder.add(ImmutableSet.copyOf(extensions));
  }

  public void validate() throws EppException {
    ImmutableSet.Builder<Class<? extends CommandExtension>> suppliedBuilder =
        new ImmutableSet.Builder<>();
    for (CommandExtension extension : eppInput.getCommandWrapper().getExtensions()) {
      suppliedBuilder.add(extension.getClass());
    }
    ImmutableSet<Class<? extends CommandExtension>> suppliedExtensions = suppliedBuilder.build();
    ImmutableSet<Class<? extends CommandExtension>> implementedExtensions =
        implementedBuilder.build();
    ImmutableSet<ImmutableSet<?>> implementedExtensionGroups =
        implementedGroupsBuilder.build();
    checkForDuplicateExtensions(suppliedExtensions, implementedExtensionGroups);
    checkForUndeclaredExtensions(suppliedExtensions);
    checkForRestrictedExtensions(suppliedExtensions);
    checkForUnimplementedExtensions(suppliedExtensions, implementedExtensions);
  }

  private void checkForDuplicateExtensions(
      ImmutableSet<Class<? extends CommandExtension>> suppliedExtensions,
      ImmutableSet<ImmutableSet<?>> implementedExtensionGroups)
          throws UnsupportedRepeatedExtensionException {
    if (suppliedExtensions.size() < eppInput.getCommandWrapper().getExtensions().size()) {
      throw new UnsupportedRepeatedExtensionException();
    }
    // No more than one extension in an extension group can be present.
    for (ImmutableSet<?> group : implementedExtensionGroups) {
      if (intersection(suppliedExtensions, group).size() > 1) {
        throw new UnsupportedRepeatedExtensionException();
      }
    }
  }

  private void checkForUndeclaredExtensions(
      ImmutableSet<Class<? extends CommandExtension>> suppliedExtensions)
          throws UndeclaredServiceExtensionException {
    ImmutableSet.Builder<String> suppliedUris = new ImmutableSet.Builder<>();
    for (Class<? extends CommandExtension> extension : suppliedExtensions) {
      suppliedUris.add(getCommandExtensionUri(extension));
    }
    Set<String> declaredUris = sessionMetadata.getServiceExtensionUris();
    Set<String> undeclaredUris = difference(suppliedUris.build(), declaredUris);
    if (undeclaredUris.isEmpty()) {
      return;
    }
    Set<String> undeclaredUrisThatError = intersection(undeclaredUris, UNDECLARED_URIS_BLACKLIST);
    if (!undeclaredUrisThatError.isEmpty()) {
      throw new UndeclaredServiceExtensionException(undeclaredUrisThatError);
    }
    logger.infofmt(
        "Client %s is attempting to run %s without declaring URIs %s on login",
        clientId,
        flowClass.getSimpleName(),
        undeclaredUris);
  }

  private void checkForRestrictedExtensions(
      ImmutableSet<Class<? extends CommandExtension>> suppliedExtensions)
          throws OnlyToolCanPassMetadataException {
    if (suppliedExtensions.contains(MetadataExtension.class)
        && !eppRequestSource.equals(EppRequestSource.TOOL)) {
      throw new OnlyToolCanPassMetadataException();
    }
  }

  private void checkForUnimplementedExtensions(
      ImmutableSet<Class<? extends CommandExtension>> suppliedExtensions,
      ImmutableSet<Class<? extends CommandExtension>> implementedExtensions)
          throws UnimplementedExtensionException {
    Set<Class<? extends CommandExtension>> unimplementedExtensions =
        difference(suppliedExtensions, implementedExtensions);
    if (!unimplementedExtensions.isEmpty()) {
      logger.infofmt("Unimplemented extensions: %s", unimplementedExtensions);
      throw new UnimplementedExtensionException();
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
