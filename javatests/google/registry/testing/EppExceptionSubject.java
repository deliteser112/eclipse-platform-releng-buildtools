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

package google.registry.testing;

import static com.google.common.truth.Truth.assertAbout;
import static com.google.common.truth.Truth.assertThat;
import static google.registry.flows.EppXmlTransformer.marshal;

import com.google.common.truth.AbstractVerb.DelegatedVerb;
import com.google.common.truth.FailureStrategy;
import com.google.common.truth.Subject;
import com.google.common.truth.SubjectFactory;
import google.registry.flows.EppException;
import google.registry.model.eppcommon.Trid;
import google.registry.model.eppoutput.EppOutput;
import google.registry.model.eppoutput.EppResponse;
import google.registry.testing.TruthChainer.And;
import google.registry.xml.ValidationMode;
import google.registry.xml.XmlException;

/** Utility methods for asserting things about {@link EppException} instances. */
public class EppExceptionSubject extends Subject<EppExceptionSubject, EppException> {

  public EppExceptionSubject(FailureStrategy strategy, EppException subject) {
    super(strategy, subject);
  }

  public And<EppExceptionSubject> hasMessage(String expected) {
    assertThat(actual()).hasMessage(expected);
    return new And<>(this);
  }

  public And<EppExceptionSubject> marshalsToXml() {
    // Attempt to marshal the exception to EPP. If it doesn't work, this will throw.
    try {
      marshal(
          EppOutput.create(new EppResponse.Builder()
              .setTrid(Trid.create(null))
              .setResult(actual().getResult())
              .build()),
          ValidationMode.STRICT);
    } catch (XmlException e) {
      fail("fails to marshal to XML: " + e.getMessage());
    }
    return new And<>(this);
  }

  public static DelegatedVerb<EppExceptionSubject, EppException> assertAboutEppExceptions() {
    return assertAbout(new SubjectFactory<EppExceptionSubject, EppException>() {
      @Override
      public EppExceptionSubject getSubject(FailureStrategy strategy, EppException subject) {
        return new EppExceptionSubject(strategy, subject);
      }});
  }
}
