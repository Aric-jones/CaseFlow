package com.caseflow.engine.ui;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.SelectOption;
import com.microsoft.playwright.options.WaitForSelectorState;
import lombok.extern.slf4j.Slf4j;

import java.nio.file.Paths;

@Slf4j
public class PlaywrightDriver implements UiBrowserDriver {

    private final Playwright playwright;
    private final Browser browser;
    private final BrowserContext context;
    private final Page page;

    public PlaywrightDriver(String browserType, boolean headless, int width, int height) {
        this.playwright = Playwright.create();
        BrowserType bt;
        switch (browserType.toUpperCase()) {
            case "FIREFOX": bt = playwright.firefox(); break;
            case "WEBKIT": bt = playwright.webkit(); break;
            default: bt = playwright.chromium(); break;
        }
        this.browser = bt.launch(new BrowserType.LaunchOptions().setHeadless(headless));
        this.context = browser.newContext(new Browser.NewContextOptions()
                .setViewportSize(width, height));
        this.page = context.newPage();
    }

    private Locator locate(String locatorType, String locatorValue) {
        switch (locatorType.toUpperCase()) {
            case "XPATH": return page.locator("xpath=" + locatorValue);
            case "ID": return page.locator("#" + locatorValue);
            case "NAME": return page.locator("[name=\"" + locatorValue + "\"]");
            case "LINK_TEXT": return page.getByText(locatorValue);
            case "TAG_NAME": return page.locator(locatorValue);
            default: return page.locator(locatorValue);
        }
    }

    @Override
    public void navigate(String url) {
        page.navigate(url);
    }

    @Override
    public void click(String locatorType, String locatorValue) {
        locate(locatorType, locatorValue).first().click();
    }

    @Override
    public void doubleClick(String locatorType, String locatorValue) {
        locate(locatorType, locatorValue).first().dblclick();
    }

    @Override
    public void rightClick(String locatorType, String locatorValue) {
        locate(locatorType, locatorValue).first()
                .click(new Locator.ClickOptions().setButton(com.microsoft.playwright.options.MouseButton.RIGHT));
    }

    @Override
    public void type(String locatorType, String locatorValue, String text) {
        Locator el = locate(locatorType, locatorValue).first();
        el.clear();
        el.fill(text);
    }

    @Override
    public void clear(String locatorType, String locatorValue) {
        locate(locatorType, locatorValue).first().clear();
    }

    @Override
    public String getText(String locatorType, String locatorValue) {
        return locate(locatorType, locatorValue).first().textContent();
    }

    @Override
    public String getAttribute(String locatorType, String locatorValue, String attr) {
        return locate(locatorType, locatorValue).first().getAttribute(attr);
    }

    @Override
    public boolean isVisible(String locatorType, String locatorValue) {
        try {
            return locate(locatorType, locatorValue).first().isVisible();
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public boolean isEnabled(String locatorType, String locatorValue) {
        try {
            return locate(locatorType, locatorValue).first().isEnabled();
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public boolean elementExists(String locatorType, String locatorValue) {
        try {
            return locate(locatorType, locatorValue).count() > 0;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public byte[] screenshot() {
        return page.screenshot(new Page.ScreenshotOptions().setFullPage(true));
    }

    @Override
    public byte[] screenshotElement(String locatorType, String locatorValue) {
        return locate(locatorType, locatorValue).first().screenshot();
    }

    @Override
    public void hover(String locatorType, String locatorValue) {
        locate(locatorType, locatorValue).first().hover();
    }

    @Override
    public void selectByText(String locatorType, String locatorValue, String text) {
        locate(locatorType, locatorValue).first().selectOption(new SelectOption().setLabel(text));
    }

    @Override
    public void waitForVisible(String locatorType, String locatorValue, long timeoutMs) {
        locate(locatorType, locatorValue).first().waitFor(new Locator.WaitForOptions()
                .setState(WaitForSelectorState.VISIBLE).setTimeout(timeoutMs));
    }

    @Override
    public void waitForHidden(String locatorType, String locatorValue, long timeoutMs) {
        locate(locatorType, locatorValue).first().waitFor(new Locator.WaitForOptions()
                .setState(WaitForSelectorState.HIDDEN).setTimeout(timeoutMs));
    }

    @Override
    public void waitForClickable(String locatorType, String locatorValue, long timeoutMs) {
        locate(locatorType, locatorValue).first().waitFor(new Locator.WaitForOptions()
                .setState(WaitForSelectorState.VISIBLE).setTimeout(timeoutMs));
    }

    @Override
    public void waitForPresent(String locatorType, String locatorValue, long timeoutMs) {
        locate(locatorType, locatorValue).first().waitFor(new Locator.WaitForOptions()
                .setState(WaitForSelectorState.ATTACHED).setTimeout(timeoutMs));
    }

    @Override
    public void setImplicitWait(long timeoutMs) {
        page.setDefaultTimeout(timeoutMs);
    }

    @Override
    public Object executeScript(String script) {
        return page.evaluate(script);
    }

    @Override
    public String getCurrentUrl() {
        return page.url();
    }

    @Override
    public String getTitle() {
        return page.title();
    }

    @Override
    public void switchToFrame(String locatorType, String locatorValue) {
        // Playwright handles frames via frameLocator
        log.info("switchToFrame: {}, {}", locatorType, locatorValue);
    }

    @Override
    public void switchToDefaultContent() {
        // No-op for Playwright since frames are accessed via locator chain
    }

    @Override
    public void scroll(int x, int y) {
        page.evaluate("window.scrollBy(" + x + "," + y + ")");
    }

    @Override
    public void keyPress(String keys) {
        page.keyboard().press(keys);
    }

    @Override
    public void uploadFile(String locatorType, String locatorValue, String filePath) {
        locate(locatorType, locatorValue).first().setInputFiles(Paths.get(filePath));
    }

    @Override
    public void close() {
        try { context.close(); } catch (Exception ignored) {}
        try { browser.close(); } catch (Exception ignored) {}
        try { playwright.close(); } catch (Exception ignored) {}
    }
}
