// Copyright 2019 The Nomulus Authors. All Rights Reserved.
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

package google.registry.webdriver;

import java.io.IOException;
import java.io.UncheckedIOException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeDriverService;
import org.openqa.selenium.chrome.ChromeOptions;

/** Implementation of {@link WebDriverPlusScreenDiffer} that uses {@link ChromeDriver}. */
class ChromeWebDriverPlusScreenDiffer implements WebDriverPlusScreenDiffer {

  private static final ChromeDriverService chromeDriverService = createChromeDriverService();
  private final WebDriver webDriver;

  public ChromeWebDriverPlusScreenDiffer() {
    ChromeOptions chromeOptions = new ChromeOptions();
    chromeOptions.setHeadless(true);
    webDriver = new ChromeDriver(chromeDriverService, chromeOptions);
  }

  private static ChromeDriverService createChromeDriverService() {
    ChromeDriverService chromeDriverService =
        new ChromeDriverService.Builder().usingAnyFreePort().build();
    try {
      chromeDriverService.start();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    Runtime.getRuntime().addShutdownHook(new Thread(chromeDriverService::stop));
    return chromeDriverService;
  }

  @Override
  public WebDriver getWebDriver() {
    return webDriver;
  }

  @Override
  public void diffElement(String imageKey, WebElement element) {
    // TODO(b/122650673): Add implementation for screenshots comparison. It is no-op
    //   right now to prevent the test failure.
  }

  @Override
  public void diffPage(String imageKey) {
    // TODO(b/122650673): Add implementation for screenshots comparison. It is no-op
    //   right now to prevent the test failure.
  }

  @Override
  public void verifyAndQuit() {
    // TODO(b/122650673): Add implementation for screenshots comparison. It is no-op
    //   right now to prevent the test failure.
  }
}
