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

package google.registry.flows;

import static com.google.common.base.Preconditions.checkState;
import static google.registry.xml.ValidationMode.LENIENT;
import static google.registry.xml.ValidationMode.STRICT;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Throwables;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import google.registry.flows.EppException.ParameterValueRangeErrorException;
import google.registry.flows.EppException.ParameterValueSyntaxErrorException;
import google.registry.flows.EppException.SyntaxErrorException;
import google.registry.flows.EppException.UnimplementedProtocolVersionException;
import google.registry.model.EppResourceUtils.InvalidRepoIdException;
import google.registry.model.ImmutableObject;
import google.registry.model.eppinput.EppInput;
import google.registry.model.eppinput.EppInput.WrongProtocolVersionException;
import google.registry.model.eppoutput.EppOutput;
import google.registry.model.host.InetAddressAdapter.IpVersionMismatchException;
import google.registry.model.translators.CurrencyUnitAdapter.UnknownCurrencyException;
import google.registry.util.FormattingLogger;
import google.registry.xml.ValidationMode;
import google.registry.xml.XmlException;
import google.registry.xml.XmlTransformer;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

/** {@link XmlTransformer} for marshalling to and from the Epp model classes.  */
public class EppXmlTransformer  {

  private static final FormattingLogger logger = FormattingLogger.getLoggerForCallerClass();

  // Hardcoded XML schemas, ordered with respect to dependency.
  private static final ImmutableList<String> SCHEMAS = ImmutableList.of(
      "eppcom.xsd",
      "epp.xsd",
      "contact.xsd",
      "host.xsd",
      "domain.xsd",
      "rgp.xsd",
      "secdns.xsd",
      "fee06.xsd",
      "fee11.xsd",
      "fee12.xsd",
      "metadata.xsd",
      "mark.xsd",
      "dsig.xsd",
      "smd.xsd",
      "launch.xsd",
      "allocate.xsd");

  private static final XmlTransformer INPUT_TRANSFORMER =
      new XmlTransformer(SCHEMAS, EppInput.class);

  private static final XmlTransformer OUTPUT_TRANSFORMER =
      new XmlTransformer(SCHEMAS, EppOutput.class);

  public static void validateOutput(String xml) throws XmlException {
    OUTPUT_TRANSFORMER.validate(xml);
  }

  /**
   * Unmarshal bytes into Epp classes.
   *
   * @param clazz type to return, specified as a param to enforce typesafe generics
   * @see <a href="http://errorprone.info/bugpattern/TypeParameterUnusedInFormals">TypeParameterUnusedInFormals</a>
   */
  public static <T> T unmarshal(Class<T> clazz, byte[] bytes) throws EppException {
    try {
      return INPUT_TRANSFORMER.unmarshal(clazz, new ByteArrayInputStream(bytes));
    } catch (XmlException e) {
      // If this XmlException is wrapping a known type find it. If not, it's a syntax error.
      FluentIterable<Throwable> causalChain = FluentIterable.from(Throwables.getCausalChain(e));
      if (!(causalChain.filter(IpVersionMismatchException.class).isEmpty())) {
        throw new IpAddressVersionMismatchException();
      }
      if (!(causalChain.filter(WrongProtocolVersionException.class).isEmpty())) {
        throw new UnimplementedProtocolVersionException();
      }
      if (!(causalChain.filter(InvalidRepoIdException.class).isEmpty())) {
        throw new InvalidRepoIdEppException();
      }
      if (!(causalChain.filter(UnknownCurrencyException.class).isEmpty())) {
        throw new UnknownCurrencyEppException();
      }
      throw new GenericSyntaxErrorException(e.getMessage());
    }
  }

  private static byte[] marshal(
      XmlTransformer transformer,
      ImmutableObject root,
      ValidationMode validation) throws XmlException {
    ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
    transformer.marshal(root, byteArrayOutputStream, UTF_8, validation);
    return byteArrayOutputStream.toByteArray();
  }

  public static byte[] marshal(EppOutput root, ValidationMode validation) throws XmlException {
    return marshal(OUTPUT_TRANSFORMER, root, validation);
  }

  public static byte[] marshalWithLenientRetry(EppOutput eppOutput) {
    checkState(eppOutput != null);
    // We need to marshal to a string instead of writing the response directly to the servlet's
    // response writer, so that partial results don't get written on failure.
    try {
      return EppXmlTransformer.marshal(eppOutput, STRICT);
    } catch (XmlException e) {
      // We failed to marshal with validation. This is very bad, but we can potentially still send
      // back slightly invalid xml, so try again without validation.
      try {
        byte[] lenient = EppXmlTransformer.marshal(eppOutput, LENIENT);
        // Marshaling worked even though the results didn't validate against the schema.
        logger.severe(e, "Result marshaled but did not validate: " + new String(lenient, UTF_8));
        return lenient;
      } catch (XmlException e2) {
        throw new RuntimeException(e2);  // Failing to marshal at all is not recoverable.
      }
    }
  }

  @VisibleForTesting
  public static byte[] marshalInput(EppInput root, ValidationMode validation) throws XmlException {
    return marshal(INPUT_TRANSFORMER, root, validation);
  }

  @VisibleForTesting
  public static void validateInput(String xml) throws XmlException {
    INPUT_TRANSFORMER.validate(xml);
  }

  /** IP address version mismatch. */
  public static class IpAddressVersionMismatchException extends ParameterValueRangeErrorException {
    public IpAddressVersionMismatchException() {
      super("IP adddress version mismatch");
    }
  }

  /** Invalid format for repository id. */
  public static class InvalidRepoIdEppException extends ParameterValueSyntaxErrorException {
    public InvalidRepoIdEppException() {
      super("Invalid format for repository id");
    }
  }

  /** Unknown currency. */
  static class UnknownCurrencyEppException extends ParameterValueRangeErrorException {
    public UnknownCurrencyEppException() {
      super("Unknown currency.");
    }
  }

  /** Generic syntax error that can be thrown by any flow. */
  static class GenericSyntaxErrorException extends SyntaxErrorException {
    public GenericSyntaxErrorException(String message) {
      super(message);
    }
  }
}
