package com.caseflow.engine.ui;

import io.github.bonigarcia.wdm.WebDriverManager;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.edge.EdgeDriver;
import org.openqa.selenium.edge.EdgeOptions;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxOptions;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.Select;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.io.File;
import java.time.Duration;

@Slf4j
public class SeleniumDriver implements UiBrowserDriver {

    private final WebDriver driver;

    public SeleniumDriver(String browserType, boolean headless, int width, int height) {
        this.driver = createDriver(browserType, headless, width, height);
    }

    private WebDriver createDriver(String browserType, boolean headless, int width, int height) {
        WebDriver wd;
        switch (browserType.toUpperCase()) {
            case "FIREFOX": {
                WebDriverManager.firefoxdriver().setup();
                FirefoxOptions opts = new FirefoxOptions();
                if (headless) opts.addArguments("--headless");
                wd = new FirefoxDriver(opts);
                break;
            }
            case "EDGE": {
                WebDriverManager.edgedriver().setup();
                EdgeOptions opts = new EdgeOptions();
                if (headless) opts.addArguments("--headless");
                wd = new EdgeDriver(opts);
                break;
            }
            default: {
                WebDriverManager.chromedriver().setup();
                ChromeOptions opts = new ChromeOptions();
                if (headless) opts.addArguments("--headless=new");
                opts.addArguments("--no-sandbox", "--disable-dev-shm-usage");
                wd = new ChromeDriver(opts);
                break;
            }
        }
        wd.manage().window().setSize(new Dimension(width, height));
        return wd;
    }

    private By toBy(String locatorType, String locatorValue) {
        switch (locatorType.toUpperCase()) {
            case "XPATH": return By.xpath(locatorValue);
            case "ID": return By.id(locatorValue);
            case "NAME": return By.name(locatorValue);
            case "LINK_TEXT": return By.linkText(locatorValue);
            case "TAG_NAME": return By.tagName(locatorValue);
            default: return By.cssSelector(locatorValue);
        }
    }

    private WebElement find(String locatorType, String locatorValue) {
        return driver.findElement(toBy(locatorType, locatorValue));
    }

    @Override
    public void navigate(String url) {
        driver.get(url);
    }

    @Override
    public void click(String locatorType, String locatorValue) {
        find(locatorType, locatorValue).click();
    }

    @Override
    public void doubleClick(String locatorType, String locatorValue) {
        new Actions(driver).doubleClick(find(locatorType, locatorValue)).perform();
    }

    @Override
    public void rightClick(String locatorType, String locatorValue) {
        new Actions(driver).contextClick(find(locatorType, locatorValue)).perform();
    }

    @Override
    public void type(String locatorType, String locatorValue, String text) {
        WebElement el = find(locatorType, locatorValue);
        el.clear();
        el.sendKeys(text);
    }

    @Override
    public void clear(String locatorType, String locatorValue) {
        find(locatorType, locatorValue).clear();
    }

    @Override
    public String getText(String locatorType, String locatorValue) {
        return find(locatorType, locatorValue).getText();
    }

    @Override
    public String getAttribute(String locatorType, String locatorValue, String attr) {
        return find(locatorType, locatorValue).getAttribute(attr);
    }

    @Override
    public boolean isVisible(String locatorType, String locatorValue) {
        try {
            return find(locatorType, locatorValue).isDisplayed();
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public boolean isEnabled(String locatorType, String locatorValue) {
        try {
            return find(locatorType, locatorValue).isEnabled();
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public boolean elementExists(String locatorType, String locatorValue) {
        try {
            return !driver.findElements(toBy(locatorType, locatorValue)).isEmpty();
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public byte[] screenshot() {
        return ((TakesScreenshot) driver).getScreenshotAs(OutputType.BYTES);
    }

    @Override
    public byte[] screenshotElement(String locatorType, String locatorValue) {
        return find(locatorType, locatorValue).getScreenshotAs(OutputType.BYTES);
    }

    @Override
    public void hover(String locatorType, String locatorValue) {
        new Actions(driver).moveToElement(find(locatorType, locatorValue)).perform();
    }

    @Override
    public void selectByText(String locatorType, String locatorValue, String text) {
        new Select(find(locatorType, locatorValue)).selectByVisibleText(text);
    }

    @Override
    public void waitForVisible(String locatorType, String locatorValue, long timeoutMs) {
        new WebDriverWait(driver, Duration.ofMillis(timeoutMs))
                .until(ExpectedConditions.visibilityOfElementLocated(toBy(locatorType, locatorValue)));
    }

    @Override
    public void waitForHidden(String locatorType, String locatorValue, long timeoutMs) {
        new WebDriverWait(driver, Duration.ofMillis(timeoutMs))
                .until(ExpectedConditions.invisibilityOfElementLocated(toBy(locatorType, locatorValue)));
    }

    @Override
    public void waitForClickable(String locatorType, String locatorValue, long timeoutMs) {
        new WebDriverWait(driver, Duration.ofMillis(timeoutMs))
                .until(ExpectedConditions.elementToBeClickable(toBy(locatorType, locatorValue)));
    }

    @Override
    public void waitForPresent(String locatorType, String locatorValue, long timeoutMs) {
        new WebDriverWait(driver, Duration.ofMillis(timeoutMs))
                .until(ExpectedConditions.presenceOfElementLocated(toBy(locatorType, locatorValue)));
    }

    @Override
    public void setImplicitWait(long timeoutMs) {
        driver.manage().timeouts().implicitlyWait(Duration.ofMillis(timeoutMs));
    }

    @Override
    public Object executeScript(String script) {
        return ((JavascriptExecutor) driver).executeScript(script);
    }

    @Override
    public String getCurrentUrl() {
        return driver.getCurrentUrl();
    }

    @Override
    public String getTitle() {
        return driver.getTitle();
    }

    @Override
    public void switchToFrame(String locatorType, String locatorValue) {
        driver.switchTo().frame(find(locatorType, locatorValue));
    }

    @Override
    public void switchToDefaultContent() {
        driver.switchTo().defaultContent();
    }

    @Override
    public void scroll(int x, int y) {
        ((JavascriptExecutor) driver).executeScript("window.scrollBy(" + x + "," + y + ")");
    }

    @Override
    public void keyPress(String keys) {
        new Actions(driver).sendKeys(Keys.valueOf(keys.toUpperCase())).perform();
    }

    @Override
    public void uploadFile(String locatorType, String locatorValue, String filePath) {
        find(locatorType, locatorValue).sendKeys(new File(filePath).getAbsolutePath());
    }

    @Override
    public void close() {
        try { driver.quit(); } catch (Exception ignored) {}
    }
}
