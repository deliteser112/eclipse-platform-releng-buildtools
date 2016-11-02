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


def domain_registry_repositories():

  native.maven_jar(
      name = "appengine_api_sdk",
      artifact = "com.google.appengine:appengine-api-1.0-sdk:1.9.42",
      sha1 = "c972bc847992e5512eb4338a38cc2392e56760f6",
  )

  native.maven_jar(
      name = "appengine_api_labs",
      artifact = "com.google.appengine:appengine-api-labs:1.9.42",
      sha1 = "1ff4107f603b12ef3016c8249884e7495718dd59",
  )

  native.maven_jar(
      name = "appengine_api_stubs",
      artifact = "com.google.appengine:appengine-api-stubs:1.9.42",
      sha1 = "3066543e37c01ea7ae1f6f7350c35c048c4d31f4",
  )

  native.maven_jar(
      name = "appengine_gcs_client",
      artifact = "com.google.appengine.tools:appengine-gcs-client:0.6",
      sha1 = "e8fc1b49334c636cdeb135c31895705deea3ccbb",
  )

  native.maven_jar(
      name = "appengine_mapreduce",
      artifact = "com.google.appengine.tools:appengine-mapreduce:0.8.5",
      sha1 = "46e0456540a9fe9006c4accb51c4c8d9a45a77ce",
  )

  native.maven_jar(
      name = "appengine_pipeline",
      artifact = "com.google.appengine.tools:appengine-pipeline:0.2.13",
      sha1 = "2019a2c6acdbc8216161970afac96bb147d07c36",
  )

  native.maven_jar(
      name = "appengine_remote_api",
      artifact = "com.google.appengine:appengine-remote-api:1.9.42",
      sha1 = "28ebe680f55122b11031d833d09a1b4ab94130f1",
  )

  native.maven_jar(
      name = "appengine_testing",
      artifact = "com.google.appengine:appengine-testing:1.9.42",
      sha1 = "25707bc375e47ae14564f7051d6842bb11cd3add",
  )

  native.maven_jar(
      name = "appengine_tools_sdk",
      artifact = "com.google.appengine:appengine-tools-sdk:1.9.42",
      sha1 = "bf3cec2fc9a9ed8f4de36e17fc61c44a8d9df935",
  )

  native.maven_jar(
      name = "auto_common",
      artifact = "com.google.auto:auto-common:0.7",
      sha1 = "910d8b3ff71063135ae743d43d3dde3435c8648c",
  )

  native.maven_jar(
      name = "auto_factory",
      artifact = "com.google.auto.factory:auto-factory:1.0-beta3",
      sha1 = "99b2ffe0e41abbd4cc42bf3836276e7174c4929d",
  )

  native.maven_jar(
      name = "auto_service",
      artifact = "com.google.auto.service:auto-service:1.0-rc2",
      sha1 = "51033a5b8fcf7039159e35b6878f106ccd5fb35f",
  )

  native.maven_jar(
      name = "auto_value",
      artifact = "com.google.auto.value:auto-value:1.3",
      sha1 = "4961194f62915eb45e21940537d60ac53912c57d",
  )

  native.maven_jar(
      name = "bcpg_jdk15on",
      artifact = "org.bouncycastle:bcpg-jdk15on:1.52",
      sha1 = "ff4665a4b5633ff6894209d5dd10b7e612291858",
  )

  native.maven_jar(
      name = "bcprov_jdk15on",
      artifact = "org.bouncycastle:bcprov-jdk15on:1.52",
      sha1 = "88a941faf9819d371e3174b5ed56a3f3f7d73269",
  )

  native.maven_jar(
      name = "bcpkix_jdk15on",
      artifact = "org.bouncycastle:bcpkix-jdk15on:1.52",
      sha1 = "b8ffac2bbc6626f86909589c8cc63637cc936504",
  )

  native.maven_jar(
      name = "braintree_java",
      artifact = "com.braintreepayments.gateway:braintree-java:2.54.0",
      sha1 = "b9940196feaf692de32b0d37c55ded76fb9b1ba7",
  )

  native.maven_jar(
      name = "charts4j",
      artifact = "com.googlecode.charts4j:charts4j:1.3",
      sha1 = "80dd3b0d5591580c429b0e2529706f6be5bddc0f",
  )

  native.maven_jar(
      name = "dagger",
      artifact = "com.google.dagger:dagger:2.7",
      sha1 = "f60e4926b5f05a62ff73e73b6eb3a856cdc74ddb",
  )

  native.maven_jar(
      name = "dagger_compiler",
      artifact = "com.google.dagger:dagger-compiler:2.7",
      sha1 = "65aa7daec6dd64bf4f3208b268c38c6a4fb2b849",
  )

  native.maven_jar(
      name = "dagger_producers",
      artifact = "com.google.dagger:dagger-producers:2.7",
      sha1 = "109b30d9c44c037e3bee87e85564fd604a7d432f",
  )

  native.maven_jar(
      name = "dnsjava",
      artifact = "dnsjava:dnsjava:2.1.7",
      sha1 = "0a1ed0a251d22bf528cebfafb94c55e6f3f339cf",
  )

  native.maven_jar(
      name = "eclipse_jdt_core",
      artifact = "org.eclipse.jdt:org.eclipse.jdt.core:3.10.0",
      sha1 = "647e19b28c106a63a14401c0f5956289792adf2f",
  )

  native.maven_jar(
      name = "error_prone_annotations",
      artifact = "com.google.errorprone:error_prone_annotations:2.0.13",
      sha1 = "5bbec1732d649b180d82f98546ce9379ca6e64a7",
  )

  native.maven_jar(
      name = "fastutil",
      artifact = "it.unimi.dsi:fastutil:6.5.16",
      sha1 = "cc0df01620c4bef9e89123e0a5c3c226bdb36ea0",
  )

  native.maven_jar(
      name = "ftpserver_core",
      artifact = "org.apache.ftpserver:ftpserver-core:1.0.6",
      sha1 = "2ad1570cd6c0d7ea7ca4d3c26a205e02452f5d7d",
  )

  native.maven_jar(
      name = "gdata_core",
      artifact = "com.google.gdata:core:1.47.1",
      sha1 = "52ee0d917c1c3461f6e12079f73ed71bc75f12d4",
  )

  native.maven_jar(
      name = "google_api_client",
      artifact = "com.google.api-client:google-api-client:1.22.0",
      sha1 = "0244350c0c845c583717ade13f5666a452fd0cfa",
  )

  native.maven_jar(
      name = "google_api_client_appengine",
      artifact = "com.google.api-client:google-api-client-appengine:1.22.0",
      sha1 = "1bf4744e6077d54b8ee481da17f7b19ecfddb227",
  )

  native.maven_jar(
      name = "google_api_services_admin_directory",
      artifact = "com.google.apis:google-api-services-admin-directory:directory_v1-rev72-1.22.0",
      sha1 = "63d932404942efddb6d55c23f856d5bfd13180d1",
  )

  native.maven_jar(
      name = "google_api_services_bigquery",
      artifact = "com.google.apis:google-api-services-bigquery:v2-rev325-1.22.0",
      sha1 = "41f4d50e1879a102fb6ce669f574b4670b9ead78",
  )

  native.maven_jar(
      name = "google_api_services_dns",
      artifact = "com.google.apis:google-api-services-dns:v2beta1-rev6-1.22.0",
      sha1 = "d707b4b96c725692aae8fd28d4b528c65928aaef",
  )

  native.maven_jar(
      name = "google_api_services_drive",
      artifact = "com.google.apis:google-api-services-drive:v2-rev160-1.19.1",
      sha1 = "098adf9128428643992ae6fa0878a7f45e7cec7d",
  )

  native.maven_jar(
      name = "google_api_services_monitoring",
      artifact = "com.google.apis:google-api-services-monitoring:v3-rev11-1.22.0",
      sha1 = "b63c77f2bd96480f018c4f4b8877afb291ceca6c",
  )

  native.maven_jar(
      name = "google_api_services_storage",
      artifact = "com.google.apis:google-api-services-storage:v1-rev86-1.22.0",
      sha1 = "5da66d2d5687d38af4bff26c22c32314cfcab006",
  )

  native.maven_jar(
      name = "google_api_services_groupssettings",
      artifact = "com.google.apis:google-api-services-groupssettings:v1-rev60-1.22.0",
      sha1 = "83967af07039f56af009114f52b34d6e865f89ec",
  )

  native.maven_jar(
      name = "google_http_client",
      artifact = "com.google.http-client:google-http-client:1.22.0",
      sha1 = "d441fc58329c4a4c067acec04ac361627f66ecc8",
  )

  native.maven_jar(
      name = "google_http_client_appengine",
      artifact = "com.google.http-client:google-http-client-appengine:1.22.0",
      sha1 = "37091fdc63f6b496199e4512f0f291d6fffdd697",
  )

  native.maven_jar(
      name = "google_http_client_jackson2",
      artifact = "com.google.http-client:google-http-client-jackson2:1.22.0",
      sha1 = "cc014d64ae11117e159d334c99d9c246d9b36f44",
  )

  native.maven_jar(
      name = "google_oauth_client",
      artifact = "com.google.oauth-client:google-oauth-client:1.22.0",
      sha1 = "1d63f369ac78e4838a3197147012026e791008cb",
  )

  native.maven_jar(
      name = "google_oauth_client_appengine",
      artifact = "com.google.oauth-client:google-oauth-client-appengine:1.22.0",
      sha1 = "18a01de34ace9934f21fc23fc6011832f4c3e34f",
  )

  native.maven_jar(
      name = "gson",
      artifact = "com.google.code.gson:gson:2.7",
      sha1 = "751f548c85fa49f330cecbb1875893f971b33c4e",
  )

  native.maven_jar(
      name = "guava",
      artifact = "com.google.guava:guava:19.0",
      sha1 = "6ce200f6b23222af3d8abb6b6459e6c44f4bb0e9",
  )

  native.maven_jar(
      name = "guava_testlib",
      artifact = "com.google.guava:guava-testlib:19.0",
      sha1 = "ce5b880b206de3f76d364988a6308c68c726f74a",
  )

  native.maven_jar(
      name = "hamcrest_core",
      artifact = "org.hamcrest:hamcrest-core:1.3",
      sha1 = "42a25dc3219429f0e5d060061f71acb49bf010a0",
  )

  native.maven_jar(
      name = "hamcrest_library",
      artifact = "org.hamcrest:hamcrest-library:1.3",
      sha1 = "4785a3c21320980282f9f33d0d1264a69040538f",
  )

  native.maven_jar(
      name = "icu4j",
      artifact = "com.ibm.icu:icu4j:57.1",
      sha1 = "198ea005f41219f038f4291f0b0e9f3259730e92",
  )

  native.maven_jar(
      name = "jackson_core",
      artifact = "com.fasterxml.jackson.core:jackson-core:2.7.0",
      sha1 = "05f2bfd0866bcacbcc7c25ce25f80b5aeed0f72f",
  )

  native.maven_jar(
      name = "jackson_databind",
      artifact = "com.fasterxml.jackson.core:jackson-databind:2.7.0",
      sha1 = "7d3430de9f2b600b074ba3bc007edcfbc4bbf4fb",
  )

  native.maven_jar(
      name = "javapoet",
      artifact = "com.squareup:javapoet:1.7.0",
      sha1 = "4fdcf1fc27c1a8f55d1109df986c923152f07759",
  )

  native.maven_jar(
      name = "javawriter",
      artifact = "com.squareup:javawriter:2.5.1",
      sha1 = "54c87b3d91238e5b58e1a436d4916eee680ec959",
  )

  native.maven_jar(
      name = "jaxb_api",
      artifact = "javax.xml.bind:jaxb-api:2.2.11",
      sha1 = "32274d4244967ff43e7a5d967743d94ed3d2aea7",
  )

  native.maven_jar(
      name = "jaxb_core",
      artifact = "com.sun.xml.bind:jaxb-core:2.2.11",
      sha1 = "c3f87d654f8d5943cd08592f3f758856544d279a",
  )

  native.maven_jar(
      name = "jaxb_impl",
      artifact = "com.sun.xml.bind:jaxb-impl:2.2.11",
      sha1 = "a49ce57aee680f9435f49ba6ef427d38c93247a6",
  )

  native.maven_jar(
      name = "jaxb_xjc",
      artifact = "com.sun.xml.bind:jaxb-xjc:2.2.11",
      sha1 = "f099cedb9b245323f906ab9f75adc48cef305cfd",
  )

  native.maven_jar(
      name = "jcommander",
      artifact = "com.beust:jcommander:1.48",
      sha1 = "bfcb96281ea3b59d626704f74bc6d625ff51cbce",
  )

  # Sync with specific version in
  # https://github.com/google/nomulus/tree/master/third_party/java/jetty/.
  native.maven_jar(
      name = "jetty",
      artifact = "org.mortbay.jetty:jetty:6.1.22",
      sha1 = "e097b3b684cececf84a35cfdd08e56096a3188da",
  )

  # Sync with specific version in
  # https://github.com/google/nomulus/tree/master/third_party/java/jetty/.
  native.maven_jar(
      name = "jetty_util",
      artifact = "org.mortbay.jetty:jetty-util:6.1.22",
      sha1 = "9039d1940a9ae1c91d2b5d7fdfd64bd1924cd447",
  )

  native.maven_jar(
      name = "joda_money",
      artifact = "org.joda:joda-money:0.10.0",
      sha1 = "4056712d2e6db043a38b78c4ee2130c74bae7216",
  )

  native.maven_jar(
      name = "joda_time",
      artifact = "joda-time:joda-time:2.3",
      sha1 = "56498efd17752898cfcc3868c1b6211a07b12b8f",
  )

  # TODO(nickfelt) remove this in favor of json_simple.
  native.maven_jar(
      name = "json",
      artifact = "org.json:json:20160810",
      sha1 = "aca5eb39e2a12fddd6c472b240afe9ebea3a6733",
  )

  native.maven_jar(
      name = "json_simple",
      artifact = "com.googlecode.json-simple:json-simple:1.1.1",
      sha1 = "c9ad4a0850ab676c5c64461a05ca524cdfff59f1",
  )

  native.maven_jar(
      name = "jsr305",
      artifact = "com.google.code.findbugs:jsr305:1.3.9",
      sha1 = "40719ea6961c0cb6afaeb6a921eaa1f6afd4cfdf",
  )

  native.maven_jar(
      name = "jsr330_inject",
      artifact = "javax.inject:javax.inject:1",
      sha1 = "6975da39a7040257bd51d21a231b76c915872d38",
  )

  native.maven_jar(
      name = "junit",
      artifact = "junit:junit:4.11",
      sha1 = "4e031bb61df09069aeb2bffb4019e7a5034a4ee0",
  )

  native.maven_jar(
      name = "jzlib",
      artifact = "com.jcraft:jzlib:1.1.3",
      sha1 = "c01428efa717624f7aabf4df319939dda9646b2d",
  )

  native.maven_jar(
      name = "mina_core",
      artifact = "org.apache.mina:mina-core:2.0.0",
      sha1 = "4ae3550e925c2621eca3ef9fb4de5298d6f91cc4",
  )

  native.maven_jar(
      name = "mockito",
      artifact = "org.mockito:mockito-all:1.9.5",
      sha1 = "79a8984096fc6591c1e3690e07d41be506356fa5",
  )

  native.maven_jar(
      name = "re2j",
      artifact = "com.google.re2j:re2j:1.1",
      sha1 = "d716952ab58aa4369ea15126505a36544d50a333",
  )

  native.maven_jar(
      name = "servlet_api",
      artifact = "org.apache.tomcat:servlet-api:6.0.45",
      sha1 = "ffcc8209754499940a65a6d450afcb2670a7f7a8",
  )

  native.maven_jar(
      name = "slf4j_api",
      artifact = "org.slf4j:slf4j-api:1.7.14",
      sha1 = "862a5bc736005d68727d1387844d09d62efdb6cb",
  )

  native.maven_jar(
      name = "sshd_core",
      artifact = "org.apache.sshd:sshd-core:1.2.0",
      sha1 = "4bc24a8228ba83dac832680366cf219da71dae8e",
  )

  native.maven_jar(
      name = "truth",
      artifact = "com.google.truth:truth:0.30",
      sha1 = "9d591b5a66eda81f0b88cf1c748ab8853d99b18b",
  )
