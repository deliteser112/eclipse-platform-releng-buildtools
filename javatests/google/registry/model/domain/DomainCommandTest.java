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

package google.registry.model.domain;

import google.registry.model.ResourceCommandTestCase;
import org.junit.Test;

/** Test xml roundtripping of commands. */
public class DomainCommandTest extends ResourceCommandTestCase {
  @Test
  public void testCreate() throws Exception {
    doXmlRoundtripTest("domain_create.xml");
  }

  @Test
  public void testCreate_sunriseSignedMark() throws Exception {
    doXmlRoundtripTest("domain_create_sunrise_signed_mark.xml");
  }

  @Test
  public void testCreate_sunriseCode() throws Exception {
    doXmlRoundtripTest("domain_create_sunrise_code.xml");
  }

  @Test
  public void testCreate_sunriseMark() throws Exception {
    doXmlRoundtripTest("domain_create_sunrise_mark.xml");
  }

  @Test
  public void testCreate_sunriseCodeWithMark() throws Exception {
    doXmlRoundtripTest("domain_create_sunrise_code_with_mark.xml");
  }

  @Test
  public void testCreate_sunriseEncodedSignedMark() throws Exception {
    doXmlRoundtripTest("domain_create_sunrise_encoded_signed_mark.xml");
  }

  @Test
  public void testCreate_landrush() throws Exception {
    doXmlRoundtripTest("domain_create_landrush.xml");
  }

  @Test
  public void testCreate_fee() throws Exception {
    doXmlRoundtripTest("domain_create_fee.xml");
  }

  @Test
  public void testCreate_flags() throws Exception {
    doXmlRoundtripTest("domain_create_flags.xml");
  }

  @Test
  public void testDelete() throws Exception {
    doXmlRoundtripTest("domain_delete.xml");
  }

  @Test
  public void testUpdate() throws Exception {
    doXmlRoundtripTest("domain_update.xml");
  }

  @Test
  public void testUpdate_fee() throws Exception {
    doXmlRoundtripTest("domain_update_fee.xml");
  }

  @Test
  public void testUpdate_flags() throws Exception {
    doXmlRoundtripTest("domain_update_flags.xml");
  }

  @Test
  public void testInfo() throws Exception {
    doXmlRoundtripTest("domain_info.xml");
  }

  @Test
  public void testInfo_sunrise() throws Exception {
    doXmlRoundtripTest("domain_info_sunrise.xml");
  }

  @Test
  public void testInfo_landrush() throws Exception {
    doXmlRoundtripTest("domain_info_landrush.xml");
  }

  @Test
  public void testInfo_sunriseNoApplicationId() throws Exception {
    doXmlRoundtripTest("domain_info_sunrise_no_application_id.xml");
  }

  @Test
  public void testInfo_feeExtension() throws Exception {
    doXmlRoundtripTest("domain_info_fee.xml");
  }

  @Test
  public void testCheck() throws Exception {
    doXmlRoundtripTest("domain_check.xml");
  }

  @Test
  public void testCheck_avail() throws Exception {
    doXmlRoundtripTest("domain_check_avail.xml");
  }

  @Test
  public void testCheck_claims() throws Exception {
    doXmlRoundtripTest("domain_check_claims.xml");
  }

  @Test
  public void testCheck_fee() throws Exception {
    doXmlRoundtripTest("domain_check_fee.xml");
  }

  @Test
  public void testCheck_flags() throws Exception {
    doXmlRoundtripTest("domain_check_flags.xml");
  }

  @Test
  public void testTransferApprove() throws Exception {
    doXmlRoundtripTest("domain_transfer_approve.xml");
  }

  @Test
  public void testTransferReject() throws Exception {
    doXmlRoundtripTest("domain_transfer_reject.xml");
  }

  @Test
  public void testTransferCancel() throws Exception {
    doXmlRoundtripTest("domain_transfer_cancel.xml");
  }

  @Test
  public void testTransferQuery() throws Exception {
    doXmlRoundtripTest("domain_transfer_query.xml");
  }

  @Test
  public void testTransferRequest() throws Exception {
    doXmlRoundtripTest("domain_transfer_request.xml");
  }

  @Test
  public void testTransferRequest_fee() throws Exception {
    doXmlRoundtripTest("domain_transfer_request_fee.xml");
  }

  @Test
  public void testTransferRequest_flags() throws Exception {
    doXmlRoundtripTest("domain_transfer_request_flags.xml");
  }

  @Test
  public void testRenew() throws Exception {
    doXmlRoundtripTest("domain_renew.xml");
  }

  @Test
  public void testRenew_fee() throws Exception {
    doXmlRoundtripTest("domain_renew_fee.xml");
  }
}
