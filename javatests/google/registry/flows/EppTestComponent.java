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

package google.registry.flows;

import static org.mockito.Mockito.mock;

import dagger.Component;
import dagger.Module;
import dagger.Provides;
import dagger.Subcomponent;
import google.registry.monitoring.whitebox.EppMetrics;
import google.registry.request.RequestScope;
import google.registry.testing.FakeClock;
import google.registry.util.Clock;
import javax.inject.Singleton;

/** Dagger component for running EPP tests. */
@Singleton
@Component(
    modules = {
        EppTestComponent.FakesAndMocksModule.class
    })
interface EppTestComponent {

  RequestComponent startRequest();

  /** Module for injecting fakes and mocks. */
  @Module
  static class FakesAndMocksModule {
    final FakeClock clock;
    final EppMetrics metrics;

    FakesAndMocksModule(FakeClock clock) {
      this.clock = clock;
      this.metrics = mock(EppMetrics.class);
    }

    @Provides
    Clock provideClock() {
      return clock;
    }

    @Provides
    EppMetrics provideMetrics() {
      return metrics;
    }
  }

  /** Subcomponent for request scoped injections. */
  @RequestScope
  @Subcomponent
  interface RequestComponent {
    EppController eppController();
    FlowComponent.Builder flowComponentBuilder();
  }
}
