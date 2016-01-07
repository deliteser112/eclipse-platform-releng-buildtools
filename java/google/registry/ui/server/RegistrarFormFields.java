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

package google.registry.ui.server;

import static com.google.common.collect.Range.atLeast;
import static com.google.common.collect.Range.atMost;
import static com.google.common.collect.Range.closed;
import static google.registry.util.DomainNameUtils.canonicalizeDomainName;

import com.google.common.base.Ascii;
import com.google.common.base.Function;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.net.InternetDomainName;
import com.google.re2j.Pattern;
import google.registry.model.registrar.Registrar;
import google.registry.model.registrar.RegistrarAddress;
import google.registry.model.registrar.RegistrarContact;
import google.registry.ui.forms.FormField;
import google.registry.ui.forms.FormFieldException;
import google.registry.ui.forms.FormFields;
import google.registry.util.CidrAddressBlock;
import google.registry.util.X509Utils;
import java.security.cert.CertificateParsingException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;

/** Form fields for validating input for the {@code Registrar} class. */
public final class RegistrarFormFields {

  public static final Pattern BASE64_PATTERN = Pattern.compile("[+/a-zA-Z0-9]*");
  public static final Pattern ASCII_PATTERN = Pattern.compile("[[:ascii:]]*");
  public static final String ASCII_ERROR = "Please only use ASCII-US characters.";

  private static final Function<String, CidrAddressBlock> CIDR_TRANSFORM =
      new Function<String, CidrAddressBlock>() {
        @Nullable
        @Override
        public CidrAddressBlock apply(@Nullable String input) {
          try {
            return input != null ? CidrAddressBlock.create(input) : null;
          } catch (IllegalArgumentException e) {
            throw new FormFieldException("Not a valid CIDR notation IP-address block.", e);
          }
        }};

  private static final Function<String, String> HOSTNAME_TRANSFORM =
      new Function<String, String>() {
        @Nullable
        @Override
        public String apply(@Nullable String input) {
          if (input == null) {
            return null;
          }
          if (!InternetDomainName.isValid(input)) {
            throw new FormFieldException("Not a valid hostname.");
          }
          return canonicalizeDomainName(input);
        }};

  public static final FormField<String, String> NAME_FIELD =
      FormFields.NAME.asBuilderNamed("registrarName")
          .required()
          .build();

  public static final FormField<String, String> EMAIL_ADDRESS_FIELD =
      FormFields.EMAIL.asBuilderNamed("emailAddress")
          .matches(ASCII_PATTERN, ASCII_ERROR)
          .required()
          .build();

  public static final FormField<String, String> ICANN_REFERRAL_EMAIL_FIELD =
      FormFields.EMAIL.asBuilderNamed("icannReferralEmail")
          .matches(ASCII_PATTERN, ASCII_ERROR)
          .required()
          .build();

  public static final FormField<String, String> PHONE_NUMBER_FIELD =
      FormFields.PHONE_NUMBER.asBuilder()
          .matches(ASCII_PATTERN, ASCII_ERROR)
          .build();

  public static final FormField<String, String> FAX_NUMBER_FIELD =
      FormFields.PHONE_NUMBER.asBuilderNamed("faxNumber")
          .matches(ASCII_PATTERN, ASCII_ERROR)
          .build();

  public static final FormField<String, Registrar.State> STATE_FIELD =
      FormField.named("state")
          .emptyToNull()
          .asEnum(Registrar.State.class)
          .build();

  public static final FormField<List<String>, Set<String>> ALLOWED_TLDS_FIELD =
      FormFields.LABEL.asBuilderNamed("allowedTlds")
          .asSet()
          .build();

  public static final FormField<String, String> WHOIS_SERVER_FIELD =
      FormFields.LABEL.asBuilderNamed("whoisServer")
          .transform(HOSTNAME_TRANSFORM)
          .build();

  public static final FormField<Boolean, Boolean> BLOCK_PREMIUM_NAMES_FIELD =
      FormField.named("blockPremiumNames", Boolean.class)
          .build();

  public static final FormField<String, String> DRIVE_FOLDER_ID_FIELD =
      FormFields.XS_NORMALIZED_STRING.asBuilderNamed("driveFolderId")
          .build();

  public static final FormField<String, String> CLIENT_CERTIFICATE_HASH_FIELD =
      FormField.named("clientCertificateHash")
          .emptyToNull()
          .matches(BASE64_PATTERN, "Field must contain a base64 value.")
          .range(closed(1, 255))
          .build();

  private static final FormField<String, String> X509_PEM_CERTIFICATE =
      FormField.named("certificate")
          .emptyToNull()
          .transform(new Function<String, String>() {
            @Nullable
            @Override
            public String apply(@Nullable String input) {
              if (input == null) {
                return null;
              }
              try {
                X509Utils.loadCertificate(input);
              } catch (CertificateParsingException e) {
                throw new FormFieldException("Invalid X.509 PEM certificate");
              }
              return input;
            }})
          .build();

  public static final FormField<String, String> CLIENT_CERTIFICATE_FIELD =
      X509_PEM_CERTIFICATE.asBuilderNamed("clientCertificate").build();

  public static final FormField<String, String> FAILOVER_CLIENT_CERTIFICATE_FIELD =
      X509_PEM_CERTIFICATE.asBuilderNamed("failoverClientCertificate").build();

  public static final FormField<Long, Long> BILLING_IDENTIFIER_FIELD =
      FormField.named("billingIdentifier", Long.class)
          .range(atLeast(1))
          .build();

  public static final FormField<Long, Long> IANA_IDENTIFIER_FIELD =
      FormField.named("ianaIdentifier", Long.class)
          .range(atLeast(1))
          .build();

  public static final FormField<String, String> URL_FIELD =
      FormFields.MIN_TOKEN.asBuilderNamed("url")
          .build();

  public static final FormField<String, String> REFERRAL_URL_FIELD =
      FormFields.MIN_TOKEN.asBuilderNamed("referralUrl")
          .build();

  public static final FormField<List<String>, List<CidrAddressBlock>> IP_ADDRESS_WHITELIST_FIELD =
      FormField.named("ipAddressWhitelist")
          .emptyToNull()
          .transform(CidrAddressBlock.class, CIDR_TRANSFORM)
          .asList()
          .build();

  public static final FormField<String, String> PASSWORD_FIELD =
      FormFields.PASSWORD.asBuilderNamed("password")
          .build();

  public static final FormField<String, String> PHONE_PASSCODE_FIELD =
      FormField.named("phonePasscode")
          .emptyToNull()
          .matches(Registrar.PHONE_PASSCODE_PATTERN)
          .build();

  public static final FormField<String, String> CONTACT_NAME_FIELD =
      FormFields.NAME.asBuilderNamed("name")
          .required()
          .build();

  public static final FormField<String, String> CONTACT_EMAIL_ADDRESS_FIELD =
      FormFields.EMAIL.asBuilderNamed("emailAddress")
          .required()
          .build();

  public static final FormField<Boolean, Boolean> CONTACT_VISIBLE_IN_WHOIS_AS_ADMIN_FIELD =
      FormField.named("visibleInWhoisAsAdmin", Boolean.class)
          .build();

  public static final FormField<Boolean, Boolean> CONTACT_VISIBLE_IN_WHOIS_AS_TECH_FIELD =
      FormField.named("visibleInWhoisAsTech", Boolean.class)
          .build();

  public static final FormField<String, String> CONTACT_PHONE_NUMBER_FIELD =
      FormFields.PHONE_NUMBER.asBuilder()
          .build();

  public static final FormField<String, String> CONTACT_FAX_NUMBER_FIELD =
      FormFields.PHONE_NUMBER.asBuilderNamed("faxNumber")
          .build();

  public static final FormField<String, String> CONTACT_GAE_USER_ID_FIELD =
      FormFields.NAME.asBuilderNamed("gaeUserId")
          .build();

  public static final FormField<String, Set<RegistrarContact.Type>> CONTACT_TYPES =
      FormField.named("types")
          .uppercased()
          .asEnum(RegistrarContact.Type.class)
          .asSet(Splitter.on(',').omitEmptyStrings().trimResults())
          .build();

  public static final Function<Map<String, ?>, RegistrarContact.Builder>
      REGISTRAR_CONTACT_TRANSFORM = new Function<Map<String, ?>, RegistrarContact.Builder>() {
        @Nullable
        @Override
        public RegistrarContact.Builder apply(@Nullable Map<String, ?> args) {
          if (args == null) {
            return null;
          }
          RegistrarContact.Builder builder = new RegistrarContact.Builder();
          for (String name : CONTACT_NAME_FIELD.extractUntyped(args).asSet()) {
            builder.setName(name);
          }
          for (String emailAddress : CONTACT_EMAIL_ADDRESS_FIELD.extractUntyped(args).asSet()) {
            builder.setEmailAddress(emailAddress);
          }
          for (Boolean visible :
                   CONTACT_VISIBLE_IN_WHOIS_AS_ADMIN_FIELD.extractUntyped(args).asSet()) {
            builder.setVisibleInWhoisAsAdmin(visible);
          }
          for (Boolean visible :
                   CONTACT_VISIBLE_IN_WHOIS_AS_TECH_FIELD.extractUntyped(args).asSet()) {
            builder.setVisibleInWhoisAsTech(visible);
          }
          for (String phoneNumber : CONTACT_PHONE_NUMBER_FIELD.extractUntyped(args).asSet()) {
            builder.setPhoneNumber(phoneNumber);
          }
          for (String faxNumber : CONTACT_FAX_NUMBER_FIELD.extractUntyped(args).asSet()) {
            builder.setFaxNumber(faxNumber);
          }
          for (Set<RegistrarContact.Type> types : CONTACT_TYPES.extractUntyped(args).asSet()) {
            builder.setTypes(types);
          }
          for (String gaeUserId : CONTACT_GAE_USER_ID_FIELD.extractUntyped(args).asSet()) {
            builder.setGaeUserId(gaeUserId);
          }
          return builder;
        }};

  public static final FormField<List<Map<String, ?>>, List<RegistrarContact.Builder>>
      CONTACTS_FIELD = FormField.mapNamed("contacts")
          .transform(RegistrarContact.Builder.class, REGISTRAR_CONTACT_TRANSFORM)
          .asList()
          .build();

  public static final FormField<List<String>, List<String>> I18N_STREET_FIELD =
      FormFields.XS_NORMALIZED_STRING.asBuilderNamed("street")
          .range(closed(1, 255))
          .matches(ASCII_PATTERN, ASCII_ERROR)
          .asList()
          .range(closed(1, 3))
          .required()
          .build();

  public static final FormField<List<String>, List<String>> L10N_STREET_FIELD =
      FormFields.XS_NORMALIZED_STRING.asBuilderNamed("street")
          .range(closed(1, 255))
          .asList()
          .range(closed(1, 3))
          .required()
          .build();

  public static final FormField<String, String> I18N_CITY_FIELD =
      FormFields.NAME.asBuilderNamed("city")
          .matches(ASCII_PATTERN, ASCII_ERROR)
          .required()
          .build();

  public static final FormField<String, String> L10N_CITY_FIELD =
      FormFields.NAME.asBuilderNamed("city")
          .required()
          .build();

  public static final FormField<String, String> I18N_STATE_FIELD =
      FormFields.XS_NORMALIZED_STRING.asBuilderNamed("state")
          .range(atMost(255))
          .matches(ASCII_PATTERN, ASCII_ERROR)
          .build();

  public static final FormField<String, String> L10N_STATE_FIELD =
      FormFields.XS_NORMALIZED_STRING.asBuilderNamed("state")
          .range(atMost(255))
          .build();

  public static final FormField<String, String> I18N_ZIP_FIELD =
      FormFields.XS_TOKEN.asBuilderNamed("zip")
          .range(atMost(16))
          .matches(ASCII_PATTERN, ASCII_ERROR)
          .build();

  public static final FormField<String, String> L10N_ZIP_FIELD =
      FormFields.XS_TOKEN.asBuilderNamed("zip")
          .range(atMost(16))
          .build();

  public static final FormField<String, String> COUNTRY_CODE_FIELD =
      FormFields.COUNTRY_CODE.asBuilder()
          .required()
          .build();

  public static final FormField<Map<String, ?>, RegistrarAddress> L10N_ADDRESS_FIELD =
      FormField.mapNamed("localizedAddress")
          .transform(RegistrarAddress.class, newAddressTransform(
              L10N_STREET_FIELD, L10N_CITY_FIELD, L10N_STATE_FIELD, L10N_ZIP_FIELD))
          .build();

  private static Function<Map<String, ?>, RegistrarAddress> newAddressTransform(
      final FormField<List<String>, List<String>> streetField,
      final FormField<String, String> cityField,
      final FormField<String, String> stateField,
      final FormField<String, String> zipField) {
    return new Function<Map<String, ?>, RegistrarAddress>() {
      @Nullable
      @Override
      public RegistrarAddress apply(@Nullable Map<String, ?> args) {
        if (args == null) {
          return null;
        }
        RegistrarAddress.Builder builder = new RegistrarAddress.Builder();
        String countryCode = COUNTRY_CODE_FIELD.extractUntyped(args).get();
        builder.setCountryCode(countryCode);
        for (List<String> streets : streetField.extractUntyped(args).asSet()) {
          builder.setStreet(ImmutableList.copyOf(streets));
        }
        for (String city : cityField.extractUntyped(args).asSet()) {
          builder.setCity(city);
        }
        for (String state : stateField.extractUntyped(args).asSet()) {
          if ("US".equals(countryCode)) {
            state = Ascii.toUpperCase(state);
            if (!StateCode.US_MAP.containsKey(state)) {
              throw new FormFieldException(stateField, "Unknown US state code.");
            }
          }
          builder.setState(state);
        }
        for (String zip : zipField.extractUntyped(args).asSet()) {
          builder.setZip(zip);
        }
        return builder.build();
      }
    };
  }
}
