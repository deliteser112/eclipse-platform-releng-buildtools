# Copyright 2017 The Nomulus Authors. All Rights Reserved.
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

#
#      .'``'.      ...
#     :o  o `....'`  ;
#     `. O         :'
#       `':          `.
#         `:.          `.
#          : `.         `.
#         `..'`...       `.
#                 `...     `.
#  DO NOT EDIT        ``...  `.
#         THIS FILE        `````.
#
# When you make changes to the XML schemas (*.xsd) or the JAXB bindings file
# (bindings.xjb), you must regenerate this file with the following commands:
#
#   bazel run java/google/registry/xjc:list_generated_files | tee /tmp/lol
#   mv /tmp/lol java/google/registry/xjc/generated_files.bzl
#

pkginfo_generated_files = [
    "contact/package-info.java",
    "domain/package-info.java",
    "dsig/package-info.java",
    "epp/package-info.java",
    "eppcom/package-info.java",
    "fee06/package-info.java",
    "fee11/package-info.java",
    "fee12/package-info.java",
    "host/package-info.java",
    "iirdea/package-info.java",
    "launch/package-info.java",
    "mark/package-info.java",
    "rde/package-info.java",
    "rdecontact/package-info.java",
    "rdedomain/package-info.java",
    "rdeeppparams/package-info.java",
    "rdeheader/package-info.java",
    "rdehost/package-info.java",
    "rdeidn/package-info.java",
    "rdenndn/package-info.java",
    "rdenotification/package-info.java",
    "rdepolicy/package-info.java",
    "rderegistrar/package-info.java",
    "rdereport/package-info.java",
    "rgp/package-info.java",
    "secdns/package-info.java",
    "smd/package-info.java",
]

xjc_generated_files = [
    "contact/ObjectFactory.java",
    "contact/XjcContactAddRemType.java",
    "contact/XjcContactAddrType.java",
    "contact/XjcContactAuthIDType.java",
    "contact/XjcContactAuthInfoType.java",
    "contact/XjcContactCheck.java",
    "contact/XjcContactCheckIDType.java",
    "contact/XjcContactCheckType.java",
    "contact/XjcContactChgPostalInfoType.java",
    "contact/XjcContactChgType.java",
    "contact/XjcContactChkData.java",
    "contact/XjcContactCreData.java",
    "contact/XjcContactCreate.java",
    "contact/XjcContactDelete.java",
    "contact/XjcContactDiscloseType.java",
    "contact/XjcContactE164Type.java",
    "contact/XjcContactInfData.java",
    "contact/XjcContactInfo.java",
    "contact/XjcContactIntLocType.java",
    "contact/XjcContactPaCLIDType.java",
    "contact/XjcContactPanData.java",
    "contact/XjcContactPostalInfoEnumType.java",
    "contact/XjcContactPostalInfoType.java",
    "contact/XjcContactStatusType.java",
    "contact/XjcContactStatusValueType.java",
    "contact/XjcContactTransfer.java",
    "contact/XjcContactTrnData.java",
    "contact/XjcContactUpdate.java",
    "domain/ObjectFactory.java",
    "domain/XjcDomainAddRemType.java",
    "domain/XjcDomainAuthInfoChgType.java",
    "domain/XjcDomainAuthInfoType.java",
    "domain/XjcDomainCheck.java",
    "domain/XjcDomainCheckNameType.java",
    "domain/XjcDomainCheckType.java",
    "domain/XjcDomainChgType.java",
    "domain/XjcDomainChkData.java",
    "domain/XjcDomainContactAttrType.java",
    "domain/XjcDomainContactType.java",
    "domain/XjcDomainCreData.java",
    "domain/XjcDomainCreate.java",
    "domain/XjcDomainDelete.java",
    "domain/XjcDomainHostAttrType.java",
    "domain/XjcDomainHostsType.java",
    "domain/XjcDomainInfData.java",
    "domain/XjcDomainInfo.java",
    "domain/XjcDomainInfoNameType.java",
    "domain/XjcDomainNsType.java",
    "domain/XjcDomainPUnitType.java",
    "domain/XjcDomainPaNameType.java",
    "domain/XjcDomainPanData.java",
    "domain/XjcDomainPeriodType.java",
    "domain/XjcDomainRenData.java",
    "domain/XjcDomainRenew.java",
    "domain/XjcDomainStatusType.java",
    "domain/XjcDomainStatusValueType.java",
    "domain/XjcDomainTransfer.java",
    "domain/XjcDomainTrnData.java",
    "domain/XjcDomainUpdate.java",
    "dsig/ObjectFactory.java",
    "dsig/XjcDsigCanonicalizationMethod.java",
    "dsig/XjcDsigDSAKeyValue.java",
    "dsig/XjcDsigDigestMethod.java",
    "dsig/XjcDsigDigestValue.java",
    "dsig/XjcDsigKeyInfo.java",
    "dsig/XjcDsigKeyName.java",
    "dsig/XjcDsigKeyValue.java",
    "dsig/XjcDsigManifest.java",
    "dsig/XjcDsigMgmtData.java",
    "dsig/XjcDsigObject.java",
    "dsig/XjcDsigPGPData.java",
    "dsig/XjcDsigRSAKeyValue.java",
    "dsig/XjcDsigReference.java",
    "dsig/XjcDsigRetrievalMethod.java",
    "dsig/XjcDsigSPKIData.java",
    "dsig/XjcDsigSignature.java",
    "dsig/XjcDsigSignatureMethod.java",
    "dsig/XjcDsigSignatureProperties.java",
    "dsig/XjcDsigSignatureProperty.java",
    "dsig/XjcDsigSignatureValue.java",
    "dsig/XjcDsigSignedInfo.java",
    "dsig/XjcDsigTransform.java",
    "dsig/XjcDsigTransforms.java",
    "dsig/XjcDsigX509Data.java",
    "dsig/XjcDsigX509IssuerSerialType.java",
    "epp/ObjectFactory.java",
    "epp/XjcEpp.java",
    "epp/XjcEppCommandType.java",
    "epp/XjcEppCredsOptionsType.java",
    "epp/XjcEppDcpAccessType.java",
    "epp/XjcEppDcpExpiryType.java",
    "epp/XjcEppDcpOursType.java",
    "epp/XjcEppDcpPurposeType.java",
    "epp/XjcEppDcpRecipientType.java",
    "epp/XjcEppDcpRetentionType.java",
    "epp/XjcEppDcpStatementType.java",
    "epp/XjcEppDcpType.java",
    "epp/XjcEppElement.java",
    "epp/XjcEppErrValueType.java",
    "epp/XjcEppExtAnyType.java",
    "epp/XjcEppExtErrValueType.java",
    "epp/XjcEppExtURIType.java",
    "epp/XjcEppGreetingType.java",
    "epp/XjcEppLoginSvcType.java",
    "epp/XjcEppLoginType.java",
    "epp/XjcEppMixedMsgType.java",
    "epp/XjcEppMsgQType.java",
    "epp/XjcEppMsgType.java",
    "epp/XjcEppPollOpType.java",
    "epp/XjcEppPollType.java",
    "epp/XjcEppReadWriteType.java",
    "epp/XjcEppResponse.java",
    "epp/XjcEppResultType.java",
    "epp/XjcEppSvcMenuType.java",
    "epp/XjcEppTrIDType.java",
    "epp/XjcEppTransferOpType.java",
    "epp/XjcEppTransferType.java",
    "eppcom/ObjectFactory.java",
    "eppcom/XjcEppcomExtAuthInfoType.java",
    "eppcom/XjcEppcomPwAuthInfoType.java",
    "eppcom/XjcEppcomReasonType.java",
    "eppcom/XjcEppcomTrStatusType.java",
    "fee06/ObjectFactory.java",
    "fee06/XjcFee06Check.java",
    "fee06/XjcFee06ChkData.java",
    "fee06/XjcFee06CommandType.java",
    "fee06/XjcFee06CreData.java",
    "fee06/XjcFee06Create.java",
    "fee06/XjcFee06CreditType.java",
    "fee06/XjcFee06DelData.java",
    "fee06/XjcFee06DomainCDType.java",
    "fee06/XjcFee06DomainCheckType.java",
    "fee06/XjcFee06FeeType.java",
    "fee06/XjcFee06InfData.java",
    "fee06/XjcFee06Info.java",
    "fee06/XjcFee06RenData.java",
    "fee06/XjcFee06Renew.java",
    "fee06/XjcFee06Transfer.java",
    "fee06/XjcFee06TransformCommandType.java",
    "fee06/XjcFee06TransformResultType.java",
    "fee06/XjcFee06TrnData.java",
    "fee06/XjcFee06UpdData.java",
    "fee06/XjcFee06Update.java",
    "fee11/ObjectFactory.java",
    "fee11/XjcFee11Check.java",
    "fee11/XjcFee11ChkData.java",
    "fee11/XjcFee11CommandType.java",
    "fee11/XjcFee11CreData.java",
    "fee11/XjcFee11Create.java",
    "fee11/XjcFee11CreditType.java",
    "fee11/XjcFee11DelData.java",
    "fee11/XjcFee11FeeType.java",
    "fee11/XjcFee11ObjectCDType.java",
    "fee11/XjcFee11RenData.java",
    "fee11/XjcFee11Renew.java",
    "fee11/XjcFee11Transfer.java",
    "fee11/XjcFee11TransformCommandType.java",
    "fee11/XjcFee11TransformResultType.java",
    "fee11/XjcFee11TrnData.java",
    "fee11/XjcFee11UpdData.java",
    "fee11/XjcFee11Update.java",
    "fee12/ObjectFactory.java",
    "fee12/XjcFee12Check.java",
    "fee12/XjcFee12ChkData.java",
    "fee12/XjcFee12CommandCDType.java",
    "fee12/XjcFee12CommandCheckType.java",
    "fee12/XjcFee12CreData.java",
    "fee12/XjcFee12Create.java",
    "fee12/XjcFee12CreditType.java",
    "fee12/XjcFee12DelData.java",
    "fee12/XjcFee12FeeType.java",
    "fee12/XjcFee12ObjectCDType.java",
    "fee12/XjcFee12RenData.java",
    "fee12/XjcFee12Renew.java",
    "fee12/XjcFee12Transfer.java",
    "fee12/XjcFee12TransformCommandType.java",
    "fee12/XjcFee12TransformResultType.java",
    "fee12/XjcFee12TrnData.java",
    "fee12/XjcFee12UpdData.java",
    "fee12/XjcFee12Update.java",
    "host/ObjectFactory.java",
    "host/XjcHostAddRemType.java",
    "host/XjcHostAddrType.java",
    "host/XjcHostCheck.java",
    "host/XjcHostCheckNameType.java",
    "host/XjcHostCheckType.java",
    "host/XjcHostChgType.java",
    "host/XjcHostChkData.java",
    "host/XjcHostCreData.java",
    "host/XjcHostCreate.java",
    "host/XjcHostDelete.java",
    "host/XjcHostInfData.java",
    "host/XjcHostInfo.java",
    "host/XjcHostIpType.java",
    "host/XjcHostPaNameType.java",
    "host/XjcHostPanData.java",
    "host/XjcHostSNameType.java",
    "host/XjcHostStatusType.java",
    "host/XjcHostStatusValueType.java",
    "host/XjcHostUpdate.java",
    "iirdea/ObjectFactory.java",
    "iirdea/XjcIirdeaCode.java",
    "iirdea/XjcIirdeaResponse.java",
    "iirdea/XjcIirdeaResponseElement.java",
    "iirdea/XjcIirdeaResult.java",
    "launch/ObjectFactory.java",
    "launch/XjcLaunchCdNameType.java",
    "launch/XjcLaunchCdType.java",
    "launch/XjcLaunchCheck.java",
    "launch/XjcLaunchCheckFormType.java",
    "launch/XjcLaunchChkData.java",
    "launch/XjcLaunchClaimKeyType.java",
    "launch/XjcLaunchCodeMarkType.java",
    "launch/XjcLaunchCodeType.java",
    "launch/XjcLaunchCreData.java",
    "launch/XjcLaunchCreate.java",
    "launch/XjcLaunchCreateNoticeType.java",
    "launch/XjcLaunchDelete.java",
    "launch/XjcLaunchIdContainerType.java",
    "launch/XjcLaunchInfData.java",
    "launch/XjcLaunchInfo.java",
    "launch/XjcLaunchNoticeIDType.java",
    "launch/XjcLaunchObjectType.java",
    "launch/XjcLaunchPhaseType.java",
    "launch/XjcLaunchPhaseTypeValue.java",
    "launch/XjcLaunchStatusType.java",
    "launch/XjcLaunchStatusValueType.java",
    "launch/XjcLaunchUpdate.java",
    "mark/ObjectFactory.java",
    "mark/XjcMarkAbstractMark.java",
    "mark/XjcMarkAbstractMarkType.java",
    "mark/XjcMarkAddrType.java",
    "mark/XjcMarkContactType.java",
    "mark/XjcMarkContactTypeType.java",
    "mark/XjcMarkCourtType.java",
    "mark/XjcMarkE164Type.java",
    "mark/XjcMarkEntitlementType.java",
    "mark/XjcMarkHolderType.java",
    "mark/XjcMarkMark.java",
    "mark/XjcMarkMarkType.java",
    "mark/XjcMarkProtectionType.java",
    "mark/XjcMarkTrademarkType.java",
    "mark/XjcMarkTreatyOrStatuteType.java",
    "rde/ObjectFactory.java",
    "rde/XjcRdeContent.java",
    "rde/XjcRdeContentType.java",
    "rde/XjcRdeContentsType.java",
    "rde/XjcRdeDelete.java",
    "rde/XjcRdeDeleteType.java",
    "rde/XjcRdeDeletesType.java",
    "rde/XjcRdeDeposit.java",
    "rde/XjcRdeDepositTypeType.java",
    "rde/XjcRdeMenuType.java",
    "rde/XjcRdeRrType.java",
    "rdecontact/ObjectFactory.java",
    "rdecontact/XjcRdeContact.java",
    "rdecontact/XjcRdeContactAbstract.java",
    "rdecontact/XjcRdeContactDelete.java",
    "rdecontact/XjcRdeContactDeleteType.java",
    "rdecontact/XjcRdeContactElement.java",
    "rdecontact/XjcRdeContactTransferDataType.java",
    "rdedomain/ObjectFactory.java",
    "rdedomain/XjcRdeDomain.java",
    "rdedomain/XjcRdeDomainAbstract.java",
    "rdedomain/XjcRdeDomainDelete.java",
    "rdedomain/XjcRdeDomainDeleteType.java",
    "rdedomain/XjcRdeDomainElement.java",
    "rdedomain/XjcRdeDomainTransferDataType.java",
    "rdeeppparams/ObjectFactory.java",
    "rdeeppparams/XjcRdeEppParams.java",
    "rdeeppparams/XjcRdeEppParamsAbstract.java",
    "rdeeppparams/XjcRdeEppParamsElement.java",
    "rdeheader/ObjectFactory.java",
    "rdeheader/XjcRdeHeader.java",
    "rdeheader/XjcRdeHeaderCount.java",
    "rdeheader/XjcRdeHeaderElement.java",
    "rdehost/ObjectFactory.java",
    "rdehost/XjcRdeHost.java",
    "rdehost/XjcRdeHostAbstractHost.java",
    "rdehost/XjcRdeHostDelete.java",
    "rdehost/XjcRdeHostDeleteType.java",
    "rdehost/XjcRdeHostElement.java",
    "rdeidn/ObjectFactory.java",
    "rdeidn/XjcRdeIdn.java",
    "rdeidn/XjcRdeIdnDelete.java",
    "rdeidn/XjcRdeIdnDeleteType.java",
    "rdeidn/XjcRdeIdnElement.java",
    "rdenndn/ObjectFactory.java",
    "rdenndn/XjcRdeNndn.java",
    "rdenndn/XjcRdeNndnAbstract.java",
    "rdenndn/XjcRdeNndnDelete.java",
    "rdenndn/XjcRdeNndnDeleteType.java",
    "rdenndn/XjcRdeNndnElement.java",
    "rdenndn/XjcRdeNndnNameState.java",
    "rdenndn/XjcRdeNndnNameStateValue.java",
    "rdenotification/ObjectFactory.java",
    "rdenotification/XjcRdeNotification.java",
    "rdenotification/XjcRdeNotificationElement.java",
    "rdenotification/XjcRdeNotificationName.java",
    "rdenotification/XjcRdeNotificationStatusType.java",
    "rdepolicy/ObjectFactory.java",
    "rdepolicy/XjcRdePolicy.java",
    "rdepolicy/XjcRdePolicyElement.java",
    "rderegistrar/ObjectFactory.java",
    "rderegistrar/XjcRdeRegistrar.java",
    "rderegistrar/XjcRdeRegistrarAbstract.java",
    "rderegistrar/XjcRdeRegistrarAddrType.java",
    "rderegistrar/XjcRdeRegistrarDelete.java",
    "rderegistrar/XjcRdeRegistrarDeleteType.java",
    "rderegistrar/XjcRdeRegistrarElement.java",
    "rderegistrar/XjcRdeRegistrarPostalInfoEnumType.java",
    "rderegistrar/XjcRdeRegistrarPostalInfoType.java",
    "rderegistrar/XjcRdeRegistrarStatusType.java",
    "rderegistrar/XjcRdeRegistrarWhoisInfoType.java",
    "rdereport/ObjectFactory.java",
    "rdereport/XjcRdeReport.java",
    "rdereport/XjcRdeReportReport.java",
    "rgp/ObjectFactory.java",
    "rgp/XjcRgpInfData.java",
    "rgp/XjcRgpMixedType.java",
    "rgp/XjcRgpOpType.java",
    "rgp/XjcRgpReportTextType.java",
    "rgp/XjcRgpReportType.java",
    "rgp/XjcRgpRespDataType.java",
    "rgp/XjcRgpRestoreType.java",
    "rgp/XjcRgpStatusType.java",
    "rgp/XjcRgpStatusValueType.java",
    "rgp/XjcRgpUpData.java",
    "rgp/XjcRgpUpdate.java",
    "secdns/ObjectFactory.java",
    "secdns/XjcSecdnsChgType.java",
    "secdns/XjcSecdnsCreate.java",
    "secdns/XjcSecdnsDsDataType.java",
    "secdns/XjcSecdnsDsOrKeyType.java",
    "secdns/XjcSecdnsInfData.java",
    "secdns/XjcSecdnsKeyDataType.java",
    "secdns/XjcSecdnsRemType.java",
    "secdns/XjcSecdnsUpdate.java",
    "smd/ObjectFactory.java",
    "smd/XjcSmdAbstractSignedMark.java",
    "smd/XjcSmdAbstractSignedMarkElement.java",
    "smd/XjcSmdEncodedSignedMark.java",
    "smd/XjcSmdIssuerInfo.java",
    "smd/XjcSmdSignedMark.java",
    "smd/XjcSmdSignedMarkElement.java",
]
