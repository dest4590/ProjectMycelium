package com.mycelium.service;

import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.*;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
@Slf4j
public class InstagramListScraper {

    static final String USER_LINK_SELECTOR = "a[role='link']";

    Set<String> scrapeUserList(String urlPart, WebDriver driver, String taskId) {
        WebDriverWait wait = SeleniumUtils.createWait(driver);
        String typeLabel = urlPart.contains("followers") ? "followers" : "following";

        wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath("//a[contains(., '" + typeLabel + "')]"))).click();
        SeleniumUtils.sleep(2000, 3000);

        wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector("div[role='dialog']")));
        WebElement firstUserLink = wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector("div[role='dialog'] " + USER_LINK_SELECTOR)));
        WebElement scrollable = findScrollableContainer(driver, firstUserLink);
        Set<String> users = scrollAndCollectUsers(scrollable, driver);

        log.info(">>> [TASK ID: {}] Scrolling completed. Collected {} users from {}", taskId, users.size(), urlPart);
        closeDialog(driver);
        return users;
    }

    private WebElement findScrollableContainer(WebDriver driver, WebElement child) {
        return (WebElement) ((JavascriptExecutor) driver).executeScript(
                "var link = arguments[0];" +
                        "var dialog = link.closest('[role=\"dialog\"]');" +
                        "if (!dialog) { return link.parentElement; }" +
                        "var elements = dialog.querySelectorAll('*');" +
                        "for (var i = 0; i < elements.length; i++) {" +
                        "  var style = window.getComputedStyle(elements[i]);" +
                        "  if ((style.overflowY === 'auto' || style.overflowY === 'scroll') &&" +
                        "      elements[i].scrollHeight > elements[i].clientHeight) {" +
                        "    return elements[i];" +
                        "  }" +
                        "}" +
                        "for (var i = 0; i < elements.length; i++) {" +
                        "  var style = window.getComputedStyle(elements[i]);" +
                        "  if (style.overflowY === 'auto' || style.overflowY === 'scroll') {" +
                        "    return elements[i];" +
                        "  }" +
                        "}" +
                        "return dialog;",
                child
        );
    }

    private Set<String> scrollAndCollectUsers(WebElement scrollable, WebDriver driver) {
        Set<String> users = new HashSet<>();
        long lastHeight = 0;
        int sameHeightCount = 0;
        final int MAX_SAME_HEIGHT_ATTEMPTS = 3;
        JavascriptExecutor js = (JavascriptExecutor) driver;

        while (sameHeightCount < MAX_SAME_HEIGHT_ATTEMPTS) {
            @SuppressWarnings("unchecked")
            List<String> hrefs = (List<String>) js.executeScript(
                    "var c = arguments[0];" +
                            "var h = c.querySelector('h4');" +
                            "var links = c.querySelectorAll('a[role=\"link\"]');" +
                            "var r = [];" +
                            "for (var i = 0; i < links.length; i++) {" +
                            "  if (!h || (links[i].compareDocumentPosition(h) & Node.DOCUMENT_POSITION_FOLLOWING)) {" +
                            "    r.push(links[i].getAttribute('href'));" +
                            "  }" +
                            "}" +
                            "return r;",
                    scrollable
            );

            for (String href : hrefs) {
                String name = extractUsernameFromHref(href);
                if (!name.isEmpty()) {
                    users.add(name);
                }
            }

            js.executeScript("arguments[0].scrollTop = arguments[0].scrollHeight", scrollable);
            SeleniumUtils.sleep(750, 1250);

            Object heightObj = js.executeScript("return arguments[0].scrollHeight", scrollable);
            long newHeight = (heightObj instanceof Number) ? ((Number) heightObj).longValue() : 0L;
            if (newHeight == lastHeight) {
                sameHeightCount++;
                if (!scrollable.findElements(By.cssSelector("a[href*='/explore/people/']")).isEmpty()) {
                    break;
                }
            } else {
                sameHeightCount = 0;
            }
            lastHeight = newHeight;
        }
        return users;
    }

    private String extractUsernameFromHref(String href) {
        if (href == null || href.isEmpty()) return "";
        return href.replaceAll("^https?://[^/]+", "").replaceAll("^/+|/+$", "").split("/")[0];
    }

    private void closeDialog(WebDriver driver) {
        try {
            new Actions(driver).sendKeys(Keys.ESCAPE).perform();
            SeleniumUtils.sleep(500, 1000);
        } catch (Exception e) {
            log.warn("Failed to close the dialog window.");
        }
    }
}
