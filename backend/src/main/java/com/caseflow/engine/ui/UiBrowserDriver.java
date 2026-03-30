package com.caseflow.engine.ui;

public interface UiBrowserDriver extends AutoCloseable {
    void navigate(String url);
    void click(String locatorType, String locatorValue);
    void doubleClick(String locatorType, String locatorValue);
    void rightClick(String locatorType, String locatorValue);
    void type(String locatorType, String locatorValue, String text);
    void clear(String locatorType, String locatorValue);
    String getText(String locatorType, String locatorValue);
    String getAttribute(String locatorType, String locatorValue, String attr);
    boolean isVisible(String locatorType, String locatorValue);
    boolean isEnabled(String locatorType, String locatorValue);
    boolean elementExists(String locatorType, String locatorValue);
    byte[] screenshot();
    byte[] screenshotElement(String locatorType, String locatorValue);
    void hover(String locatorType, String locatorValue);
    void selectByText(String locatorType, String locatorValue, String text);
    void waitForVisible(String locatorType, String locatorValue, long timeoutMs);
    void waitForHidden(String locatorType, String locatorValue, long timeoutMs);
    void waitForClickable(String locatorType, String locatorValue, long timeoutMs);
    void waitForPresent(String locatorType, String locatorValue, long timeoutMs);
    void setImplicitWait(long timeoutMs);
    Object executeScript(String script);
    String getCurrentUrl();
    String getTitle();
    void switchToFrame(String locatorType, String locatorValue);
    void switchToDefaultContent();
    void scroll(int x, int y);
    void keyPress(String keys);
    void uploadFile(String locatorType, String locatorValue, String filePath);

    @Override
    void close();
}
