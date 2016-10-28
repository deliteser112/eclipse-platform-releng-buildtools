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

package google.registry.request;

import static com.google.common.truth.Truth.assertThat;
import static google.registry.request.RequestModule.provideJsonPayload;

import com.google.common.net.MediaType;
import google.registry.request.HttpException.BadRequestException;
import google.registry.request.HttpException.UnsupportedMediaTypeException;
import google.registry.testing.ExceptionRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link RequestModule}. */
@RunWith(JUnit4.class)
public final class RequestModuleTest {

  @Rule
  public final ExceptionRule thrown = new ExceptionRule();

  @Test
  public void testProvideJsonPayload() throws Exception {
    assertThat(provideJsonPayload(MediaType.JSON_UTF_8, "{\"k\":\"v\"}"))
        .containsExactly("k", "v");
  }

  @Test
  public void testProvideJsonPayload_contentTypeWithoutCharsetAllowed() throws Exception {
    assertThat(provideJsonPayload(MediaType.JSON_UTF_8.withoutParameters(), "{\"k\":\"v\"}"))
        .containsExactly("k", "v");
  }

  @Test
  public void testProvideJsonPayload_malformedInput_throws500() throws Exception {
    thrown.expect(BadRequestException.class, "Malformed JSON");
    provideJsonPayload(MediaType.JSON_UTF_8, "{\"k\":");
  }

  @Test
  public void testProvideJsonPayload_emptyInput_throws500() throws Exception {
    thrown.expect(BadRequestException.class, "Malformed JSON");
    provideJsonPayload(MediaType.JSON_UTF_8, "");
  }

  @Test
  public void testProvideJsonPayload_nonJsonContentType_throws415() throws Exception {
    thrown.expect(UnsupportedMediaTypeException.class);
    provideJsonPayload(MediaType.PLAIN_TEXT_UTF_8, "{}");
  }

  @Test
  public void testProvideJsonPayload_contentTypeWithWeirdParam_throws415() throws Exception {
    thrown.expect(UnsupportedMediaTypeException.class);
    provideJsonPayload(MediaType.JSON_UTF_8.withParameter("omg", "handel"), "{}");
  }
}
