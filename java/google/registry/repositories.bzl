# Copyright 2016 The Nomulus Authors. All Rights Reserved.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

"""External dependencies for Nomulus."""


load("@io_bazel_rules_closure//closure/private:java_import_external.bzl", "java_import_external")

def domain_registry_bazel_check():
  """Checks Bazel version for Nomulus."""
  _check_bazel_version("Nomulus", "0.4.2")

def domain_registry_repositories(
    omit_com_beust_jcommander=False,
    omit_com_braintreepayments_gateway_braintree_java=False,
    omit_com_fasterxml_jackson_core=False,
    omit_com_fasterxml_jackson_core_jackson_annotations=False,
    omit_com_fasterxml_jackson_core_jackson_databind=False,
    omit_com_google_api_client=False,
    omit_com_google_api_client_appengine=False,
    omit_com_google_api_client_servlet=False,
    omit_com_google_apis_google_api_services_admin_directory=False,
    omit_com_google_apis_google_api_services_bigquery=False,
    omit_com_google_apis_google_api_services_dns=False,
    omit_com_google_apis_google_api_services_drive=False,
    omit_com_google_apis_google_api_services_groupssettings=False,
    omit_com_google_apis_google_api_services_monitoring=False,
    omit_com_google_apis_google_api_services_storage=False,
    omit_com_google_appengine_api_1_0_sdk=False,
    omit_com_google_appengine_api_labs=False,
    omit_com_google_appengine_api_stubs=False,
    omit_com_google_appengine_remote_api=False,
    omit_com_google_appengine_testing=False,
    omit_com_google_appengine_tools_appengine_gcs_client=False,
    omit_com_google_appengine_tools_appengine_mapreduce=False,
    omit_com_google_appengine_tools_appengine_pipeline=False,
    omit_com_google_appengine_tools_sdk=False,
    omit_com_google_auto_common=False,
    omit_com_google_auto_factory=False,
    omit_com_google_auto_service=False,
    omit_com_google_auto_value=False,
    omit_com_google_code_findbugs_jsr305=False,
    omit_com_google_dagger=False,
    omit_com_google_dagger_compiler=False,
    omit_com_google_dagger_producers=False,
    omit_com_google_errorprone_error_prone_annotations=False,
    omit_com_google_gdata_core=False,
    omit_com_google_guava=False,
    omit_com_google_guava_testlib=False,
    omit_com_google_http_client=False,
    omit_com_google_http_client_appengine=False,
    omit_com_google_http_client_jackson2=False,
    omit_com_google_oauth_client=False,
    omit_com_google_oauth_client_appengine=False,
    omit_com_google_oauth_client_java6=False,
    omit_com_google_oauth_client_jetty=False,
    omit_com_google_oauth_client_servlet=False,
    omit_com_google_protobuf_java=False,
    omit_com_google_re2j=False,
    omit_com_google_truth=False,
    omit_com_googlecode_charts4j=False,
    omit_com_googlecode_json_simple=False,
    omit_com_ibm_icu_icu4j=False,
    omit_com_jcraft_jzlib=False,
    omit_com_squareup_javawriter=False,
    omit_com_sun_xml_bind_jaxb_core=False,
    omit_com_sun_xml_bind_jaxb_impl=False,
    omit_com_sun_xml_bind_jaxb_xjc=False,
    omit_commons_codec=False,
    omit_commons_logging=False,
    omit_dnsjava=False,
    omit_it_unimi_dsi_fastutil=False,
    omit_javax_activation=False,
    omit_javax_inject=False,
    omit_javax_mail=False,
    omit_javax_servlet_api=False,
    omit_javax_xml_bind_jaxb_api=False,
    omit_joda_time=False,
    omit_junit=False,
    omit_org_apache_ftpserver_core=False,
    omit_org_apache_httpcomponents_httpclient=False,
    omit_org_apache_httpcomponents_httpcore=False,
    omit_org_apache_mina_core=False,
    omit_org_apache_sshd_core=False,
    omit_org_apache_tomcat_servlet_api=False,
    omit_org_bouncycastle_bcpg_jdk15on=False,
    omit_org_bouncycastle_bcpkix_jdk15on=False,
    omit_org_bouncycastle_bcprov_jdk15on=False,
    omit_org_hamcrest_core=False,
    omit_org_hamcrest_library=False,
    omit_org_joda_money=False,
    omit_org_json=False,
    omit_org_mockito_all=False,
    omit_org_mortbay_jetty=False,
    omit_org_mortbay_jetty_servlet_api=False,
    omit_org_mortbay_jetty_util=False,
    omit_org_slf4j_api=False):
  """Imports dependencies for Nomulus."""
  domain_registry_bazel_check()
  if not omit_com_beust_jcommander:
    com_beust_jcommander()
  if not omit_com_braintreepayments_gateway_braintree_java:
    com_braintreepayments_gateway_braintree_java()
  if not omit_com_fasterxml_jackson_core:
    com_fasterxml_jackson_core()
  if not omit_com_fasterxml_jackson_core_jackson_annotations:
    com_fasterxml_jackson_core_jackson_annotations()
  if not omit_com_fasterxml_jackson_core_jackson_databind:
    com_fasterxml_jackson_core_jackson_databind()
  if not omit_com_google_api_client:
    com_google_api_client()
  if not omit_com_google_api_client_appengine:
    com_google_api_client_appengine()
  if not omit_com_google_api_client_servlet:
    com_google_api_client_servlet()
  if not omit_com_google_apis_google_api_services_admin_directory:
    com_google_apis_google_api_services_admin_directory()
  if not omit_com_google_apis_google_api_services_bigquery:
    com_google_apis_google_api_services_bigquery()
  if not omit_com_google_apis_google_api_services_dns:
    com_google_apis_google_api_services_dns()
  if not omit_com_google_apis_google_api_services_drive:
    com_google_apis_google_api_services_drive()
  if not omit_com_google_apis_google_api_services_groupssettings:
    com_google_apis_google_api_services_groupssettings()
  if not omit_com_google_apis_google_api_services_monitoring:
    com_google_apis_google_api_services_monitoring()
  if not omit_com_google_apis_google_api_services_storage:
    com_google_apis_google_api_services_storage()
  if not omit_com_google_appengine_api_1_0_sdk:
    com_google_appengine_api_1_0_sdk()
  if not omit_com_google_appengine_api_labs:
    com_google_appengine_api_labs()
  if not omit_com_google_appengine_api_stubs:
    com_google_appengine_api_stubs()
  if not omit_com_google_appengine_remote_api:
    com_google_appengine_remote_api()
  if not omit_com_google_appengine_testing:
    com_google_appengine_testing()
  if not omit_com_google_appengine_tools_appengine_gcs_client:
    com_google_appengine_tools_appengine_gcs_client()
  if not omit_com_google_appengine_tools_appengine_mapreduce:
    com_google_appengine_tools_appengine_mapreduce()
  if not omit_com_google_appengine_tools_appengine_pipeline:
    com_google_appengine_tools_appengine_pipeline()
  if not omit_com_google_appengine_tools_sdk:
    com_google_appengine_tools_sdk()
  if not omit_com_google_auto_common:
    com_google_auto_common()
  if not omit_com_google_auto_factory:
    com_google_auto_factory()
  if not omit_com_google_auto_service:
    com_google_auto_service()
  if not omit_com_google_auto_value:
    com_google_auto_value()
  if not omit_com_google_code_findbugs_jsr305:
    com_google_code_findbugs_jsr305()
  if not omit_com_google_dagger:
    com_google_dagger()
  if not omit_com_google_dagger_compiler:
    com_google_dagger_compiler()
  if not omit_com_google_dagger_producers:
    com_google_dagger_producers()
  if not omit_com_google_errorprone_error_prone_annotations:
    com_google_errorprone_error_prone_annotations()
  if not omit_com_google_gdata_core:
    com_google_gdata_core()
  if not omit_com_google_guava:
    com_google_guava()
  if not omit_com_google_guava_testlib:
    com_google_guava_testlib()
  if not omit_com_google_http_client:
    com_google_http_client()
  if not omit_com_google_http_client_appengine:
    com_google_http_client_appengine()
  if not omit_com_google_http_client_jackson2:
    com_google_http_client_jackson2()
  if not omit_com_google_oauth_client:
    com_google_oauth_client()
  if not omit_com_google_oauth_client_appengine:
    com_google_oauth_client_appengine()
  if not omit_com_google_oauth_client_java6:
    com_google_oauth_client_java6()
  if not omit_com_google_oauth_client_jetty:
    com_google_oauth_client_jetty()
  if not omit_com_google_oauth_client_servlet:
    com_google_oauth_client_servlet()
  if not omit_com_google_protobuf_java:
    com_google_protobuf_java()
  if not omit_com_google_re2j:
    com_google_re2j()
  if not omit_com_google_truth:
    com_google_truth()
  if not omit_com_googlecode_charts4j:
    com_googlecode_charts4j()
  if not omit_com_googlecode_json_simple:
    com_googlecode_json_simple()
  if not omit_com_ibm_icu_icu4j:
    com_ibm_icu_icu4j()
  if not omit_com_jcraft_jzlib:
    com_jcraft_jzlib()
  if not omit_com_squareup_javawriter:
    com_squareup_javawriter()
  if not omit_com_sun_xml_bind_jaxb_core:
    com_sun_xml_bind_jaxb_core()
  if not omit_com_sun_xml_bind_jaxb_impl:
    com_sun_xml_bind_jaxb_impl()
  if not omit_com_sun_xml_bind_jaxb_xjc:
    com_sun_xml_bind_jaxb_xjc()
  if not omit_commons_codec:
    commons_codec()
  if not omit_commons_logging:
    commons_logging()
  if not omit_dnsjava:
    dnsjava()
  if not omit_it_unimi_dsi_fastutil:
    it_unimi_dsi_fastutil()
  if not omit_javax_activation:
    javax_activation()
  if not omit_javax_inject:
    javax_inject()
  if not omit_javax_mail:
    javax_mail()
  if not omit_javax_servlet_api:
    javax_servlet_api()
  if not omit_javax_xml_bind_jaxb_api:
    javax_xml_bind_jaxb_api()
  if not omit_joda_time:
    joda_time()
  if not omit_junit:
    junit()
  if not omit_org_apache_ftpserver_core:
    org_apache_ftpserver_core()
  if not omit_org_apache_httpcomponents_httpclient:
    org_apache_httpcomponents_httpclient()
  if not omit_org_apache_httpcomponents_httpcore:
    org_apache_httpcomponents_httpcore()
  if not omit_org_apache_mina_core:
    org_apache_mina_core()
  if not omit_org_apache_sshd_core:
    org_apache_sshd_core()
  if not omit_org_apache_tomcat_servlet_api:
    org_apache_tomcat_servlet_api()
  if not omit_org_bouncycastle_bcpg_jdk15on:
    org_bouncycastle_bcpg_jdk15on()
  if not omit_org_bouncycastle_bcpkix_jdk15on:
    org_bouncycastle_bcpkix_jdk15on()
  if not omit_org_bouncycastle_bcprov_jdk15on:
    org_bouncycastle_bcprov_jdk15on()
  if not omit_org_hamcrest_core:
    org_hamcrest_core()
  if not omit_org_hamcrest_library:
    org_hamcrest_library()
  if not omit_org_joda_money:
    org_joda_money()
  if not omit_org_json:
    org_json()
  if not omit_org_mockito_all:
    org_mockito_all()
  if not omit_org_mortbay_jetty:
    org_mortbay_jetty()
  if not omit_org_mortbay_jetty_servlet_api:
    org_mortbay_jetty_servlet_api()
  if not omit_org_mortbay_jetty_util:
    org_mortbay_jetty_util()
  if not omit_org_slf4j_api:
    org_slf4j_api()

def com_beust_jcommander():
  java_import_external(
      name = "com_beust_jcommander",
      jar_sha256 = "a7313fcfde070930e40ec79edf3c5948cf34e4f0d25cb3a09f9963d8bdd84113",
      jar_urls = [
          "http://domain-registry-maven.storage.googleapis.com/repo1.maven.org/maven2/com/beust/jcommander/1.48/jcommander-1.48.jar",
          "http://maven.ibiblio.org/maven2/com/beust/jcommander/1.48/jcommander-1.48.jar",
          "http://repo1.maven.org/maven2/com/beust/jcommander/1.48/jcommander-1.48.jar",
      ],
      licenses = ["notice"],  # The Apache Software License, Version 2.0
  )

def com_braintreepayments_gateway_braintree_java():
  java_import_external(
      name = "com_braintreepayments_gateway_braintree_java",
      jar_sha256 = "e6fa51822d05334971d60a8353d4bfcab155b9639d9d8d3d052fe75ead534dd9",
      jar_urls = [
          "http://domain-registry-maven.storage.googleapis.com/repo1.maven.org/maven2/com/braintreepayments/gateway/braintree-java/2.54.0/braintree-java-2.54.0.jar",
          "http://maven.ibiblio.org/maven2/com/braintreepayments/gateway/braintree-java/2.54.0/braintree-java-2.54.0.jar",
          "http://repo1.maven.org/maven2/com/braintreepayments/gateway/braintree-java/2.54.0/braintree-java-2.54.0.jar",
      ],
      licenses = ["notice"],  # MIT license
  )

def com_fasterxml_jackson_core():
  java_import_external(
      name = "com_fasterxml_jackson_core",
      jar_sha256 = "85b48d80d0ff36eecdc61ab57fe211a266b9fc326d5e172764d150e29fc99e21",
      jar_urls = [
          "http://domain-registry-maven.storage.googleapis.com/repo1.maven.org/maven2/com/fasterxml/jackson/core/jackson-core/2.8.5/jackson-core-2.8.5.jar",
          "http://repo1.maven.org/maven2/com/fasterxml/jackson/core/jackson-core/2.8.5/jackson-core-2.8.5.jar",
          "http://maven.ibiblio.org/maven2/com/fasterxml/jackson/core/jackson-core/2.8.5/jackson-core-2.8.5.jar",
      ],
      licenses = ["notice"],  # The Apache Software License, Version 2.0
  )

def com_fasterxml_jackson_core_jackson_annotations():
  java_import_external(
      name = "com_fasterxml_jackson_core_jackson_annotations",
      jar_sha256 = "e61b7343aceeb6ecda291d4ef133cd3e765f178c631c357ffd081abab7f15db8",
      jar_urls = [
          "http://domain-registry-maven.storage.googleapis.com/repo1.maven.org/maven2/com/fasterxml/jackson/core/jackson-annotations/2.8.0/jackson-annotations-2.8.0.jar",
          "http://repo1.maven.org/maven2/com/fasterxml/jackson/core/jackson-annotations/2.8.0/jackson-annotations-2.8.0.jar",
          "http://maven.ibiblio.org/maven2/com/fasterxml/jackson/core/jackson-annotations/2.8.0/jackson-annotations-2.8.0.jar",
      ],
      licenses = ["notice"],  # The Apache Software License, Version 2.0
  )

def com_fasterxml_jackson_core_jackson_databind():
  java_import_external(
      name = "com_fasterxml_jackson_core_jackson_databind",
      jar_sha256 = "2ed1d9d9ad732093bbe9f2c23f7d143c35c092ccc48f1754f23d031f8de2436e",
      jar_urls = [
          "http://domain-registry-maven.storage.googleapis.com/repo1.maven.org/maven2/com/fasterxml/jackson/core/jackson-databind/2.8.5/jackson-databind-2.8.5.jar",
          "http://repo1.maven.org/maven2/com/fasterxml/jackson/core/jackson-databind/2.8.5/jackson-databind-2.8.5.jar",
          "http://maven.ibiblio.org/maven2/com/fasterxml/jackson/core/jackson-databind/2.8.5/jackson-databind-2.8.5.jar",
      ],
      licenses = ["notice"],  # The Apache Software License, Version 2.0
      deps = [
          "@com_fasterxml_jackson_core_jackson_annotations",
          "@com_fasterxml_jackson_core",
      ],
  )

def com_google_api_client():
  java_import_external(
      name = "com_google_api_client",
      jar_sha256 = "47c625c83a8cf97b8bbdff2acde923ff8fd3174e62aabcfc5d1b86692594ffba",
      jar_urls = [
          "http://domain-registry-maven.storage.googleapis.com/repo1.maven.org/maven2/com/google/api-client/google-api-client/1.22.0/google-api-client-1.22.0.jar",
          "http://repo1.maven.org/maven2/com/google/api-client/google-api-client/1.22.0/google-api-client-1.22.0.jar",
          "http://maven.ibiblio.org/maven2/com/google/api-client/google-api-client/1.22.0/google-api-client-1.22.0.jar",
      ],
      licenses = ["notice"],  # The Apache Software License, Version 2.0
      deps = [
          "@com_google_oauth_client",
          "@com_google_http_client_jackson2",
          "@commons_codec",
          "@com_google_guava",
      ],
  )

def com_google_api_client_appengine():
  java_import_external(
      name = "com_google_api_client_appengine",
      jar_sha256 = "3b6f69bea556806e96ca6d443440968f848058c7956d945abea625e22b1d3fac",
      jar_urls = [
          "http://domain-registry-maven.storage.googleapis.com/repo1.maven.org/maven2/com/google/api-client/google-api-client-appengine/1.22.0/google-api-client-appengine-1.22.0.jar",
          "http://maven.ibiblio.org/maven2/com/google/api-client/google-api-client-appengine/1.22.0/google-api-client-appengine-1.22.0.jar",
          "http://repo1.maven.org/maven2/com/google/api-client/google-api-client-appengine/1.22.0/google-api-client-appengine-1.22.0.jar",
      ],
      licenses = ["notice"],  # The Apache Software License, Version 2.0
      deps = [
          "@com_google_appengine_api_1_0_sdk",
          "@com_google_oauth_client_appengine",
          "@com_google_api_client",
          "@com_google_api_client_servlet",
          "@com_google_http_client_appengine",
      ]
  )

def com_google_api_client_servlet():
  java_import_external(
      name = "com_google_api_client_servlet",
      jar_sha256 = "66cf62e2ecd7ae73c3dbf4713850e8ff5e5bb0bcaac61243bb0034fa28b2681c",
      jar_urls = [
          "http://domain-registry-maven.storage.googleapis.com/repo1.maven.org/maven2/com/google/api-client/google-api-client-servlet/1.22.0/google-api-client-servlet-1.22.0.jar",
          "http://repo1.maven.org/maven2/com/google/api-client/google-api-client-servlet/1.22.0/google-api-client-servlet-1.22.0.jar",
          "http://maven.ibiblio.org/maven2/com/google/api-client/google-api-client-servlet/1.22.0/google-api-client-servlet-1.22.0.jar",
      ],
      licenses = ["notice"],  # The Apache Software License, Version 2.0
      deps = [
          "@com_google_oauth_client_servlet",
          "@com_google_api_client",
          "@javax_servlet_api",
      ]
  )

def com_google_apis_google_api_services_admin_directory():
  java_import_external(
      name = "com_google_apis_google_api_services_admin_directory",
      jar_sha256 = "c1455436b318d16d665ed0ff305b03eb177542d8a74cfb8b7c1e9bfed5227640",
      jar_urls = [
          "http://domain-registry-maven.storage.googleapis.com/repo1.maven.org/maven2/com/google/apis/google-api-services-admin-directory/directory_v1-rev72-1.22.0/google-api-services-admin-directory-directory_v1-rev72-1.22.0.jar",
          "http://repo1.maven.org/maven2/com/google/apis/google-api-services-admin-directory/directory_v1-rev72-1.22.0/google-api-services-admin-directory-directory_v1-rev72-1.22.0.jar",
          "http://maven.ibiblio.org/maven2/com/google/apis/google-api-services-admin-directory/directory_v1-rev72-1.22.0/google-api-services-admin-directory-directory_v1-rev72-1.22.0.jar",
      ],
      licenses = ["notice"],  # The Apache Software License, Version 2.0
      deps = ["@com_google_api_client"],
  )

def com_google_apis_google_api_services_bigquery():
  java_import_external(
      name = "com_google_apis_google_api_services_bigquery",
      jar_sha256 = "a8659f00301b34292878f288bc3604c5763d51cb6b82c956a46bbf5b46d8f3f0",
      jar_urls = [
          "http://domain-registry-maven.storage.googleapis.com/repo1.maven.org/maven2/com/google/apis/google-api-services-bigquery/v2-rev325-1.22.0/google-api-services-bigquery-v2-rev325-1.22.0.jar",
          "http://repo1.maven.org/maven2/com/google/apis/google-api-services-bigquery/v2-rev325-1.22.0/google-api-services-bigquery-v2-rev325-1.22.0.jar",
          "http://maven.ibiblio.org/maven2/com/google/apis/google-api-services-bigquery/v2-rev325-1.22.0/google-api-services-bigquery-v2-rev325-1.22.0.jar",
      ],
      licenses = ["notice"],  # The Apache Software License, Version 2.0
      deps = ["@com_google_api_client"],
  )

def com_google_apis_google_api_services_dns():
  java_import_external(
      name = "com_google_apis_google_api_services_dns",
      jar_sha256 = "70fd3a33fe59a033176feeee8e4e1e380a3939468fc953852843c4f874e7d087",
      jar_urls = [
          "http://domain-registry-maven.storage.googleapis.com/repo1.maven.org/maven2/com/google/apis/google-api-services-dns/v2beta1-rev6-1.22.0/google-api-services-dns-v2beta1-rev6-1.22.0.jar",
          "http://repo1.maven.org/maven2/com/google/apis/google-api-services-dns/v2beta1-rev6-1.22.0/google-api-services-dns-v2beta1-rev6-1.22.0.jar",
          "http://maven.ibiblio.org/maven2/com/google/apis/google-api-services-dns/v2beta1-rev6-1.22.0/google-api-services-dns-v2beta1-rev6-1.22.0.jar",
      ],
      licenses = ["notice"],  # The Apache Software License, Version 2.0
      deps = ["@com_google_api_client"],
  )

def com_google_apis_google_api_services_drive():
  java_import_external(
      name = "com_google_apis_google_api_services_drive",
      jar_sha256 = "8894c1ac3bbf723c493c83aca1f786cd6acd8a833a3f1c31394bcc484b6916e4",
      jar_urls = [
          "http://domain-registry-maven.storage.googleapis.com/repo1.maven.org/maven2/com/google/apis/google-api-services-drive/v2-rev160-1.19.1/google-api-services-drive-v2-rev160-1.19.1.jar",
          "http://maven.ibiblio.org/maven2/com/google/apis/google-api-services-drive/v2-rev160-1.19.1/google-api-services-drive-v2-rev160-1.19.1.jar",
          "http://repo1.maven.org/maven2/com/google/apis/google-api-services-drive/v2-rev160-1.19.1/google-api-services-drive-v2-rev160-1.19.1.jar",
      ],
      licenses = ["notice"],  # The Apache Software License, Version 2.0
      deps = ["@com_google_api_client"],
  )

def com_google_apis_google_api_services_groupssettings():
  java_import_external(
      name = "com_google_apis_google_api_services_groupssettings",
      jar_sha256 = "bb06f971362b1b78842f7ec71ae28adf3b31132c97d9cf786645bb2468d56b46",
      jar_urls = [
          "http://domain-registry-maven.storage.googleapis.com/repo1.maven.org/maven2/com/google/apis/google-api-services-groupssettings/v1-rev60-1.22.0/google-api-services-groupssettings-v1-rev60-1.22.0.jar",
          "http://maven.ibiblio.org/maven2/com/google/apis/google-api-services-groupssettings/v1-rev60-1.22.0/google-api-services-groupssettings-v1-rev60-1.22.0.jar",
          "http://repo1.maven.org/maven2/com/google/apis/google-api-services-groupssettings/v1-rev60-1.22.0/google-api-services-groupssettings-v1-rev60-1.22.0.jar",
      ],
      licenses = ["notice"],  # The Apache Software License, Version 2.0
      deps = ["@com_google_api_client"],
  )

def com_google_apis_google_api_services_monitoring():
  java_import_external(
      name = "com_google_apis_google_api_services_monitoring",
      jar_sha256 = "8943d11779280ba90e57245d657368a94bb8474e260ad528e31f1e4ee081e1d9",
      jar_urls = [
          "http://domain-registry-maven.storage.googleapis.com/repo1.maven.org/maven2/com/google/apis/google-api-services-monitoring/v3-rev11-1.22.0/google-api-services-monitoring-v3-rev11-1.22.0.jar",
          "http://maven.ibiblio.org/maven2/com/google/apis/google-api-services-monitoring/v3-rev11-1.22.0/google-api-services-monitoring-v3-rev11-1.22.0.jar",
          "http://repo1.maven.org/maven2/com/google/apis/google-api-services-monitoring/v3-rev11-1.22.0/google-api-services-monitoring-v3-rev11-1.22.0.jar",
      ],
      licenses = ["notice"],  # The Apache Software License, Version 2.0
      deps = ["@com_google_api_client"],
  )

def com_google_apis_google_api_services_storage():
  java_import_external(
      name = "com_google_apis_google_api_services_storage",
      jar_sha256 = "3a6c857e409a4398ada630124ca52222582beba04943c3cd7c5c76aee0854fcf",
      jar_urls = [
          "http://domain-registry-maven.storage.googleapis.com/repo1.maven.org/maven2/com/google/apis/google-api-services-storage/v1-rev86-1.22.0/google-api-services-storage-v1-rev86-1.22.0.jar",
          "http://repo1.maven.org/maven2/com/google/apis/google-api-services-storage/v1-rev86-1.22.0/google-api-services-storage-v1-rev86-1.22.0.jar",
          "http://maven.ibiblio.org/maven2/com/google/apis/google-api-services-storage/v1-rev86-1.22.0/google-api-services-storage-v1-rev86-1.22.0.jar",
      ],
      licenses = ["notice"],  # The Apache Software License, Version 2.0
      deps = ["@com_google_api_client"],
  )

def com_google_appengine_api_1_0_sdk():
  java_import_external(
      name = "com_google_appengine_api_1_0_sdk",
      jar_sha256 = "c151aee2a0eb5ede2f051861f420da023d437e7b275b50a9f2fe42c893ba693b",
      jar_urls = [
          "http://domain-registry-maven.storage.googleapis.com/repo1.maven.org/maven2/com/google/appengine/appengine-api-1.0-sdk/1.9.48/appengine-api-1.0-sdk-1.9.48.jar",
          "http://repo1.maven.org/maven2/com/google/appengine/appengine-api-1.0-sdk/1.9.48/appengine-api-1.0-sdk-1.9.48.jar",
      ],
      # Google App Engine Terms of Service: https://cloud.google.com/terms/
      # + Shaded Jackson: Apache 2.0
      # + Shaded Antlr: BSD
      # + Shaded Apache Commons Codec: Apache 2.0
      # + Shaded Joda Time: Apache 2.0
      # + Shaded Apache Geronimo: Apache 2.0
      licenses = ["notice"],
      neverlink = True,
      generated_linkable_rule_name = "link",
      extra_build_file_content = "\n".join([
          "java_import(",
          "    name = \"testonly\",",
          "    jars = [\"appengine-api-1.0-sdk-1.9.48.jar\"],",
          "    testonly = True,",
          ")",
      ]),
  )

def com_google_appengine_api_labs():
  java_import_external(
      name = "com_google_appengine_api_labs",
      jar_sha256 = "adb9e0e778ca0911c08c7715e0769c487a5e56d7a984a4e17e26044b5c1fc1be",
      jar_urls = [
          "http://domain-registry-maven.storage.googleapis.com/repo1.maven.org/maven2/com/google/appengine/appengine-api-labs/1.9.48/appengine-api-labs-1.9.48.jar",
          "http://repo1.maven.org/maven2/com/google/appengine/appengine-api-labs/1.9.48/appengine-api-labs-1.9.48.jar",
      ],
      licenses = ["permissive"],  # Google App Engine Terms of Service: https://cloud.google.com/terms/
  )

def com_google_appengine_api_stubs():
  java_import_external(
      name = "com_google_appengine_api_stubs",
      jar_sha256 = "49c5a9228477c2ed589f62e2430a4c029464adf166a8b50c260ad35171b0d763",
      jar_urls = [
          "http://domain-registry-maven.storage.googleapis.com/repo1.maven.org/maven2/com/google/appengine/appengine-api-stubs/1.9.48/appengine-api-stubs-1.9.48.jar",
          "http://repo1.maven.org/maven2/com/google/appengine/appengine-api-stubs/1.9.48/appengine-api-stubs-1.9.48.jar",
      ],
      licenses = ["permissive"],  # Google App Engine Terms of Service: https://cloud.google.com/terms/
  )

def com_google_appengine_remote_api():
  java_import_external(
      name = "com_google_appengine_remote_api",
      jar_sha256 = "6ea6dc3b529038ea6b37e855cd1cd7612f6640feaeb0eec842d4e6d85e1fd052",
      jar_urls = [
          "http://domain-registry-maven.storage.googleapis.com/repo1.maven.org/maven2/com/google/appengine/appengine-remote-api/1.9.48/appengine-remote-api-1.9.48.jar",
          "http://repo1.maven.org/maven2/com/google/appengine/appengine-remote-api/1.9.48/appengine-remote-api-1.9.48.jar",
      ],
      licenses = ["permissive"],  # Google App Engine Terms of Service: https://cloud.google.com/terms/
      neverlink = True,
      generated_linkable_rule_name = "link",
  )

def com_google_appengine_testing():
  java_import_external(
      name = "com_google_appengine_testing",
      jar_sha256 = "ec59a2cc5b502c57b46a5ffff9eb11c3f9b64ead390318fc0bb063cade27c9f4",
      jar_urls = [
          "http://domain-registry-maven.storage.googleapis.com/repo1.maven.org/maven2/com/google/appengine/appengine-testing/1.9.48/appengine-testing-1.9.48.jar",
          "http://repo1.maven.org/maven2/com/google/appengine/appengine-testing/1.9.48/appengine-testing-1.9.48.jar",
      ],
      licenses = ["permissive"],  # Google App Engine Terms of Service: https://cloud.google.com/terms/
      testonly_ = True,
      deps = [
          "@com_google_appengine_api_1_0_sdk//:testonly",
          "@com_google_appengine_api_labs",
          "@com_google_appengine_api_stubs",
      ],
  )

def com_google_appengine_tools_appengine_gcs_client():
  java_import_external(
      name = "com_google_appengine_tools_appengine_gcs_client",
      jar_sha256 = "99daa975012ac2f1509ecff1e70c9ec1e5eb435a499d3f381c88bb944739c7d8",
      jar_urls = [
          "http://domain-registry-maven.storage.googleapis.com/repo1.maven.org/maven2/com/google/appengine/tools/appengine-gcs-client/0.6/appengine-gcs-client-0.6.jar",
          "http://maven.ibiblio.org/maven2/com/google/appengine/tools/appengine-gcs-client/0.6/appengine-gcs-client-0.6.jar",
          "http://repo1.maven.org/maven2/com/google/appengine/tools/appengine-gcs-client/0.6/appengine-gcs-client-0.6.jar",
      ],
      licenses = ["notice"],  # The Apache Software License, Version 2.0
      deps = [
          "@com_google_appengine_api_1_0_sdk",
          "@com_google_guava",
          "@joda_time",
          "@com_google_apis_google_api_services_storage",
          "@com_google_api_client_appengine",
          "@com_google_http_client",
          "@com_google_http_client_jackson2",
      ]
  )

def com_google_appengine_tools_appengine_mapreduce():
  java_import_external(
      name = "com_google_appengine_tools_appengine_mapreduce",
      jar_sha256 = "5247f29ad94f422511fb7321a11ffb47bdd6156b00b9b6d7221a4f8f00c4a750",
      jar_urls = [
          "http://domain-registry-maven.storage.googleapis.com/repo1.maven.org/maven2/com/google/appengine/tools/appengine-mapreduce/0.8.5/appengine-mapreduce-0.8.5.jar",
          "http://maven.ibiblio.org/maven2/com/google/appengine/tools/appengine-mapreduce/0.8.5/appengine-mapreduce-0.8.5.jar",
          "http://repo1.maven.org/maven2/com/google/appengine/tools/appengine-mapreduce/0.8.5/appengine-mapreduce-0.8.5.jar",
      ],
      licenses = ["notice"],  # The Apache Software License, Version 2.0
      deps = [
          "@com_google_appengine_api_1_0_sdk",
          "@javax_servlet_api",
          "@com_google_appengine_tools_appengine_gcs_client",
          "@com_google_appengine_tools_appengine_pipeline",
          "@com_googlecode_charts4j",
          "@org_json",
          "@com_google_protobuf_java",
          "@com_google_guava",
          "@joda_time",
          "@it_unimi_dsi_fastutil",
          "@com_google_apis_google_api_services_bigquery",
          "@com_google_api_client",
          "@com_google_api_client_appengine",
          "@com_google_http_client",
          "@com_google_http_client_jackson2",
          "@com_fasterxml_jackson_core_jackson_databind",
          "@com_fasterxml_jackson_core",
      ]
  )

def com_google_appengine_tools_appengine_pipeline():
  java_import_external(
      name = "com_google_appengine_tools_appengine_pipeline",
      jar_sha256 = "61da36f73843545db9eaf403112ba14f36a1fa6e685557cff56ce0083d0a7b97",
      jar_urls = [
          "http://domain-registry-maven.storage.googleapis.com/repo1.maven.org/maven2/com/google/appengine/tools/appengine-pipeline/0.2.13/appengine-pipeline-0.2.13.jar",
          "http://repo1.maven.org/maven2/com/google/appengine/tools/appengine-pipeline/0.2.13/appengine-pipeline-0.2.13.jar",
          "http://maven.ibiblio.org/maven2/com/google/appengine/tools/appengine-pipeline/0.2.13/appengine-pipeline-0.2.13.jar",
      ],
      licenses = ["notice"],  # The Apache Software License, Version 2.0
      deps = [
          "@org_json",
          "@com_google_appengine_api_1_0_sdk",
          "@com_google_guava",
          "@javax_servlet_api",
          "@com_google_appengine_tools_appengine_gcs_client",
      ]
  )

def com_google_appengine_tools_sdk():
  java_import_external(
      name = "com_google_appengine_tools_sdk",
      jar_sha256 = "16acae92eed3a227dda8aae8b456e4058f3c0d30c18859ce0ec872c43101bbdc",
      jar_urls = [
          "http://domain-registry-maven.storage.googleapis.com/repo1.maven.org/maven2/com/google/appengine/appengine-tools-sdk/1.9.48/appengine-tools-sdk-1.9.48.jar",
          "http://repo1.maven.org/maven2/com/google/appengine/appengine-tools-sdk/1.9.48/appengine-tools-sdk-1.9.48.jar",
      ],
      licenses = ["permissive"],  # Google App Engine Terms of Service: https://cloud.google.com/terms/
  )

def com_google_auto_common():
  java_import_external(
      name = "com_google_auto_common",
      jar_sha256 = "eee75e0d1b1b8f31584dcbe25e7c30752545001b46673d007d468d75cf6b2c52",
      jar_urls = [
          "http://domain-registry-maven.storage.googleapis.com/repo1.maven.org/maven2/com/google/auto/auto-common/0.7/auto-common-0.7.jar",
          "http://repo1.maven.org/maven2/com/google/auto/auto-common/0.7/auto-common-0.7.jar",
          "http://maven.ibiblio.org/maven2/com/google/auto/auto-common/0.7/auto-common-0.7.jar",
      ],
      licenses = ["notice"],  # Apache 2.0
      deps = ["@com_google_guava"],
  )

def com_google_auto_factory():
  java_import_external(
      name = "com_google_auto_factory",
      licenses = ["notice"],  # Apache 2.0
      jar_sha256 = "a038e409da90b9e065ec537cce2375b0bb0b07548dca0f9448671b0befb83439",
      jar_urls = [
          "http://domain-registry-maven.storage.googleapis.com/repo1.maven.org/maven2/com/google/auto/factory/auto-factory/1.0-beta3/auto-factory-1.0-beta3.jar",
          "http://maven.ibiblio.org/maven2/com/google/auto/factory/auto-factory/1.0-beta3/auto-factory-1.0-beta3.jar",
          "http://repo1.maven.org/maven2/com/google/auto/factory/auto-factory/1.0-beta3/auto-factory-1.0-beta3.jar",
      ],
      # Auto Factory ships its annotations, runtime, and processor in the same
      # jar. The generated code must link against this jar at runtime. So our
      # goal is to introduce as little bloat as possible.The only class we need
      # at runtime is com.google.auto.factory.internal.Preconditions. So we're
      # not going to specify the deps of this jar as part of the java_import().
      generated_rule_name = "jar",
      extra_build_file_content = "\n".join([
          "java_library(",
          "    name = \"processor\",",
          "    exports = [\":jar\"],",
          "    runtime_deps = [",
          "        \"@com_google_auto_common\",",
          "        \"@com_google_guava\",",
          "        \"@com_squareup_javawriter\",",
          "        \"@javax_inject\",",
          "    ],",
          ")",
          "",
          "java_plugin(",
          "    name = \"AutoFactoryProcessor\",",
          # TODO(jart): https://github.com/bazelbuild/bazel/issues/2286
          # "    output_licenses = [\"unencumbered\"],",
          "    processor_class = \"com.google.auto.factory.processor.AutoFactoryProcessor\",",
          "    tags = [\"annotation=com.google.auto.factory.AutoFactory;genclass=${package}.${outerclasses}@{className|${classname}Factory}\"],",
          "    deps = [\":processor\"],",
          ")",
          "",
          "java_library(",
          "    name = \"com_google_auto_factory\",",
          "    exported_plugins = [\":AutoFactoryProcessor\"],",
          "    exports = [",
          "        \":jar\",",
          "        \"@com_google_code_findbugs_jsr305\",",
          "        \"@javax_inject\",",
          "    ],",
          ")",
      ]),
  )

def com_google_auto_service():
  java_import_external(
      name = "com_google_auto_service",
      jar_sha256 = "46808c92276b4c19e05781963432e6ab3e920b305c0e6df621517d3624a35d71",
      jar_urls = [
          "http://domain-registry-maven.storage.googleapis.com/repo1.maven.org/maven2/com/google/auto/service/auto-service/1.0-rc2/auto-service-1.0-rc2.jar",
          "http://repo1.maven.org/maven2/com/google/auto/service/auto-service/1.0-rc2/auto-service-1.0-rc2.jar",
          "http://maven.ibiblio.org/maven2/com/google/auto/service/auto-service/1.0-rc2/auto-service-1.0-rc2.jar",
      ],
      licenses = ["notice"],  # Apache 2.0
      neverlink = True,
      generated_rule_name = "compile",
      generated_linkable_rule_name = "processor",
      deps = [
          "@com_google_auto_common",
          "@com_google_guava",
      ],
      extra_build_file_content = "\n".join([
          "java_plugin(",
          "    name = \"AutoServiceProcessor\",",
          # TODO(jart): https://github.com/bazelbuild/bazel/issues/2286
          # "    output_licenses = [\"unencumbered\"],",
          "    processor_class = \"com.google.auto.service.processor.AutoServiceProcessor\",",
          "    deps = [\":processor\"],",
          ")",
          "",
          "java_library(",
          "    name = \"com_google_auto_service\",",
          "    exported_plugins = [\":AutoServiceProcessor\"],",
          "    exports = [\":compile\"],",
          ")",
      ]),
  )

def com_google_auto_value():
  java_import_external(
      name = "com_google_auto_value",
      jar_sha256 = "ea26f99150825f61752efc8784739cf50dd25d7956774573f8cdc3b948b23086",
      jar_urls = [
          "http://domain-registry-maven.storage.googleapis.com/repo1.maven.org/maven2/com/google/auto/value/auto-value/1.4-rc2/auto-value-1.4-rc2.jar",
          "http://repo1.maven.org/maven2/com/google/auto/value/auto-value/1.4-rc2/auto-value-1.4-rc2.jar",
      ],
      licenses = ["notice"],  # Apache 2.0
      neverlink = True,
      generated_rule_name = "compile",
      generated_linkable_rule_name = "processor",
      deps = [
          "@com_google_auto_common",
          "@com_google_code_findbugs_jsr305",
          "@com_google_guava",
      ],
      extra_build_file_content = "\n".join([
          "java_plugin(",
          "    name = \"AutoAnnotationProcessor\",",
          # TODO(jart): https://github.com/bazelbuild/bazel/issues/2286
          # "    output_licenses = [\"unencumbered\"],",
          "    processor_class = \"com.google.auto.value.processor.AutoAnnotationProcessor\",",
          "    tags = [\"annotation=com.google.auto.value.AutoAnnotation;genclass=${package}.AutoAnnotation_${outerclasses}${classname}_${methodname}\"],",
          "    deps = [\":processor\"],",
          ")",
          "",
          "java_plugin(",
          "    name = \"AutoValueProcessor\",",
          # TODO(jart): https://github.com/bazelbuild/bazel/issues/2286
          # "    output_licenses = [\"unencumbered\"],",
          "    processor_class = \"com.google.auto.value.processor.AutoValueProcessor\",",
          "    tags = [\"annotation=com.google.auto.value.AutoValue;genclass=${package}.AutoValue_${outerclasses}${classname}\"],",
          "    deps = [\":processor\"],",
          ")",
          "",
          "java_library(",
          "    name = \"com_google_auto_value\",",
          "    exported_plugins = [",
          "        \":AutoAnnotationProcessor\",",
          "        \":AutoValueProcessor\",",
          "    ],",
          "    exports = [",
          "        \":compile\",",
          "        \"@com_google_code_findbugs_jsr305\",",
          "    ],",
          ")",
      ]),
  )

def com_google_code_findbugs_jsr305():
  java_import_external(
      name = "com_google_code_findbugs_jsr305",
      jar_sha256 = "905721a0eea90a81534abb7ee6ef4ea2e5e645fa1def0a5cd88402df1b46c9ed",
      jar_urls = [
          "http://domain-registry-maven.storage.googleapis.com/repo1.maven.org/maven2/com/google/code/findbugs/jsr305/1.3.9/jsr305-1.3.9.jar",
          "http://repo1.maven.org/maven2/com/google/code/findbugs/jsr305/1.3.9/jsr305-1.3.9.jar",
          "http://maven.ibiblio.org/maven2/com/google/code/findbugs/jsr305/1.3.9/jsr305-1.3.9.jar",
      ],
      licenses = ["notice"],  # The Apache Software License, Version 2.0
  )

def com_google_dagger():
  java_import_external(
      name = "com_google_dagger",
      jar_sha256 = "5070e1dff5c551a4908ba7b93125c0243de2a688aed3d2f475357d86d9d7c0ad",
      jar_urls = [
          "http://domain-registry-maven.storage.googleapis.com/repo1.maven.org/maven2/com/google/dagger/dagger/2.8/dagger-2.8.jar",
          "http://repo1.maven.org/maven2/com/google/dagger/dagger/2.8/dagger-2.8.jar",
          "http://maven.ibiblio.org/maven2/com/google/dagger/dagger/2.8/dagger-2.8.jar",
      ],
      licenses = ["notice"],  # Apache 2.0
      deps = ["@javax_inject"],
      generated_rule_name = "runtime",
      extra_build_file_content = "\n".join([
          "java_library(",
          "    name = \"com_google_dagger\",",
          "    exported_plugins = [\"@com_google_dagger_compiler//:ComponentProcessor\"],",
          "    exports = [",
          "        \":runtime\",",
          "        \"@javax_inject\",",
          "    ],",
          ")",
      ]),
  )

def com_google_dagger_compiler():
  java_import_external(
      name = "com_google_dagger_compiler",
      jar_sha256 = "7b2686f94907868c5364e9965601ffe2f020ba4af1849ad9b57dad5fe3fa6242",
      jar_urls = [
          "http://domain-registry-maven.storage.googleapis.com/repo1.maven.org/maven2/com/google/dagger/dagger-compiler/2.8/dagger-compiler-2.8.jar",
          "http://maven.ibiblio.org/maven2/com/google/dagger/dagger-compiler/2.8/dagger-compiler-2.8.jar",
          "http://repo1.maven.org/maven2/com/google/dagger/dagger-compiler/2.8/dagger-compiler-2.8.jar",
      ],
      licenses = ["notice"],  # Apache 2.0
      deps = [
          "@com_google_code_findbugs_jsr305",
          "@com_google_dagger//:runtime",
          "@com_google_dagger_producers//:runtime",
          "@com_google_guava",
      ],
      extra_build_file_content = "\n".join([
          "java_plugin(",
          "    name = \"ComponentProcessor\",",
          # TODO(jart): https://github.com/bazelbuild/bazel/issues/2286
          # "    output_licenses = [\"unencumbered\"],",
          "    processor_class = \"dagger.internal.codegen.ComponentProcessor\",",
          "    tags = [",
          "        \"annotation=dagger.Component;genclass=${package}.Dagger${outerclasses}${classname}\",",
          "        \"annotation=dagger.producers.ProductionComponent;genclass=${package}.Dagger${outerclasses}${classname}\",",
          "    ],",
          "    deps = [\":com_google_dagger_compiler\"],",
          ")",
      ]),
  )

def com_google_dagger_producers():
  java_import_external(
      name = "com_google_dagger_producers",
      jar_sha256 = "1e4043e85f67de381d19e22c7932aaf7ff1611091be7e1aaae93f2c37f331cf2",
      jar_urls = [
          "http://domain-registry-maven.storage.googleapis.com/repo1.maven.org/maven2/com/google/dagger/dagger-producers/2.8/dagger-producers-2.8.jar",
          "http://maven.ibiblio.org/maven2/com/google/dagger/dagger-producers/2.8/dagger-producers-2.8.jar",
          "http://repo1.maven.org/maven2/com/google/dagger/dagger-producers/2.8/dagger-producers-2.8.jar",
      ],
      licenses = ["notice"],  # Apache 2.0
      deps = [
          "@com_google_dagger//:runtime",
          "@com_google_guava",
      ],
      generated_rule_name = "runtime",
      extra_build_file_content = "\n".join([
          "java_library(",
          "    name = \"com_google_dagger\",",
          "    exported_plugins = [\"@com_google_dagger_compiler//:ComponentProcessor\"],",
          "    exports = [",
          "        \":runtime\",",
          "        \"@javax_inject\",",
          "    ],",
          ")",
      ]),
  )

def com_google_errorprone_error_prone_annotations():
  java_import_external(
      name = "com_google_errorprone_error_prone_annotations",
      jar_sha256 = "e7749ffdf03fb8ebe08a727ea205acb301c8791da837fee211b99b04f9d79c46",
      jar_urls = [
          "http://domain-registry-maven.storage.googleapis.com/repo1.maven.org/maven2/com/google/errorprone/error_prone_annotations/2.0.15/error_prone_annotations-2.0.15.jar",
          "http://repo1.maven.org/maven2/com/google/errorprone/error_prone_annotations/2.0.15/error_prone_annotations-2.0.15.jar",
      ],
      licenses = ["notice"],  # Apache 2.0
  )

def com_google_gdata_core():
  java_import_external(
      name = "com_google_gdata_core",
      jar_sha256 = "671fb963dd0bc767a69c7e4a74c07cf8dad3912bd40d37e600cc2b06d7a42dea",
      jar_urls = [
          "http://domain-registry-maven.storage.googleapis.com/repo1.maven.org/maven2/com/google/gdata/core/1.47.1/core-1.47.1.jar",
          "http://repo1.maven.org/maven2/com/google/gdata/core/1.47.1/core-1.47.1.jar",
          "http://maven.ibiblio.org/maven2/com/google/gdata/core/1.47.1/core-1.47.1.jar",
      ],
      licenses = ["notice"],  # The Apache Software License, Version 2.0
      deps = [
          "@com_google_guava",
          "@com_google_oauth_client_jetty",
          "@com_google_code_findbugs_jsr305",
          "@javax_mail",
      ],
  )

def com_google_guava():
  java_import_external(
      name = "com_google_guava",
      jar_sha256 = "36a666e3b71ae7f0f0dca23654b67e086e6c93d192f60ba5dfd5519db6c288c8",
      jar_urls = [
          "http://domain-registry-maven.storage.googleapis.com/repo1.maven.org/maven2/com/google/guava/guava/20.0/guava-20.0.jar",
          "http://repo1.maven.org/maven2/com/google/guava/guava/20.0/guava-20.0.jar",
          "http://maven.ibiblio.org/maven2/com/google/guava/guava/20.0/guava-20.0.jar",
      ],
      licenses = ["notice"],  # The Apache Software License, Version 2.0
      deps = [
          "@com_google_errorprone_error_prone_annotations",
      ],
  )

def com_google_guava_testlib():
  java_import_external(
      name = "com_google_guava_testlib",
      jar_sha256 = "a9f52f328ac024e420c8995a107ea0dbef3fc169ddf97b3426e634f28d6b3663",
      jar_urls = [
          "http://domain-registry-maven.storage.googleapis.com/repo1.maven.org/maven2/com/google/guava/guava-testlib/20.0/guava-testlib-20.0.jar",
          "http://maven.ibiblio.org/maven2/com/google/guava/guava-testlib/20.0/guava-testlib-20.0.jar",
          "http://repo1.maven.org/maven2/com/google/guava/guava-testlib/20.0/guava-testlib-20.0.jar",
      ],
      licenses = ["notice"],  # The Apache Software License, Version 2.0
      testonly_ = True,
      deps = [
          "@com_google_code_findbugs_jsr305",
          "@com_google_errorprone_error_prone_annotations",
          "@com_google_guava",
          "@junit",
      ],
  )

def com_google_http_client():
  java_import_external(
      name = "com_google_http_client",
      jar_sha256 = "f88ffa329ac52fb4f2ff0eb877ef7318423ac9b791a107f886ed5c7a00e77e11",
      jar_urls = [
          "http://domain-registry-maven.storage.googleapis.com/repo1.maven.org/maven2/com/google/http-client/google-http-client/1.22.0/google-http-client-1.22.0.jar",
          "http://repo1.maven.org/maven2/com/google/http-client/google-http-client/1.22.0/google-http-client-1.22.0.jar",
          "http://maven.ibiblio.org/maven2/com/google/http-client/google-http-client/1.22.0/google-http-client-1.22.0.jar",
      ],
      licenses = ["notice"],  # The Apache Software License, Version 2.0
      deps = [
          "@com_google_code_findbugs_jsr305",
          "@com_google_guava",
          "@org_apache_httpcomponents_httpclient",
          "@commons_codec",
      ],
  )

def com_google_http_client_appengine():
  java_import_external(
      name = "com_google_http_client_appengine",
      jar_sha256 = "44a92b5caf023cc526fdbc94d5259457988a9a557a5ce570cbfbafad0fd32420",
      jar_urls = [
          "http://domain-registry-maven.storage.googleapis.com/repo1.maven.org/maven2/com/google/http-client/google-http-client-appengine/1.22.0/google-http-client-appengine-1.22.0.jar",
          "http://maven.ibiblio.org/maven2/com/google/http-client/google-http-client-appengine/1.22.0/google-http-client-appengine-1.22.0.jar",
          "http://repo1.maven.org/maven2/com/google/http-client/google-http-client-appengine/1.22.0/google-http-client-appengine-1.22.0.jar",
      ],
      licenses = ["notice"],  # The Apache Software License, Version 2.0
      deps = [
          "@com_google_http_client",
          "@com_google_appengine_api_1_0_sdk",
      ],
  )

def com_google_http_client_jackson2():
  java_import_external(
      name = "com_google_http_client_jackson2",
      jar_sha256 = "45b1e34b2dcef5cb496ef25a1223d19cf102b8c2ea4abf96491631b2faf4611c",
      jar_urls = [
          "http://domain-registry-maven.storage.googleapis.com/repo1.maven.org/maven2/com/google/http-client/google-http-client-jackson2/1.22.0/google-http-client-jackson2-1.22.0.jar",
          "http://maven.ibiblio.org/maven2/com/google/http-client/google-http-client-jackson2/1.22.0/google-http-client-jackson2-1.22.0.jar",
          "http://repo1.maven.org/maven2/com/google/http-client/google-http-client-jackson2/1.22.0/google-http-client-jackson2-1.22.0.jar",
      ],
      licenses = ["notice"],  # The Apache Software License, Version 2.0
      deps = [
          "@com_google_http_client",
          "@com_fasterxml_jackson_core",
      ],
  )

def com_google_oauth_client():
  java_import_external(
      name = "com_google_oauth_client",
      jar_sha256 = "a4c56168b3e042105d68cf136e40e74f6e27f63ed0a948df966b332678e19022",
      jar_urls = [
          "http://domain-registry-maven.storage.googleapis.com/repo1.maven.org/maven2/com/google/oauth-client/google-oauth-client/1.22.0/google-oauth-client-1.22.0.jar",
          "http://repo1.maven.org/maven2/com/google/oauth-client/google-oauth-client/1.22.0/google-oauth-client-1.22.0.jar",
          "http://maven.ibiblio.org/maven2/com/google/oauth-client/google-oauth-client/1.22.0/google-oauth-client-1.22.0.jar",
      ],
      licenses = ["notice"],  # The Apache Software License, Version 2.0
      deps = [
          "@com_google_http_client",
          "@com_google_code_findbugs_jsr305",
      ],
  )

def com_google_oauth_client_appengine():
  java_import_external(
      name = "com_google_oauth_client_appengine",
      jar_sha256 = "9d78ad610143fffea773e5fb9214fa1a6889c6d08be627d0b45e189f613f7877",
      jar_urls = [
          "http://domain-registry-maven.storage.googleapis.com/repo1.maven.org/maven2/com/google/oauth-client/google-oauth-client-appengine/1.22.0/google-oauth-client-appengine-1.22.0.jar",
          "http://repo1.maven.org/maven2/com/google/oauth-client/google-oauth-client-appengine/1.22.0/google-oauth-client-appengine-1.22.0.jar",
          "http://maven.ibiblio.org/maven2/com/google/oauth-client/google-oauth-client-appengine/1.22.0/google-oauth-client-appengine-1.22.0.jar",
      ],
      licenses = ["notice"],  # The Apache Software License, Version 2.0
      deps = [
          "@com_google_http_client_appengine",
          "@com_google_oauth_client",
          "@com_google_oauth_client_servlet",
          "@com_google_appengine_api_1_0_sdk",
          "@javax_servlet_api",
      ],
  )

def com_google_oauth_client_java6():
  java_import_external(
      name = "com_google_oauth_client_java6",
      jar_sha256 = "a1d405cb3318bf844fd9cecd4a22b9bbcfc34a0a437a3eb3e141adac6796a0c5",
      jar_urls = [
          "http://domain-registry-maven.storage.googleapis.com/repo1.maven.org/maven2/com/google/oauth-client/google-oauth-client-java6/1.11.0-beta/google-oauth-client-java6-1.11.0-beta.jar",
          "http://repo1.maven.org/maven2/com/google/oauth-client/google-oauth-client-java6/1.11.0-beta/google-oauth-client-java6-1.11.0-beta.jar",
          "http://maven.ibiblio.org/maven2/com/google/oauth-client/google-oauth-client-java6/1.11.0-beta/google-oauth-client-java6-1.11.0-beta.jar",
      ],
      licenses = ["notice"],  # The Apache Software License, Version 2.0
      deps = ["@com_google_oauth_client"],
  )

def com_google_oauth_client_jetty():
  java_import_external(
      name = "com_google_oauth_client_jetty",
      jar_sha256 = "b96bcb1924003370f5d59d799d70c62bf1bd7ca9dace09ec1e42457d7028ba29",
      jar_urls = [
          "http://domain-registry-maven.storage.googleapis.com/repo1.maven.org/maven2/com/google/oauth-client/google-oauth-client-jetty/1.11.0-beta/google-oauth-client-jetty-1.11.0-beta.jar",
          "http://maven.ibiblio.org/maven2/com/google/oauth-client/google-oauth-client-jetty/1.11.0-beta/google-oauth-client-jetty-1.11.0-beta.jar",
          "http://repo1.maven.org/maven2/com/google/oauth-client/google-oauth-client-jetty/1.11.0-beta/google-oauth-client-jetty-1.11.0-beta.jar",
      ],
      licenses = ["notice"],  # The Apache Software License, Version 2.0
      deps = [
          "@com_google_oauth_client_java6",
          "@org_mortbay_jetty",
      ],
  )

def com_google_oauth_client_servlet():
  java_import_external(
      name = "com_google_oauth_client_servlet",
      jar_sha256 = "6956ac1bd055ebf0b1592a005f029a551af3039584a32fe03854f0d2f6f022ef",
      jar_urls = [
          "http://domain-registry-maven.storage.googleapis.com/repo1.maven.org/maven2/com/google/oauth-client/google-oauth-client-servlet/1.22.0/google-oauth-client-servlet-1.22.0.jar",
          "http://repo1.maven.org/maven2/com/google/oauth-client/google-oauth-client-servlet/1.22.0/google-oauth-client-servlet-1.22.0.jar",
          "http://maven.ibiblio.org/maven2/com/google/oauth-client/google-oauth-client-servlet/1.22.0/google-oauth-client-servlet-1.22.0.jar",
      ],
      licenses = ["notice"],  # The Apache Software License, Version 2.0
      deps = [
          "@com_google_oauth_client",
          "@javax_servlet_api",
      ],
  )

def com_google_protobuf_java():
  java_import_external(
      name = "com_google_protobuf_java",
      jar_sha256 = "5636b013420f19c0a5342dab6de33956e20a40b06681d2cf021266d6ef478c6e",
      jar_urls = [
          "http://domain-registry-maven.storage.googleapis.com/repo1.maven.org/maven2/com/google/protobuf/protobuf-java/2.6.0/protobuf-java-2.6.0.jar",
          "http://repo1.maven.org/maven2/com/google/protobuf/protobuf-java/2.6.0/protobuf-java-2.6.0.jar",
          "http://maven.ibiblio.org/maven2/com/google/protobuf/protobuf-java/2.6.0/protobuf-java-2.6.0.jar",
      ],
      # New BSD license
      # http://www.opensource.org/licenses/bsd-license.php
      # The Apache Software License, Version 2.0
      # http://www.apache.org/licenses/LICENSE-2.0.txt
      licenses = ["notice"],
  )

def com_google_re2j():
  java_import_external(
      name = "com_google_re2j",
      jar_sha256 = "24ada84d1b5de584e3e84b06f0c7dd562cee6eafe8dea8083bd8eb123823bbe7",
      jar_urls = [
          "http://domain-registry-maven.storage.googleapis.com/repo1.maven.org/maven2/com/google/re2j/re2j/1.1/re2j-1.1.jar",
          "http://maven.ibiblio.org/maven2/com/google/re2j/re2j/1.1/re2j-1.1.jar",
          "http://repo1.maven.org/maven2/com/google/re2j/re2j/1.1/re2j-1.1.jar",
      ],
      licenses = ["notice"],  # The Go license
  )

def com_google_truth():
  java_import_external(
      name = "com_google_truth",
      jar_sha256 = "f4a4c5e69c4994b750ce3ee80adbb2b7150fe39f057d7dff89832c8ca3af512e",
      jar_urls = [
          "http://domain-registry-maven.storage.googleapis.com/repo1.maven.org/maven2/com/google/truth/truth/0.30/truth-0.30.jar",
          "http://repo1.maven.org/maven2/com/google/truth/truth/0.30/truth-0.30.jar",
          "http://maven.ibiblio.org/maven2/com/google/truth/truth/0.30/truth-0.30.jar",
      ],
      licenses = ["notice"],  # The Apache Software License, Version 2.0
      testonly_ = True,
      deps = [
          "@com_google_guava",
          "@junit",
          "@com_google_auto_value",
          "@com_google_errorprone_error_prone_annotations",
      ],
  )

def com_googlecode_charts4j():
  java_import_external(
      name = "com_googlecode_charts4j",
      jar_sha256 = "6ac5ed6a390a585fecaed95d3ce6b96a8cfe95adb1e76bd93376e7e37249020a",
      jar_urls = [
          "http://domain-registry-maven.storage.googleapis.com/repo1.maven.org/maven2/com/googlecode/charts4j/charts4j/1.3/charts4j-1.3.jar",
          "http://repo1.maven.org/maven2/com/googlecode/charts4j/charts4j/1.3/charts4j-1.3.jar",
          "http://maven.ibiblio.org/maven2/com/googlecode/charts4j/charts4j/1.3/charts4j-1.3.jar",
      ],
      licenses = ["notice"],  # The MIT License
  )

def com_googlecode_json_simple():
  java_import_external(
      name = "com_googlecode_json_simple",
      jar_sha256 = "4e69696892b88b41c55d49ab2fdcc21eead92bf54acc588c0050596c3b75199c",
      jar_urls = [
          "http://domain-registry-maven.storage.googleapis.com/repo1.maven.org/maven2/com/googlecode/json-simple/json-simple/1.1.1/json-simple-1.1.1.jar",
          "http://repo1.maven.org/maven2/com/googlecode/json-simple/json-simple/1.1.1/json-simple-1.1.1.jar",
          "http://maven.ibiblio.org/maven2/com/googlecode/json-simple/json-simple/1.1.1/json-simple-1.1.1.jar",
      ],
      licenses = ["notice"],  # The Apache Software License, Version 2.0
  )

def com_ibm_icu_icu4j():
  java_import_external(
      name = "com_ibm_icu_icu4j",
      jar_sha256 = "759d89ed2f8c6a6b627ab954be5913fbdc464f62254a513294e52260f28591ee",
      jar_urls = [
          "http://domain-registry-maven.storage.googleapis.com/repo1.maven.org/maven2/com/ibm/icu/icu4j/57.1/icu4j-57.1.jar",
          "http://repo1.maven.org/maven2/com/ibm/icu/icu4j/57.1/icu4j-57.1.jar",
          "http://maven.ibiblio.org/maven2/com/ibm/icu/icu4j/57.1/icu4j-57.1.jar",
      ],
      licenses = ["notice"],  # ICU License
  )

def com_jcraft_jzlib():
  java_import_external(
      name = "com_jcraft_jzlib",
      jar_sha256 = "89b1360f407381bf61fde411019d8cbd009ebb10cff715f3669017a031027560",
      jar_urls = [
          "http://domain-registry-maven.storage.googleapis.com/repo1.maven.org/maven2/com/jcraft/jzlib/1.1.3/jzlib-1.1.3.jar",
          "http://repo1.maven.org/maven2/com/jcraft/jzlib/1.1.3/jzlib-1.1.3.jar",
          "http://maven.ibiblio.org/maven2/com/jcraft/jzlib/1.1.3/jzlib-1.1.3.jar",
      ],
      licenses = ["notice"],  # BSD
  )

def com_squareup_javawriter():
  java_import_external(
      name = "com_squareup_javawriter",
      jar_sha256 = "39b054910ff212d4379129a89070fb7dbb1f341371c925e9e99904f154a22d93",
      jar_urls = [
          "http://domain-registry-maven.storage.googleapis.com/repo1.maven.org/maven2/com/squareup/javawriter/2.5.1/javawriter-2.5.1.jar",
          "http://maven.ibiblio.org/maven2/com/squareup/javawriter/2.5.1/javawriter-2.5.1.jar",
          "http://repo1.maven.org/maven2/com/squareup/javawriter/2.5.1/javawriter-2.5.1.jar",
      ],
      licenses = ["notice"],  # Apache 2.0
  )

def com_sun_xml_bind_jaxb_core():
  java_import_external(
      name = "com_sun_xml_bind_jaxb_core",
      jar_sha256 = "b13da0c655a3d590a2a945553648c407e6347648c9f7a3f811b7b3a8a1974baa",
      jar_urls = [
          "http://domain-registry-maven.storage.googleapis.com/repo1.maven.org/maven2/com/sun/xml/bind/jaxb-core/2.2.11/jaxb-core-2.2.11.jar",
          "http://maven.ibiblio.org/maven2/com/sun/xml/bind/jaxb-core/2.2.11/jaxb-core-2.2.11.jar",
          "http://repo1.maven.org/maven2/com/sun/xml/bind/jaxb-core/2.2.11/jaxb-core-2.2.11.jar",
      ],
      licenses = ["reciprocal"],  # CDDL 1.1 or GPLv2 (We choo-choo-choose the CDDL)
  )

def com_sun_xml_bind_jaxb_impl():
  java_import_external(
      name = "com_sun_xml_bind_jaxb_impl",
      jar_sha256 = "f91793a96f185a2fc004c86a37086f060985854ce6b19935e03c4de51e3201d2",
      jar_urls = [
          "http://domain-registry-maven.storage.googleapis.com/repo1.maven.org/maven2/com/sun/xml/bind/jaxb-impl/2.2.11/jaxb-impl-2.2.11.jar",
          "http://repo1.maven.org/maven2/com/sun/xml/bind/jaxb-impl/2.2.11/jaxb-impl-2.2.11.jar",
          "http://maven.ibiblio.org/maven2/com/sun/xml/bind/jaxb-impl/2.2.11/jaxb-impl-2.2.11.jar",
      ],
      licenses = ["reciprocal"],  # CDDL 1.1 or GPLv2 (We choo-choo-choose the CDDL)
  )

def com_sun_xml_bind_jaxb_xjc():
  java_import_external(
      name = "com_sun_xml_bind_jaxb_xjc",
      jar_sha256 = "d602e9fdc488512ee062a4cd2306aaa32f1c28bf0d0ae6024b2d93a2c8d62bdb",
      jar_urls = [
          "http://domain-registry-maven.storage.googleapis.com/repo1.maven.org/maven2/com/sun/xml/bind/jaxb-xjc/2.2.11/jaxb-xjc-2.2.11.jar",
          "http://repo1.maven.org/maven2/com/sun/xml/bind/jaxb-xjc/2.2.11/jaxb-xjc-2.2.11.jar",
          "http://maven.ibiblio.org/maven2/com/sun/xml/bind/jaxb-xjc/2.2.11/jaxb-xjc-2.2.11.jar",
      ],
      licenses = ["reciprocal"],  # CDDL 1.1 or GPLv2 (We choo-choo-choose the CDDL)
      extra_build_file_content = "\n".join([
          "java_binary(",
          "    name = \"XJCFacade\",",
          "    main_class = \"com.sun.tools.xjc.XJCFacade\",",
          "    runtime_deps = [",
          "        \":com_sun_xml_bind_jaxb_xjc\",",
          "        \"@javax_xml_bind_jaxb_api\",",
          "        \"@com_sun_xml_bind_jaxb_core\",",
          "        \"@com_sun_xml_bind_jaxb_impl\",",
          "    ],",
          ")",
      ]),
  )

def commons_codec():
  java_import_external(
      name = "commons_codec",
      jar_sha256 = "54b34e941b8e1414bd3e40d736efd3481772dc26db3296f6aa45cec9f6203d86",
      jar_urls = [
          "http://domain-registry-maven.storage.googleapis.com/repo1.maven.org/maven2/commons-codec/commons-codec/1.6/commons-codec-1.6.jar",
          "http://maven.ibiblio.org/maven2/commons-codec/commons-codec/1.6/commons-codec-1.6.jar",
          "http://repo1.maven.org/maven2/commons-codec/commons-codec/1.6/commons-codec-1.6.jar",
      ],
      licenses = ["notice"],  # The Apache Software License, Version 2.0
  )

def commons_logging():
  java_import_external(
      name = "commons_logging",
      jar_sha256 = "ce6f913cad1f0db3aad70186d65c5bc7ffcc9a99e3fe8e0b137312819f7c362f",
      jar_urls = [
          "http://domain-registry-maven.storage.googleapis.com/repo1.maven.org/maven2/commons-logging/commons-logging/1.1.1/commons-logging-1.1.1.jar",
          "http://maven.ibiblio.org/maven2/commons-logging/commons-logging/1.1.1/commons-logging-1.1.1.jar",
          "http://repo1.maven.org/maven2/commons-logging/commons-logging/1.1.1/commons-logging-1.1.1.jar",
      ],
      licenses = ["notice"],  # The Apache Software License, Version 2.0
  )

def dnsjava():
  java_import_external(
      name = "dnsjava",
      jar_sha256 = "2c52a6fabd5af9331d73fc7787dafc32a56bd8019c49f89749c2eeef244e303c",
      jar_urls = [
          "http://domain-registry-maven.storage.googleapis.com/repo1.maven.org/maven2/dnsjava/dnsjava/2.1.7/dnsjava-2.1.7.jar",
          "http://repo1.maven.org/maven2/dnsjava/dnsjava/2.1.7/dnsjava-2.1.7.jar",
          "http://maven.ibiblio.org/maven2/dnsjava/dnsjava/2.1.7/dnsjava-2.1.7.jar",
      ],
      licenses = ["notice"],  # BSD 2-Clause license
  )

def it_unimi_dsi_fastutil():
  java_import_external(
      name = "it_unimi_dsi_fastutil",
      jar_sha256 = "af5a1ad8261d0607e7d8d3759d97ba7ad834a6be8277466aaccf2121a75963c7",
      jar_urls = [
          "http://domain-registry-maven.storage.googleapis.com/repo1.maven.org/maven2/it/unimi/dsi/fastutil/6.5.16/fastutil-6.5.16.jar",
          "http://maven.ibiblio.org/maven2/it/unimi/dsi/fastutil/6.5.16/fastutil-6.5.16.jar",
          "http://repo1.maven.org/maven2/it/unimi/dsi/fastutil/6.5.16/fastutil-6.5.16.jar",
      ],
      licenses = ["notice"],  # Apache License, Version 2.0
  )

def javax_activation():
  java_import_external(
      name = "javax_activation",
      jar_sha256 = "2881c79c9d6ef01c58e62beea13e9d1ac8b8baa16f2fc198ad6e6776defdcdd3",
      jar_urls = [
          "http://domain-registry-maven.storage.googleapis.com/repo1.maven.org/maven2/javax/activation/activation/1.1/activation-1.1.jar",
          "http://repo1.maven.org/maven2/javax/activation/activation/1.1/activation-1.1.jar",
          "http://maven.ibiblio.org/maven2/javax/activation/activation/1.1/activation-1.1.jar",
      ],
      licenses = ["reciprocal"],  # Common Development and Distribution License (CDDL) v1.0
  )

def javax_inject():
  java_import_external(
      name = "javax_inject",
      jar_sha256 = "91c77044a50c481636c32d916fd89c9118a72195390452c81065080f957de7ff",
      jar_urls = [
          "http://domain-registry-maven.storage.googleapis.com/repo1.maven.org/maven2/javax/inject/javax.inject/1/javax.inject-1.jar",
          "http://repo1.maven.org/maven2/javax/inject/javax.inject/1/javax.inject-1.jar",
          "http://maven.ibiblio.org/maven2/javax/inject/javax.inject/1/javax.inject-1.jar",
      ],
      licenses = ["notice"],  # The Apache Software License, Version 2.0
  )

def javax_mail():
  java_import_external(
      name = "javax_mail",
      jar_sha256 = "96868f82264ebd9b7d41f04d78cbe87ab75d68a7bbf8edfb82416aabe9b54b6c",
      jar_urls = [
          "http://domain-registry-maven.storage.googleapis.com/repo1.maven.org/maven2/javax/mail/mail/1.4/mail-1.4.jar",
          "http://repo1.maven.org/maven2/javax/mail/mail/1.4/mail-1.4.jar",
          "http://maven.ibiblio.org/maven2/javax/mail/mail/1.4/mail-1.4.jar",
      ],
      licenses = ["reciprocal"],  # Common Development and Distribution License (CDDL) v1.0
      deps = ["@javax_activation"],
  )

def javax_servlet_api():
  java_import_external(
      name = "javax_servlet_api",
      jar_sha256 = "c658ea360a70faeeadb66fb3c90a702e4142a0ab7768f9ae9828678e0d9ad4dc",
      jar_urls = [
          "http://domain-registry-maven.storage.googleapis.com/repo1.maven.org/maven2/javax/servlet/servlet-api/2.5/servlet-api-2.5.jar",
          "http://repo1.maven.org/maven2/javax/servlet/servlet-api/2.5/servlet-api-2.5.jar",
          "http://maven.ibiblio.org/maven2/javax/servlet/servlet-api/2.5/servlet-api-2.5.jar",
      ],
      licenses = ["notice"],  # Apache
  )

def javax_xml_bind_jaxb_api():
  java_import_external(
      name = "javax_xml_bind_jaxb_api",
      jar_sha256 = "273d82f8653b53ad9d00ce2b2febaef357e79a273560e796ff3fcfec765f8910",
      jar_urls = [
          "http://domain-registry-maven.storage.googleapis.com/repo1.maven.org/maven2/javax/xml/bind/jaxb-api/2.2.11/jaxb-api-2.2.11.jar",
          "http://maven.ibiblio.org/maven2/javax/xml/bind/jaxb-api/2.2.11/jaxb-api-2.2.11.jar",
          "http://repo1.maven.org/maven2/javax/xml/bind/jaxb-api/2.2.11/jaxb-api-2.2.11.jar",
      ],
      # CDDL 1.1 or GPLv2 w/ CPE (We choo-choo-choose the CDDL)
      # https://glassfish.java.net/public/CDDL+GPL_1_1.html
      licenses = ["reciprocal"],
  )

def joda_time():
  java_import_external(
      name = "joda_time",
      jar_sha256 = "602fd8006641f8b3afd589acbd9c9b356712bdcf0f9323557ec8648cd234983b",
      jar_urls = [
          "http://domain-registry-maven.storage.googleapis.com/repo1.maven.org/maven2/joda-time/joda-time/2.3/joda-time-2.3.jar",
          "http://maven.ibiblio.org/maven2/joda-time/joda-time/2.3/joda-time-2.3.jar",
          "http://repo1.maven.org/maven2/joda-time/joda-time/2.3/joda-time-2.3.jar",
      ],
      licenses = ["notice"],  # Apache 2
  )

def junit():
  java_import_external(
      name = "junit",
      jar_sha256 = "90a8e1603eeca48e7e879f3afbc9560715322985f39a274f6f6070b43f9d06fe",
      jar_urls = [
          "http://domain-registry-maven.storage.googleapis.com/repo1.maven.org/maven2/junit/junit/4.11/junit-4.11.jar",
          "http://repo1.maven.org/maven2/junit/junit/4.11/junit-4.11.jar",
          "http://maven.ibiblio.org/maven2/junit/junit/4.11/junit-4.11.jar",
      ],
      licenses = ["reciprocal"],  # Common Public License Version 1.0
      testonly_ = True,
      deps = ["@org_hamcrest_core"],
  )

def org_apache_ftpserver_core():
  java_import_external(
      name = "org_apache_ftpserver_core",
      jar_sha256 = "e0b6df55cca376c65a8969e4d9ed72a92c9bf0780ee077a03ff728e07314edcb",
      jar_urls = [
          "http://domain-registry-maven.storage.googleapis.com/repo1.maven.org/maven2/org/apache/ftpserver/ftpserver-core/1.0.6/ftpserver-core-1.0.6.jar",
          "http://repo1.maven.org/maven2/org/apache/ftpserver/ftpserver-core/1.0.6/ftpserver-core-1.0.6.jar",
          "http://maven.ibiblio.org/maven2/org/apache/ftpserver/ftpserver-core/1.0.6/ftpserver-core-1.0.6.jar",
      ],
      licenses = ["notice"],  # Apache 2.0 License
      deps = [
          "@org_slf4j_api",
          "@org_apache_mina_core",
      ],
  )

def org_apache_httpcomponents_httpclient():
  java_import_external(
      name = "org_apache_httpcomponents_httpclient",
      jar_sha256 = "752596ebdc7c9ae5d9a655de3bb06d078734679a9de23321dbf284ee44563c03",
      jar_urls = [
          "http://domain-registry-maven.storage.googleapis.com/repo1.maven.org/maven2/org/apache/httpcomponents/httpclient/4.0.1/httpclient-4.0.1.jar",
          "http://repo1.maven.org/maven2/org/apache/httpcomponents/httpclient/4.0.1/httpclient-4.0.1.jar",
          "http://maven.ibiblio.org/maven2/org/apache/httpcomponents/httpclient/4.0.1/httpclient-4.0.1.jar",
      ],
      licenses = ["notice"],  # Apache License
      deps = [
          "@org_apache_httpcomponents_httpcore",
          "@commons_logging",
          "@commons_codec",
      ],
  )

def org_apache_httpcomponents_httpcore():
  java_import_external(
      name = "org_apache_httpcomponents_httpcore",
      jar_sha256 = "3b6bf92affa85d4169a91547ce3c7093ed993b41ad2df80469fc768ad01e6b6b",
      jar_urls = [
          "http://domain-registry-maven.storage.googleapis.com/repo1.maven.org/maven2/org/apache/httpcomponents/httpcore/4.0.1/httpcore-4.0.1.jar",
          "http://maven.ibiblio.org/maven2/org/apache/httpcomponents/httpcore/4.0.1/httpcore-4.0.1.jar",
          "http://repo1.maven.org/maven2/org/apache/httpcomponents/httpcore/4.0.1/httpcore-4.0.1.jar",
      ],
      licenses = ["notice"],  # Apache License
  )

def org_apache_mina_core():
  java_import_external(
      name = "org_apache_mina_core",
      jar_sha256 = "f6e37603b0ff1b50b31c1be7e5815098d78aff1f277db27d3aee5d7e8cce636e",
      jar_urls = [
          "http://domain-registry-maven.storage.googleapis.com/repo1.maven.org/maven2/org/apache/mina/mina-core/2.0.4/mina-core-2.0.4.jar",
          "http://maven.ibiblio.org/maven2/org/apache/mina/mina-core/2.0.4/mina-core-2.0.4.jar",
          "http://repo1.maven.org/maven2/org/apache/mina/mina-core/2.0.4/mina-core-2.0.4.jar",
      ],
      licenses = ["notice"],  # Apache 2.0 License
  )

def org_apache_sshd_core():
  java_import_external(
      name = "org_apache_sshd_core",
      jar_sha256 = "5630fa11f7e2f7f5b6b7e6b9be06e476715dfb48db37998b4b7c3eea098d86ff",
      jar_urls = [
          "http://domain-registry-maven.storage.googleapis.com/repo1.maven.org/maven2/org/apache/sshd/sshd-core/1.2.0/sshd-core-1.2.0.jar",
          "http://maven.ibiblio.org/maven2/org/apache/sshd/sshd-core/1.2.0/sshd-core-1.2.0.jar",
          "http://repo1.maven.org/maven2/org/apache/sshd/sshd-core/1.2.0/sshd-core-1.2.0.jar",
      ],
      licenses = ["notice"],  # Apache 2.0 License
      deps = ["@org_slf4j_api"],
  )

def org_apache_tomcat_servlet_api():
  java_import_external(
      name = "org_apache_tomcat_servlet_api",
      jar_sha256 = "8df016b101b7e5f24940dbbcdf03b8b6b1544462ec6af97a92d3bbf3641153b9",
      jar_urls = [
          "http://domain-registry-maven.storage.googleapis.com/repo1.maven.org/maven2/org/apache/tomcat/servlet-api/6.0.45/servlet-api-6.0.45.jar",
          "http://repo1.maven.org/maven2/org/apache/tomcat/servlet-api/6.0.45/servlet-api-6.0.45.jar",
          "http://maven.ibiblio.org/maven2/org/apache/tomcat/servlet-api/6.0.45/servlet-api-6.0.45.jar",
      ],
      licenses = ["reciprocal"],  # Apache License, Version 2.0 and Common Development And Distribution License (CDDL) Version 1.0
  )

def org_bouncycastle_bcpg_jdk15on():
  java_import_external(
      name = "org_bouncycastle_bcpg_jdk15on",
      jar_sha256 = "eb3c3744c9ad775a7afd03e9dfd3d34786c11832a93ea1143b97cc88b0344154",
      jar_urls = [
          "http://domain-registry-maven.storage.googleapis.com/repo1.maven.org/maven2/org/bouncycastle/bcpg-jdk15on/1.52/bcpg-jdk15on-1.52.jar",
          "http://maven.ibiblio.org/maven2/org/bouncycastle/bcpg-jdk15on/1.52/bcpg-jdk15on-1.52.jar",
          "http://repo1.maven.org/maven2/org/bouncycastle/bcpg-jdk15on/1.52/bcpg-jdk15on-1.52.jar",
      ],
      # Bouncy Castle Licence
      # http://www.bouncycastle.org/licence.html
      # Apache Software License, Version 1.1
      # http://www.apache.org/licenses/LICENSE-1.1
      licenses = ["notice"],
      deps = ["@org_bouncycastle_bcprov_jdk15on"],
  )

def org_bouncycastle_bcpkix_jdk15on():
  java_import_external(
      name = "org_bouncycastle_bcpkix_jdk15on",
      jar_sha256 = "8e8e9ac258051ec8d6f7f1128d0ddec800ed87b14e7a55023d0f2850b8049615",
      jar_urls = [
          "http://domain-registry-maven.storage.googleapis.com/repo1.maven.org/maven2/org/bouncycastle/bcpkix-jdk15on/1.52/bcpkix-jdk15on-1.52.jar",
          "http://maven.ibiblio.org/maven2/org/bouncycastle/bcpkix-jdk15on/1.52/bcpkix-jdk15on-1.52.jar",
          "http://repo1.maven.org/maven2/org/bouncycastle/bcpkix-jdk15on/1.52/bcpkix-jdk15on-1.52.jar",
      ],
      licenses = ["notice"],  # Bouncy Castle Licence
      exports = ["@org_bouncycastle_bcprov_jdk15on"],
      deps = ["@org_bouncycastle_bcprov_jdk15on"],
  )

def org_bouncycastle_bcprov_jdk15on():
  java_import_external(
      name = "org_bouncycastle_bcprov_jdk15on",
      jar_sha256 = "0dc4d181e4d347893c2ddbd2e6cd5d7287fc651c03648fa64b2341c7366b1773",
      jar_urls = [
          "http://domain-registry-maven.storage.googleapis.com/repo1.maven.org/maven2/org/bouncycastle/bcprov-jdk15on/1.52/bcprov-jdk15on-1.52.jar",
          "http://repo1.maven.org/maven2/org/bouncycastle/bcprov-jdk15on/1.52/bcprov-jdk15on-1.52.jar",
          "http://maven.ibiblio.org/maven2/org/bouncycastle/bcprov-jdk15on/1.52/bcprov-jdk15on-1.52.jar",
      ],
      licenses = ["notice"],  # Bouncy Castle Licence
  )

def org_hamcrest_core():
  java_import_external(
      name = "org_hamcrest_core",
      jar_sha256 = "66fdef91e9739348df7a096aa384a5685f4e875584cce89386a7a47251c4d8e9",
      jar_urls = [
          "http://domain-registry-maven.storage.googleapis.com/repo1.maven.org/maven2/org/hamcrest/hamcrest-core/1.3/hamcrest-core-1.3.jar",
          "http://repo1.maven.org/maven2/org/hamcrest/hamcrest-core/1.3/hamcrest-core-1.3.jar",
          "http://maven.ibiblio.org/maven2/org/hamcrest/hamcrest-core/1.3/hamcrest-core-1.3.jar",
      ],
      licenses = ["notice"],  # New BSD License
      testonly_ = True,
  )

def org_hamcrest_library():
  java_import_external(
      name = "org_hamcrest_library",
      jar_sha256 = "711d64522f9ec410983bd310934296da134be4254a125080a0416ec178dfad1c",
      jar_urls = [
          "http://domain-registry-maven.storage.googleapis.com/repo1.maven.org/maven2/org/hamcrest/hamcrest-library/1.3/hamcrest-library-1.3.jar",
          "http://maven.ibiblio.org/maven2/org/hamcrest/hamcrest-library/1.3/hamcrest-library-1.3.jar",
          "http://repo1.maven.org/maven2/org/hamcrest/hamcrest-library/1.3/hamcrest-library-1.3.jar",
      ],
      licenses = ["notice"],  # New BSD License
      testonly_ = True,
      deps = ["@org_hamcrest_core"],
  )

def org_joda_money():
  java_import_external(
      name = "org_joda_money",
      jar_sha256 = "d530b7f0907d91f5c98f25e91eb89ad164845412700be36b07652c07512ef8d4",
      jar_urls = [
          "http://domain-registry-maven.storage.googleapis.com/repo1.maven.org/maven2/org/joda/joda-money/0.10.0/joda-money-0.10.0.jar",
          "http://maven.ibiblio.org/maven2/org/joda/joda-money/0.10.0/joda-money-0.10.0.jar",
          "http://repo1.maven.org/maven2/org/joda/joda-money/0.10.0/joda-money-0.10.0.jar",
      ],
      licenses = ["notice"],  # Apache 2
  )

def org_json():
  java_import_external(
      name = "org_json",
      jar_sha256 = "bf51c9013128cb15201225e51476f60ad9116813729040655a238d2829aef8b8",
      jar_urls = [
          "http://domain-registry-maven.storage.googleapis.com/repo1.maven.org/maven2/org/json/json/20160810/json-20160810.jar",
          "http://repo1.maven.org/maven2/org/json/json/20160810/json-20160810.jar",
      ],
      licenses = ["notice"],  # The JSON License
  )

def org_mockito_all():
  java_import_external(
      name = "org_mockito_all",
      jar_sha256 = "b2a63307d1dce3aa1623fdaacb2327a4cd7795b0066f31bf542b1e8f2683239e",
      jar_urls = [
          "http://domain-registry-maven.storage.googleapis.com/repo1.maven.org/maven2/org/mockito/mockito-all/1.9.5/mockito-all-1.9.5.jar",
          "http://maven.ibiblio.org/maven2/org/mockito/mockito-all/1.9.5/mockito-all-1.9.5.jar",
          "http://repo1.maven.org/maven2/org/mockito/mockito-all/1.9.5/mockito-all-1.9.5.jar",
      ],
      licenses = ["notice"],  # The MIT License
      testonly_ = True,
  )

def org_mortbay_jetty():
  java_import_external(
      name = "org_mortbay_jetty",
      jar_sha256 = "21091d3a9c1349f640fdc421504a604c040ed89087ecc12afbe32353326ed4e5",
      jar_urls = [
          "http://domain-registry-maven.storage.googleapis.com/repo1.maven.org/maven2/org/mortbay/jetty/jetty/6.1.26/jetty-6.1.26.jar",
          "http://repo1.maven.org/maven2/org/mortbay/jetty/jetty/6.1.26/jetty-6.1.26.jar",
          "http://maven.ibiblio.org/maven2/org/mortbay/jetty/jetty/6.1.26/jetty-6.1.26.jar",
      ],
      # Apache Software License - Version 2.0
      # http://www.apache.org/licenses/LICENSE-2.0
      # Eclipse Public License - Version 1.0
      # http://www.eclipse.org/org/documents/epl-v10.php
      licenses = ["notice"],
      deps = [
          "@org_mortbay_jetty_util",
          "@org_mortbay_jetty_servlet_api",
      ],
  )

def org_mortbay_jetty_servlet_api():
  java_import_external(
      name = "org_mortbay_jetty_servlet_api",
      jar_sha256 = "068756096996fe00f604ac3b6672d6f663dc777ea4a83056e240d0456e77e472",
      jar_urls = [
          "http://domain-registry-maven.storage.googleapis.com/repo1.maven.org/maven2/org/mortbay/jetty/servlet-api/2.5-20081211/servlet-api-2.5-20081211.jar",
          "http://maven.ibiblio.org/maven2/org/mortbay/jetty/servlet-api/2.5-20081211/servlet-api-2.5-20081211.jar",
          "http://repo1.maven.org/maven2/org/mortbay/jetty/servlet-api/2.5-20081211/servlet-api-2.5-20081211.jar",
      ],
      licenses = ["notice"],  # Apache License Version 2.0
  )

def org_mortbay_jetty_util():
  java_import_external(
      name = "org_mortbay_jetty_util",
      jar_sha256 = "9b974ce2b99f48254b76126337dc45b21226f383aaed616f59780adaf167c047",
      jar_urls = [
          "http://domain-registry-maven.storage.googleapis.com/repo1.maven.org/maven2/org/mortbay/jetty/jetty-util/6.1.26/jetty-util-6.1.26.jar",
          "http://maven.ibiblio.org/maven2/org/mortbay/jetty/jetty-util/6.1.26/jetty-util-6.1.26.jar",
          "http://repo1.maven.org/maven2/org/mortbay/jetty/jetty-util/6.1.26/jetty-util-6.1.26.jar",
      ],
      # Apache Software License - Version 2.0
      # http://www.apache.org/licenses/LICENSE-2.0
      # Eclipse Public License - Version 1.0
      # http://www.eclipse.org/org/documents/epl-v10.php
      licenses = ["notice"],
      deps = ["@org_mortbay_jetty_servlet_api"],
  )

def org_slf4j_api():
  java_import_external(
      name = "org_slf4j_api",
      jar_sha256 = "e56288031f5e60652c06e7bb6e9fa410a61231ab54890f7b708fc6adc4107c5b",
      jar_urls = [
          "http://domain-registry-maven.storage.googleapis.com/repo1.maven.org/maven2/org/slf4j/slf4j-api/1.7.16/slf4j-api-1.7.16.jar",
          "http://maven.ibiblio.org/maven2/org/slf4j/slf4j-api/1.7.16/slf4j-api-1.7.16.jar",
          "http://repo1.maven.org/maven2/org/slf4j/slf4j-api/1.7.16/slf4j-api-1.7.16.jar",
      ],
      licenses = ["notice"],  # MIT License
  )

def _check_bazel_version(project, bazel_version):
  if "bazel_version" not in dir(native):
    fail("%s requires Bazel >=%s but was <0.2.1" % (project, bazel_version))
  elif not native.bazel_version:
    pass  # user probably compiled Bazel from scratch
  else:
    current_bazel_version = _parse_bazel_version(native.bazel_version)
    minimum_bazel_version = _parse_bazel_version(bazel_version)
    if minimum_bazel_version > current_bazel_version:
      fail("%s requires Bazel >=%s but was %s" % (
          project, bazel_version, native.bazel_version))

def _parse_bazel_version(bazel_version):
  # Remove commit from version.
  version = bazel_version.split(" ", 1)[0]
  # Split into (release, date) parts and only return the release
  # as a tuple of integers.
  parts = version.split("-", 1)
  # Turn "release" into a tuple of strings
  version_tuple = ()
  for number in parts[0].split("."):
    version_tuple += (str(number),)
  return version_tuple
