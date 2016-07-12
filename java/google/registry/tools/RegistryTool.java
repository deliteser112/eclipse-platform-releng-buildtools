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

package google.registry.tools;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSortedMap;
import google.registry.tools.javascrap.LoadAndResaveCommand;
import google.registry.tools.javascrap.RemoveIpAddressCommand;

/** Container class to create and run remote commands against a datastore instance. */
public final class RegistryTool {

  /**
   * Available commands.
   *
   * <p><b>Note:</b> If changing the command-line name of any commands below, remember to resolve
   * any invocations in scripts (e.g. PDT, ICANN reporting).
   */
  @VisibleForTesting
  static final ImmutableMap<String, Class<? extends Command>> COMMAND_MAP =
      ImmutableSortedMap.<String, Class<? extends Command>>naturalOrder()
          .putAll(GtechTool.COMMAND_MAP)
          .put("allocate_domain", AllocateDomainCommand.class)
          .put("check_snapshot", CheckSnapshotCommand.class)
          .put("create_auction_credits", CreateAuctionCreditsCommand.class)
          .put("create_premium_list", CreatePremiumListCommand.class)
          .put("create_reserved_list", CreateReservedListCommand.class)
          .put("create_tld", CreateTldCommand.class)
          .put("delete_credit", DeleteCreditCommand.class)
          .put("delete_entity", DeleteEntityCommand.class)
          .put("delete_epp_resource", DeleteEppResourceCommand.class)
          .put("delete_premium_list", DeletePremiumListCommand.class)
          .put("delete_reserved_list", DeleteReservedListCommand.class)
          .put("encrypt_escrow_deposit", EncryptEscrowDepositCommand.class)
          .put("execute_epp", ExecuteEppCommand.class)
          .put("generate_zone_files", GenerateZoneFilesCommand.class)
          .put("generate_escrow_deposit", GenerateEscrowDepositCommand.class)
          .put("generate_lordn", GenerateLordnCommand.class)
          .put("get_claims_list", GetClaimsListCommand.class)
          .put("get_resource_by_key", GetResourceByKeyCommand.class)
          .put("ghostryde", GhostrydeCommand.class)
          .put("list_cursors", ListCursorsCommand.class)
          .put("list_domains", ListDomainsCommand.class)
          .put("list_hosts", ListHostsCommand.class)
          .put("list_premium_lists", ListPremiumListsCommand.class)
          .put("list_reserved_lists", ListReservedListsCommand.class)
          .put("load_and_resave", LoadAndResaveCommand.class)
          .put("load_snapshot", LoadSnapshotCommand.class)
          .put("make_billing_tables", MakeBillingTablesCommand.class)
          .put("pending_escrow", PendingEscrowCommand.class)
          .put("remove_ip_address", RemoveIpAddressCommand.class)
          .put("resave_environment_entities", ResaveEnvironmentEntitiesCommand.class)
          .put("send_escrow_report_to_icann", SendEscrowReportToIcannCommand.class)
          .put("update_application_status", UpdateApplicationStatusCommand.class)
          .put("update_claims_notice", UpdateClaimsNoticeCommand.class)
          .put("update_credits", UpdateCreditsCommand.class)
          .put("update_cursors", UpdateCursorsCommand.class)
          .put("update_premium_list", UpdatePremiumListCommand.class)
          .put("update_reserved_list", UpdateReservedListCommand.class)
          .put("update_smd", UpdateSmdCommand.class)
          .put("update_tld", UpdateTldCommand.class)
          .put("upload_claims_list", UploadClaimsListCommand.class)
          .put("validate_escrow_deposit", ValidateEscrowDepositCommand.class)
          .build();

  public static void main(String[] args) throws Exception {
    RegistryToolEnvironment.parseFromArgs(args).setup();
    new RegistryCli().run("registry_tool", args, COMMAND_MAP);
  }
}
