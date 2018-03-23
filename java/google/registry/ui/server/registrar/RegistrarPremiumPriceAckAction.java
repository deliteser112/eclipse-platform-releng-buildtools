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

package google.registry.ui.server.registrar;

import static google.registry.model.ofy.ObjectifyService.ofy;
import static google.registry.security.JsonResponseHelper.Status.ERROR;
import static google.registry.security.JsonResponseHelper.Status.SUCCESS;
import static google.registry.ui.server.registrar.RegistrarSettingsAction.ARGS_PARAM;
import static google.registry.ui.server.registrar.RegistrarSettingsAction.OP_PARAM;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import google.registry.config.RegistryConfig.Config;
import google.registry.export.sheet.SyncRegistrarsSheetAction;
import google.registry.model.registrar.Registrar;
import google.registry.request.Action;
import google.registry.request.HttpException.BadRequestException;
import google.registry.request.JsonActionRunner;
import google.registry.request.auth.Auth;
import google.registry.request.auth.AuthResult;
import google.registry.security.JsonResponseHelper;
import google.registry.ui.forms.FormException;
import google.registry.ui.forms.FormFieldException;
import google.registry.ui.server.RegistrarFormFields;
import google.registry.util.CollectionUtils;
import google.registry.util.DiffUtils;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;

/**
 * Action handler for toggling the "Premium Price Ack Required" flag.
 *
 * <p>This class exists to supplement RegistrarSettings, which is supposed to take care of
 * everything but mostly doesn't work.
 */
@Action(
  path = RegistrarPremiumPriceAckAction.PATH,
  method = Action.Method.POST,
  auth = Auth.AUTH_PUBLIC_LOGGED_IN
)
public class RegistrarPremiumPriceAckAction implements Runnable, JsonActionRunner.JsonAction {
  public static final String PATH = "/registrar-premium-price-ack";

  @Inject AuthResult authResult;
  @Inject HttpServletRequest request;
  @Inject JsonActionRunner jsonActionRunner;
  @Inject SendEmailUtils sendEmailUtils;
  @Inject SessionUtils sessionUtils;

  @Inject
  @Config("registrarChangesNotificationEmailAddresses")
  ImmutableList<String> registrarChangesNotificationEmailAddresses;

  @Inject
  RegistrarPremiumPriceAckAction() {}

  @Override
  public void run() {
    jsonActionRunner.run(this);
  }

  @Override
  public Map<String, Object> handleJsonRequest(Map<String, ?> input) {
    if (input == null) {
      throw new BadRequestException("Malformed JSON");
    }

    // Get the registrar
    Registrar initialRegistrar = sessionUtils.getRegistrarForAuthResult(request, authResult, true);

    // Process the operation.  Though originally derived from a CRUD handler, registrar-settings
    // and registrar-premium-price-action really only support read and update.
    String op = Optional.ofNullable((String) input.get(OP_PARAM)).orElse("read");
    @SuppressWarnings("unchecked")
    Map<String, ?> args =
        (Map<String, Object>)
            Optional.<Object>ofNullable(input.get(ARGS_PARAM)).orElse(ImmutableMap.of());
    try {
      switch (op) {
        case "update":
          return update(args, initialRegistrar);
        case "read":
          // Though this class only exists to support update of the premiumPriceAckRequired flag,
          // "read" gives back a javascript representation of the entire registrar object to support
          // the architecture of the console.
          return JsonResponseHelper.create(SUCCESS, "Success", initialRegistrar.toJsonMap());
        default:
          return JsonResponseHelper.create(ERROR, "Unknown or unsupported operation: " + op);
      }
    } catch (FormFieldException e) {
      return JsonResponseHelper.createFormFieldError(e.getMessage(), e.getFieldName());
    } catch (FormException e) {
      return JsonResponseHelper.create(ERROR, e.getMessage());
    }
  }

  Map<String, Object> update(final Map<String, ?> args, final Registrar registrar) {
    final String clientId = sessionUtils.getRegistrarClientId(request);
    return ofy()
        .transact(
            () -> {
              // Verify that the registrar hasn't been changed.
              Optional<Registrar> latest = Registrar.loadByClientId(registrar.getClientId());
              if (!latest.isPresent()
                  || latest.get().getPremiumPriceAckRequired()
                      != registrar.getPremiumPriceAckRequired()) {
                return JsonResponseHelper.create(
                    ERROR,
                    "Premium price ack has changed (or registrar no longer exists). "
                        + "Please reload.");
              }
              Registrar reg = latest.get();

              Optional<Boolean> premiumPriceAckRequired =
                  RegistrarFormFields.PREMIUM_PRICE_ACK_REQUIRED.extractUntyped(args);
              if (premiumPriceAckRequired.isPresent()
                  && premiumPriceAckRequired.get() != reg.getPremiumPriceAckRequired()) {
                Registrar updatedRegistrar =
                    reg.asBuilder()
                        .setPremiumPriceAckRequired(premiumPriceAckRequired.get())
                        .build();
                ofy().save().entity(updatedRegistrar);
                sendExternalUpdatesIfNecessary(reg, updatedRegistrar);
                return JsonResponseHelper.create(
                    SUCCESS, "Saved " + clientId, updatedRegistrar.toJsonMap());
              } else {
                return JsonResponseHelper.create(SUCCESS, "Unchanged " + clientId, reg.toJsonMap());
              }
            });
  }

  private void sendExternalUpdatesIfNecessary(
      Registrar existingRegistrar, Registrar updatedRegistrar) {
    if (registrarChangesNotificationEmailAddresses.isEmpty()) {
      return;
    }

    Map<?, ?> diffs =
        DiffUtils.deepDiff(
            existingRegistrar.toDiffableFieldMap(), updatedRegistrar.toDiffableFieldMap(), true);
    @SuppressWarnings("unchecked")
    Set<String> changedKeys = (Set<String>) diffs.keySet();
    if (CollectionUtils.difference(changedKeys, "lastUpdateTime").isEmpty()) {
      return;
    }
    SyncRegistrarsSheetAction.enqueueBackendTask();
    if (!registrarChangesNotificationEmailAddresses.isEmpty()) {
      sendEmailUtils.sendEmail(
          registrarChangesNotificationEmailAddresses,
          String.format("Registrar %s updated", existingRegistrar.getRegistrarName()),
          "The following changes were made to the registrar:\n"
              + DiffUtils.prettyPrintDiffedMap(diffs, null));
    }
  }
}
