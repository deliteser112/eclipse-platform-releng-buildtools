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

package google.registry.tmch;

import static google.registry.tmch.TmchTestData.loadSmd;

import google.registry.config.RegistryConfig.ConfigModule.TmchCaMode;
import google.registry.testing.AppEngineRule;
import google.registry.testing.ExceptionRule;
import google.registry.testing.FakeClock;
import google.registry.testing.InjectRule;
import java.security.SignatureException;
import java.security.cert.CertificateExpiredException;
import java.security.cert.CertificateNotYetValidException;
import java.security.cert.CertificateRevokedException;
import javax.xml.crypto.dsig.XMLSignatureException;
import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Unit tests for {@link TmchXmlSignature}.
 *
 * <p>This class does not test the {@code revoked/smd/} folder because it's not a crypto issue.
 */
@RunWith(JUnit4.class)
public class TmchXmlSignatureTest {

  @Rule
  public final AppEngineRule appEngine = AppEngineRule.builder()
      .withDatastore()
      .build();

  @Rule
  public final ExceptionRule thrown = new ExceptionRule();

  @Rule
  public final InjectRule inject = new InjectRule();

  private final FakeClock clock = new FakeClock(DateTime.parse("2013-11-24T23:15:37.4Z"));
  private byte[] smdData;
  private TmchXmlSignature tmchXmlSignature;

  @Before
  public void before() throws Exception {
    inject.setStaticField(TmchCertificateAuthority.class, "clock", clock);
    tmchXmlSignature = new TmchXmlSignature(new TmchCertificateAuthority(TmchCaMode.PILOT));
  }

  public void wrongCertificateAuthority() throws Exception {
    tmchXmlSignature = new TmchXmlSignature(new TmchCertificateAuthority(TmchCaMode.PRODUCTION));
    smdData = loadSmd("active/Court-Agent-Arabic-Active.smd");
    thrown.expectRootCause(SignatureException.class, "Signature does not match");
    tmchXmlSignature.verify(smdData);
  }

  @Test
  public void timeTravelBeforeCertificateWasCreated() throws Exception {
    smdData = loadSmd("active/Court-Agent-Arabic-Active.smd");
    clock.setTo(DateTime.parse("2013-05-01T00:00:00Z"));
    thrown.expectRootCause(CertificateNotYetValidException.class);
    tmchXmlSignature.verify(smdData);
  }

  @Test
  public void timeTravelAfterCertificateHasExpired() throws Exception {
    smdData = loadSmd("active/Court-Agent-Arabic-Active.smd");
    clock.setTo(DateTime.parse("2023-06-01T00:00:00Z"));
    thrown.expectRootCause(CertificateExpiredException.class);
    tmchXmlSignature.verify(smdData);
  }

  @Test
  public void testActiveCourtAgentArabicActive() throws Exception {
    smdData = loadSmd("active/Court-Agent-Arabic-Active.smd");
    tmchXmlSignature.verify(smdData);
  }

  @Test
  public void testActiveCourtAgentChineseActive() throws Exception {
    smdData = loadSmd("active/Court-Agent-Chinese-Active.smd");
    tmchXmlSignature.verify(smdData);
  }

  @Test
  public void testActiveCourtAgentEnglishActive() throws Exception {
    smdData = loadSmd("active/Court-Agent-English-Active.smd");
    tmchXmlSignature.verify(smdData);
  }

  @Test
  public void testActiveCourtAgentFrenchActive() throws Exception {
    smdData = loadSmd("active/Court-Agent-French-Active.smd");
    tmchXmlSignature.verify(smdData);
  }

  @Test
  public void testActiveCourtAgentRussianActive() throws Exception {
    smdData = loadSmd("active/Court-Agent-Russian-Active.smd");
    tmchXmlSignature.verify(smdData);
  }

  @Test
  public void testActiveCourtHolderArabicActive() throws Exception {
    smdData = loadSmd("active/Court-Holder-Arabic-Active.smd");
    tmchXmlSignature.verify(smdData);
  }

  @Test
  public void testActiveCourtHolderChineseActive() throws Exception {
    smdData = loadSmd("active/Court-Holder-Chinese-Active.smd");
    tmchXmlSignature.verify(smdData);
  }

  @Test
  public void testActiveCourtHolderEnglishActive() throws Exception {
    smdData = loadSmd("active/Court-Holder-English-Active.smd");
    tmchXmlSignature.verify(smdData);
  }

  @Test
  public void testActiveCourtHolderFrenchActive() throws Exception {
    smdData = loadSmd("active/Court-Holder-French-Active.smd");
    tmchXmlSignature.verify(smdData);
  }

  @Test
  public void testActiveCourtHolderRussianActive() throws Exception {
    smdData = loadSmd("active/Court-Holder-Russian-Active.smd");
    tmchXmlSignature.verify(smdData);
  }

  @Test
  public void testActiveTrademarkAgentArabicActive() throws Exception {
    smdData = loadSmd("active/Trademark-Agent-Arabic-Active.smd");
    tmchXmlSignature.verify(smdData);
  }

  @Test
  public void testActiveTrademarkAgentChineseActive() throws Exception {
    smdData = loadSmd("active/Trademark-Agent-Chinese-Active.smd");
    tmchXmlSignature.verify(smdData);
  }

  @Test
  public void testActiveTrademarkAgentEnglishActive() throws Exception {
    smdData = loadSmd("active/Trademark-Agent-English-Active.smd");
    tmchXmlSignature.verify(smdData);
  }

  @Test
  public void testActiveTrademarkAgentFrenchActive() throws Exception {
    smdData = loadSmd("active/Trademark-Agent-French-Active.smd");
    tmchXmlSignature.verify(smdData);
  }

  @Test
  public void testActiveTrademarkAgentRussianActive() throws Exception {
    smdData = loadSmd("active/Trademark-Agent-Russian-Active.smd");
    tmchXmlSignature.verify(smdData);
  }

  @Test
  public void testActiveTrademarkHolderArabicActive() throws Exception {
    smdData = loadSmd("active/Trademark-Holder-Arabic-Active.smd");
    tmchXmlSignature.verify(smdData);
  }

  @Test
  public void testActiveTrademarkHolderChineseActive() throws Exception {
    smdData = loadSmd("active/Trademark-Holder-Chinese-Active.smd");
    tmchXmlSignature.verify(smdData);
  }

  @Test
  public void testActiveTrademarkHolderEnglishActive() throws Exception {
    smdData = loadSmd("active/Trademark-Holder-English-Active.smd");
    tmchXmlSignature.verify(smdData);
  }

  @Test
  public void testActiveTrademarkHolderFrenchActive() throws Exception {
    smdData = loadSmd("active/Trademark-Holder-French-Active.smd");
    tmchXmlSignature.verify(smdData);
  }

  @Test
  public void testActiveTrademarkHolderRussianActive() throws Exception {
    smdData = loadSmd("active/Trademark-Holder-Russian-Active.smd");
    tmchXmlSignature.verify(smdData);
  }

  @Test
  public void testActiveTreatystatuteAgentArabicActive() throws Exception {
    smdData = loadSmd("active/TreatyStatute-Agent-Arabic-Active.smd");
    tmchXmlSignature.verify(smdData);
  }

  @Test
  public void testActiveTreatystatuteAgentChineseActive() throws Exception {
    smdData = loadSmd("active/TreatyStatute-Agent-Chinese-Active.smd");
    tmchXmlSignature.verify(smdData);
  }

  @Test
  public void testActiveTreatystatuteAgentEnglishActive() throws Exception {
    smdData = loadSmd("active/TreatyStatute-Agent-English-Active.smd");
    tmchXmlSignature.verify(smdData);
  }

  @Test
  public void testActiveTreatystatuteAgentFrenchActive() throws Exception {
    smdData = loadSmd("active/TreatyStatute-Agent-French-Active.smd");
    tmchXmlSignature.verify(smdData);
  }

  @Test
  public void testActiveTreatystatuteAgentRussianActive() throws Exception {
    smdData = loadSmd("active/TreatyStatute-Agent-Russian-Active.smd");
    tmchXmlSignature.verify(smdData);
  }

  @Test
  public void testActiveTreatystatuteHolderArabicActive() throws Exception {
    smdData = loadSmd("active/TreatyStatute-Holder-Arabic-Active.smd");
    tmchXmlSignature.verify(smdData);
  }

  @Test
  public void testActiveTreatystatuteHolderChineseActive() throws Exception {
    smdData = loadSmd("active/TreatyStatute-Holder-Chinese-Active.smd");
    tmchXmlSignature.verify(smdData);
  }

  @Test
  public void testActiveTreatystatuteHolderEnglishActive() throws Exception {
    smdData = loadSmd("active/TreatyStatute-Holder-English-Active.smd");
    tmchXmlSignature.verify(smdData);
  }

  @Test
  public void testActiveTreatystatuteHolderFrenchActive() throws Exception {
    smdData = loadSmd("active/TreatyStatute-Holder-French-Active.smd");
    tmchXmlSignature.verify(smdData);
  }

  @Test
  public void testActiveTreatystatuteHolderRussianActive() throws Exception {
    smdData = loadSmd("active/TreatyStatute-Holder-Russian-Active.smd");
    tmchXmlSignature.verify(smdData);
  }

  @Test
  public void testInvalidInvalidsignatureCourtAgentFrenchActive() throws Exception {
    smdData = loadSmd("invalid/InvalidSignature-Court-Agent-French-Active.smd");
    thrown.expect(XMLSignatureException.class);
    tmchXmlSignature.verify(smdData);
  }

  @Test
  public void testInvalidInvalidsignatureTrademarkAgentEnglishActive() throws Exception {
    smdData = loadSmd("invalid/InvalidSignature-Trademark-Agent-English-Active.smd");
    thrown.expect(XMLSignatureException.class);
    tmchXmlSignature.verify(smdData);
  }

  @Test
  public void testInvalidInvalidsignatureTrademarkAgentRussianActive() throws Exception {
    smdData = loadSmd("invalid/InvalidSignature-Trademark-Agent-Russian-Active.smd");
    thrown.expect(XMLSignatureException.class);
    tmchXmlSignature.verify(smdData);
  }

  @Test
  public void testInvalidInvalidsignatureTreatystatuteAgentChineseActive() throws Exception {
    smdData = loadSmd("invalid/InvalidSignature-TreatyStatute-Agent-Chinese-Active.smd");
    thrown.expect(XMLSignatureException.class);
    tmchXmlSignature.verify(smdData);
  }

  @Test
  public void testInvalidInvalidsignatureTreatystatuteAgentEnglishActive() throws Exception {
    smdData = loadSmd("invalid/InvalidSignature-TreatyStatute-Agent-English-Active.smd");
    thrown.expect(XMLSignatureException.class);
    tmchXmlSignature.verify(smdData);
  }

  @Test
  public void testRevokedTmvTmvrevokedCourtAgentFrenchActive() throws Exception {
    smdData = loadSmd("revoked/tmv/TMVRevoked-Court-Agent-French-Active.smd");
    thrown.expectRootCause(CertificateRevokedException.class, "KEY_COMPROMISE");
    tmchXmlSignature.verify(smdData);
  }

  @Test
  public void testRevokedTmvTmvrevokedTrademarkAgentEnglishActive() throws Exception {
    smdData = loadSmd("revoked/tmv/TMVRevoked-Trademark-Agent-English-Active.smd");
    thrown.expectRootCause(CertificateRevokedException.class, "KEY_COMPROMISE");
    tmchXmlSignature.verify(smdData);
  }

  @Test
  public void testRevokedTmvTmvrevokedTrademarkAgentRussianActive() throws Exception {
    smdData = loadSmd("revoked/tmv/TMVRevoked-Trademark-Agent-Russian-Active.smd");
    thrown.expectRootCause(CertificateRevokedException.class, "KEY_COMPROMISE");
    tmchXmlSignature.verify(smdData);
  }

  @Test
  public void testRevokedTmvTmvrevokedTreatystatuteAgentChineseActive() throws Exception {
    smdData = loadSmd("revoked/tmv/TMVRevoked-TreatyStatute-Agent-Chinese-Active.smd");
    thrown.expectRootCause(CertificateRevokedException.class, "KEY_COMPROMISE");
    tmchXmlSignature.verify(smdData);
  }

  @Test
  public void testRevokedTmvTmvrevokedTreatystatuteAgentEnglishActive() throws Throwable {
    smdData = loadSmd("revoked/tmv/TMVRevoked-TreatyStatute-Agent-English-Active.smd");
    thrown.expectRootCause(CertificateRevokedException.class, "KEY_COMPROMISE");
    tmchXmlSignature.verify(smdData);
  }
}
