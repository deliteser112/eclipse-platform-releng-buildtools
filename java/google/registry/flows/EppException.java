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

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import google.registry.model.annotations.ExternalMessagingName;
import google.registry.model.eppinput.EppInput.InnerCommand;
import google.registry.model.eppinput.EppInput.ResourceCommandWrapper;
import google.registry.model.eppoutput.Result;
import google.registry.model.eppoutput.Result.Code;
import java.lang.annotation.Documented;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/** Exception used to propagate all failures containing one or more EPP responses. */
public abstract class EppException extends Exception {

  private final Result result;

  /** Create an EppException with a custom message. */
  private EppException(String message) {
    super(message);
    Code code = getClass().getAnnotation(EppResultCode.class).value();
    Preconditions.checkState(!code.isSuccess());
    this.result = Result.create(code, message);
  }

  /** Create an EppException with the default message for this code. */
  private EppException() {
    this(null);
  }

  public Result getResult() {
    return result;
  }

  /** Annotation for associating an EPP Result.Code value with an EppException subclass. */
  @Documented
  @Inherited
  @Retention(RUNTIME)
  @Target(TYPE)
  public @interface EppResultCode {
    /** The Code value associated with this exception. */
    Code value();
  }

  /** Abstract exception class. Do not throw this directly or catch in tests. */
  @EppResultCode(Code.AuthenticationError)
  public abstract static class AuthenticationErrorException extends EppException {
    public AuthenticationErrorException(String message) {
      super(message);
    }
  }

  /** Abstract exception class. Do not throw this directly or catch in tests. */
  @EppResultCode(Code.AuthenticationErrorClosingConnection)
  public abstract static class AuthenticationErrorClosingConnectionException extends EppException {
    public AuthenticationErrorClosingConnectionException(String message) {
      super(message);
    }
  }

  /** Abstract exception class. Do not throw this directly or catch in tests. */
  @EppResultCode(Code.AuthorizationError)
  public abstract static class AuthorizationErrorException extends EppException {
    public AuthorizationErrorException(String message) {
      super(message);
    }
  }

  /** Abstract exception class. Do not throw this directly or catch in tests. */
  @EppResultCode(Code.InvalidAuthorizationInformationError)
  public abstract static class InvalidAuthorizationInformationErrorException extends EppException {
    public InvalidAuthorizationInformationErrorException(String message) {
      super(message);
    }
  }

  /** Abstract exception class. Do not throw this directly or catch in tests. */
  @EppResultCode(Code.CommandUseError)
  public abstract static class CommandUseErrorException extends EppException {
    public CommandUseErrorException(String message) {
      super(message);
    }
  }

  /** Abstract exception class. Do not throw this directly or catch in tests. */
  @EppResultCode(Code.ObjectExists)
  public abstract static class ObjectAlreadyExistsException extends EppException {
    public ObjectAlreadyExistsException(String message) {
      super(message);
    }
  }

  /** Abstract exception class. Do not throw this directly or catch in tests. */
  @EppResultCode(Code.ObjectDoesNotExist)
  public abstract static class ObjectDoesNotExistException extends EppException {
    public ObjectDoesNotExistException(Class<?> type, String id) {
      super(
          String.format(
              "The %s with given ID (%s) doesn't exist.",
              type.isAnnotationPresent(ExternalMessagingName.class)
                  ? type.getAnnotation(ExternalMessagingName.class).value()
                  : "object",
              id));
    }

    public ObjectDoesNotExistException(Class<?> type, ImmutableSet<String> ids) {
      super(
          String.format(
              "The %s with given IDs (%s) don't exist.",
              type.isAnnotationPresent(ExternalMessagingName.class)
                  ? type.getAnnotation(ExternalMessagingName.class).value() + " objects"
                  : "objects",
              Joiner.on(',').join(ids)));
    }
  }

  /** Abstract exception class. Do not throw this directly or catch in tests. */
  @EppResultCode(Code.ObjectPendingTransfer)
  public abstract static class ObjectPendingTransferException extends EppException {
    public ObjectPendingTransferException(String id) {
      super(String.format("Object with given ID (%s) already has a pending transfer.", id));
    }
  }

  /** Abstract exception class. Do not throw this directly or catch in tests. */
  @EppResultCode(Code.ObjectNotPendingTransfer)
  public abstract static class ObjectNotPendingTransferException extends EppException {
    public ObjectNotPendingTransferException(String id) {
      super(String.format("Object with given ID (%s) does not have a pending transfer.", id));
    }
  }

  /** Abstract exception class. Do not throw this directly or catch in tests. */
  @EppResultCode(Code.AssociationProhibitsOperation)
  public abstract static class AssociationProhibitsOperationException extends EppException {
    public AssociationProhibitsOperationException(String message) {
      super(message);
    }
  }

  /** Abstract exception class. Do not throw this directly or catch in tests. */
  @EppResultCode(Code.ParameterValuePolicyError)
  public abstract static class ParameterValuePolicyErrorException extends EppException {
    public ParameterValuePolicyErrorException(String message) {
      super(message);
    }
  }

  /** Abstract exception class. Do not throw this directly or catch in tests. */
  @EppResultCode(Code.ParameterValueRangeError)
  public abstract static class ParameterValueRangeErrorException extends EppException {
    public ParameterValueRangeErrorException(String message) {
      super(message);
    }
  }

  /** Abstract exception class. Do not throw this directly or catch in tests. */
  @EppResultCode(Code.ParameterValueSyntaxError)
  public abstract static class ParameterValueSyntaxErrorException extends EppException {
    public ParameterValueSyntaxErrorException(String message) {
      super(message);
    }
  }

  /** Abstract exception class. Do not throw this directly or catch in tests. */
  @EppResultCode(Code.RequiredParameterMissing)
  public abstract static class RequiredParameterMissingException extends EppException {
    public RequiredParameterMissingException(String message) {
      super(message);
    }
  }

  /** Abstract exception class. Do not throw this directly or catch in tests. */
  @EppResultCode(Code.StatusProhibitsOperation)
  public abstract static class StatusProhibitsOperationException extends EppException {
    public StatusProhibitsOperationException(String message) {
      super(message);
    }
  }

  /** Abstract exception class. Do not throw this directly or catch in tests. */
  @EppResultCode(Code.SyntaxError)
  public abstract static class SyntaxErrorException extends EppException {
    public SyntaxErrorException(String message) {
      super(message);
    }
  }

  /** Specified command is not implemented. */
  @EppResultCode(Code.UnimplementedCommand)
  public static class UnimplementedCommandException extends EppException {
    public UnimplementedCommandException(InnerCommand command) {
      super(String.format(
          "No flow found for %s with extension %s",
          command.getClass().getSimpleName(),
          command instanceof ResourceCommandWrapper
              ? ((ResourceCommandWrapper) command).getResourceCommand().getClass().getSimpleName()
              : null));
    }
  }

  /** Abstract exception class. Do not throw this directly or catch in tests. */
  @EppResultCode(Code.UnimplementedOption)
  public abstract static class UnimplementedOptionException extends EppException {
    public UnimplementedOptionException(String message) {
      super(message);
    }
  }

  /** Specified extension is not implemented. */
  @EppResultCode(Code.UnimplementedExtension)
  public static class UnimplementedExtensionException extends EppException {
    public UnimplementedExtensionException() {
      super("Specified extension is not implemented");
    }
  }

  /** Specified object service is not implemented. */
  @EppResultCode(Code.UnimplementedObjectService)
  public static class UnimplementedObjectServiceException extends EppException {
    public UnimplementedObjectServiceException() {
      super("Specified object service is not implemented");
    }
  }

  /** Specified protocol version is not implemented. */
  @EppResultCode(Code.UnimplementedProtocolVersion)
  public static class UnimplementedProtocolVersionException extends EppException {
    public UnimplementedProtocolVersionException() {
      super("Specified protocol version is not implemented");
    }
  }
}
