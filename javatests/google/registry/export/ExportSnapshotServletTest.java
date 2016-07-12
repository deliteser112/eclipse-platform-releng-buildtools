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

package google.registry.export;

import static javax.servlet.http.HttpServletResponse.SC_OK;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import google.registry.config.TestRegistryConfig;
import google.registry.testing.FakeClock;
import google.registry.testing.InjectRule;
import google.registry.testing.RegistryConfigRule;
import java.io.PrintWriter;
import java.io.StringWriter;
import javax.servlet.ServletConfig;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

/** Unit tests for {@link ExportSnapshotServlet}. */
@RunWith(MockitoJUnitRunner.class)
public class ExportSnapshotServletTest {

  @Rule
  public final InjectRule inject = new InjectRule();

  @Rule
  public final RegistryConfigRule configRule = new RegistryConfigRule(new TestRegistryConfig() {
    @Override public String getSnapshotsBucket() {
      return "Bucket-Id";
    }});

  @Mock
  private HttpServletRequest req;

  @Mock
  private HttpServletResponse rsp;

  @Mock
  private DatastoreBackupService backupService;

  @Mock
  private CheckSnapshotServlet checkSnapshotServlet;

  private final FakeClock clock = new FakeClock();
  private final StringWriter httpOutput = new StringWriter();
  private final ExportSnapshotServlet servlet = new ExportSnapshotServlet();

  private static final DateTime START_TIME = DateTime.parse("2014-08-01T01:02:03Z");

  @Before
  public void before() throws Exception {
    clock.setTo(START_TIME);
    inject.setStaticField(ExportSnapshotServlet.class, "clock", clock);
    inject.setStaticField(ExportSnapshotServlet.class, "backupService", backupService);
    inject.setStaticField(
        ExportSnapshotServlet.class, "checkSnapshotServlet", checkSnapshotServlet);
    when(rsp.getWriter()).thenReturn(new PrintWriter(httpOutput));

    servlet.init(mock(ServletConfig.class));
    when(req.getMethod()).thenReturn("POST");
  }

  @Test
  public void testPost_launchesBackup_andEnqueuesPollTask() throws Exception {
    servlet.service(req, rsp);
    verify(rsp).setStatus(SC_OK);
    verify(backupService).launchNewBackup(
        ExportSnapshotServlet.QUEUE,
        "auto_snapshot_20140801_010203",
        "Bucket-Id",
        ExportConstants.getBackupKinds());
    verify(checkSnapshotServlet)
        .enqueuePollTask("auto_snapshot_20140801_010203", ExportConstants.getReportingKinds());
  }
}
