// Copyright 2016 Google Inc. All Rights Reserved.
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

package com.google.domain.registry.model.translators;

import static com.google.common.truth.Truth.assertThat;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.domain.registry.flows.EppXmlTransformer;
import com.google.domain.registry.model.eppcommon.StatusValue;
import com.google.domain.registry.model.eppinput.EppInput;
import com.google.domain.registry.model.eppinput.EppInput.ResourceCommandWrapper;
import com.google.domain.registry.model.eppoutput.EppOutput;
import com.google.domain.registry.model.eppoutput.Response;
import com.google.domain.registry.model.host.HostCommand;
import com.google.domain.registry.model.host.HostResource;
import com.google.domain.registry.testing.AppEngineRule;
import com.google.domain.registry.testing.EppLoader;
import com.google.domain.registry.xml.ValidationMode;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class StatusValueAdapterTest {

  // Needed to create HostResources.
  @Rule
  public AppEngineRule appEngine = new AppEngineRule.Builder()
      .withDatastore()
      .build();

  @Test
  public void testMarshalling() throws Exception {
    // Mangle the status value through marshalling by stuffing it in a host info response and then
    // ripping it out of the marshalled xml. Use lenient marshalling so we can omit other fields.
    String marshalled = new String(
        EppXmlTransformer.marshal(
            EppOutput.create(new Response.Builder()
                .setResData(ImmutableList.of(new HostResource.Builder()
                    .addStatusValue(StatusValue.CLIENT_UPDATE_PROHIBITED)
                    .build()))
                .build()),
            ValidationMode.LENIENT),
        UTF_8);
    assertThat(marshalled.toString()).contains("<host:status s=\"clientUpdateProhibited\"/>");
  }

  private StatusValue unmarshal(String statusValueXml) throws Exception {
    // Mangle the status value through unmarshalling by stuffing it in a simple host command and
    // then ripping it out of the unmarshalled EPP object.
    EppInput eppInput =
        new EppLoader(this, "host_update.xml", ImmutableMap.of("STATUS", statusValueXml)).getEpp();
    ResourceCommandWrapper wrapper =
        (ResourceCommandWrapper) eppInput.getCommandWrapper().getCommand();
    HostCommand.Update update = (HostCommand.Update) wrapper.getResourceCommand();
    return update.getInnerAdd().getStatusValues().asList().get(0);
  }

  @Test
  public void testNoOptionalFields_unmarshallsWithoutException() throws Exception {
    assertThat(unmarshal("<host:status s=\"clientUpdateProhibited\"/>"))
        .isEqualTo(StatusValue.CLIENT_UPDATE_PROHIBITED);
  }

  @Test
  public void testHasLang_unmarshallsWithoutException() throws Exception {
    assertThat(unmarshal("<host:status s=\"clientUpdateProhibited\" lang=\"fr\"/>"))
        .isEqualTo(StatusValue.CLIENT_UPDATE_PROHIBITED);
  }

  @Test
  public void testHasMessage_unmarshallsWithoutException() throws Exception {
    assertThat(unmarshal("<host:status s=\"clientUpdateProhibited\">my message</host:status>"))
        .isEqualTo(StatusValue.CLIENT_UPDATE_PROHIBITED);
  }
}
