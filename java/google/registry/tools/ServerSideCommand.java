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

package google.registry.tools;

import com.google.common.net.MediaType;
import google.registry.tools.Command.RemoteApiCommand;
import java.io.IOException;
import java.util.Map;

/** A command that executes on the server. */
interface ServerSideCommand extends RemoteApiCommand {

  /** An http connection to AppEngine. */
  interface Connection {

    void prefetchXsrfToken() throws IOException;

    String send(String endpoint, Map<String, ?> params, MediaType contentType, byte[] payload)
        throws IOException;

    Map<String, Object> sendJson(String endpoint, Map<String, ?> object) throws IOException;

    String getServerUrl();
  }

  void setConnection(Connection connection);
}
