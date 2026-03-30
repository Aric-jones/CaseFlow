package com.caseflow.engine.ui;

import org.springframework.stereotype.Component;

@Component
public class UiDriverFactory {

    public UiBrowserDriver create(String driverType, String browserType,
                                  boolean headless, int width, int height) {
        if ("SELENIUM".equalsIgnoreCase(driverType)) {
            return new SeleniumDriver(browserType, headless, width, height);
        }
        return new PlaywrightDriver(browserType, headless, width, height);
    }
}
