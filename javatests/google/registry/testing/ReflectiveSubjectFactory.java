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

import com.google.common.truth.FailureStrategy;
import com.google.common.truth.Subject;
import com.google.common.truth.SubjectFactory;
import google.registry.util.TypeUtils.TypeInstantiator;

/** Helper to reduce boilerplate in making new Truth subject classes. */
public class ReflectiveSubjectFactory<T, S extends Subject<S, T>> extends SubjectFactory<S, T> {
  @Override
  public S getSubject(FailureStrategy strategy, T subject) {
    try {
      Class<S> sType = new TypeInstantiator<S>(getClass()){}.getExactType();
      return sType
          .getConstructor(FailureStrategy.class, subject.getClass())
          .newInstance(strategy, subject);
    } catch (ReflectiveOperationException e) {
      throw new RuntimeException(e);
    }
  }
}

