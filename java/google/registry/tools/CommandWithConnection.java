// Copyright 2018 The Nomulus Authors. All Rights Reserved.
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

package google.registry.tools;

import com.google.common.net.MediaType;
import java.io.IOException;
import java.util.Map;
import javax.annotation.Nullable;

/** A command that can send HTTP requests to a backend module. */
interface CommandWithConnection extends Command {

  /** An http connection to AppEngine. */
  interface Connection {

    void prefetchXsrfToken();

    /** Send a POST request.  TODO(mmuller): change to sendPostRequest() */
    String send(
        String endpoint, Map<String, ?> params, MediaType contentType, @Nullable byte[] payload)
        throws IOException;

    String sendGetRequest(String endpoint, Map<String, ?> params) throws IOException;

    Map<String, Object> sendJson(String endpoint, Map<String, ?> object) throws IOException;

    String getServerUrl();
  }

  void setConnection(Connection connection);
}
