// Copyright 2023 The Nomulus Authors. All Rights Reserved.
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

import { NgModule } from '@angular/core';
import { BrowserModule } from '@angular/platform-browser';
import { FormsModule } from '@angular/forms';

import { AppRoutingModule } from './app-routing.module';
import { AppComponent } from './app.component';
import { BrowserAnimationsModule } from '@angular/platform-browser/animations';
import { MaterialModule } from './material.module';

import { BackendService } from './shared/services/backend.service';

import { HomeComponent } from './home/home.component';
import { TldsComponent } from './tlds/tlds.component';
import { HeaderComponent } from './header/header.component';
import { SettingsComponent } from './settings/settings.component';
import SettingsContactComponent, {
  ContactDetailsDialogComponent,
} from './settings/contact/contact.component';
import { HttpClientModule } from '@angular/common/http';
import { RegistrarComponent } from './registrar/registrarsTable.component';
import { RegistrarGuard } from './registrar/registrar.guard';
import SecurityComponent from './settings/security/security.component';
import { MAT_FORM_FIELD_DEFAULT_OPTIONS } from '@angular/material/form-field';
import { EmptyRegistrar } from './registrar/emptyRegistrar.component';
import { RegistrarSelectorComponent } from './registrar/registrarSelector.component';
import { GlobalLoaderService } from './shared/services/globalLoader.service';
import { ContactWidgetComponent } from './home/widgets/contactWidget.component';
import { PromotionsWidgetComponent } from './home/widgets/promotionsWidget.component';
import { TldsWidgetComponent } from './home/widgets/tldsWidget.component';
import { ResourcesWidgetComponent } from './home/widgets/resourcesWidget.component';
import { EppWidgetComponent } from './home/widgets/eppWidget.component';
import { BillingWidgetComponent } from './home/widgets/billingWidget.component';
import { DomainsWidgetComponent } from './home/widgets/domainsWidget.component';
import { SettingsWidgetComponent } from './home/widgets/settingsWidget.component';
import { UserDataService } from './shared/services/userData.service';
import WhoisComponent from './settings/whois/whois.component';
import { SnackBarModule } from './snackbar.module';
import { RegistrarDetailsComponent } from './registrar/registrarDetails.component';
import { DomainListComponent } from './domains/domainList.component';
import { DialogBottomSheetWrapper } from './shared/components/dialogBottomSheet.component';

@NgModule({
  declarations: [
    AppComponent,
    DialogBottomSheetWrapper,
    BillingWidgetComponent,
    ContactDetailsDialogComponent,
    ContactWidgetComponent,
    DomainListComponent,
    DomainsWidgetComponent,
    EmptyRegistrar,
    EppWidgetComponent,
    HeaderComponent,
    HomeComponent,
    PromotionsWidgetComponent,
    RegistrarComponent,
    RegistrarDetailsComponent,
    RegistrarSelectorComponent,
    ResourcesWidgetComponent,
    SecurityComponent,
    SettingsComponent,
    SettingsContactComponent,
    SettingsWidgetComponent,
    TldsComponent,
    TldsWidgetComponent,
    WhoisComponent,
  ],
  imports: [
    AppRoutingModule,
    BrowserAnimationsModule,
    BrowserModule,
    FormsModule,
    HttpClientModule,
    MaterialModule,
    SnackBarModule,
  ],
  providers: [
    BackendService,
    GlobalLoaderService,
    RegistrarGuard,
    UserDataService,
    {
      provide: MAT_FORM_FIELD_DEFAULT_OPTIONS,
      useValue: {
        subscriptSizing: 'dynamic',
      },
    },
  ],
  bootstrap: [AppComponent],
})
export class AppModule {}
