package com.mycelium.service;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.FileReader;
import java.io.FileWriter;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.time.Duration;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

@Component
@Slf4j
public class SeleniumInstagramClient implements InstagramClient {

    private static final String COOKIES_FILE = "insta_cookies_main.json";
    private static final String PAGE_UNAVAILABLE_XPATH = "//*[text()=\"Sorry, this page isn't available.\"]";
    private static final String PRIVATE_ICON_PATH_D = "M60.931 70.001H35.065a5.036 5.036 0 0 1-5.068-5.004V46.005A5.036 5.036 0 0 1 35.065 41H60.93a5.035 5.035 0 0 1 5.066 5.004v18.992A5.035 5.035 0 0 1 60.93 70ZM37.999 39.996v-6.998a10 10 0 0 1 20 0v6.998";
    private static final String PRIVATE_ICON_XPATH = "//*[local-name()='svg']//*[local-name()='path' and @d='" + PRIVATE_ICON_PATH_D + "']";
    private static final String DIALOG_WRAPPER_CSS = "div[style='height: auto; overflow: hidden auto;']";
    private static final String USERS_LIST_CSS = "a[role='link'] span._aaco";
    private static final String CLOSE_DIALOG_BUTTON_CSS = "button[type='button']._abl-";
    public static volatile int FOLLOWER_LIMIT = 700;
    public static volatile int FOLLOWING_LIMIT = 700;
    private final Gson gson = new Gson();

    private final UpdateService updateService;
    @Value("${instagram.login.username}")
    private String instagramUsername;
    @Value("${instagram.login.password}")
    private String instagramPassword;

    public SeleniumInstagramClient(UpdateService updateService) {
        this.updateService = updateService;
    }

    @Override
    public WebDriver setupSelenium() {
        System.setProperty("webdriver.chrome.driver", "chromedriver.exe");
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--disable-notifications", "--mute-audio");
        return new ChromeDriver(options);
    }

    @Override
    public void ensureLoggedIn(WebDriver driver) {
        if (!loadCookiesAndCheckSession(instagramUsername, driver)) {
            log.info("... Session is invalid. Performing a full login.");
            loginToInstagram(instagramUsername, instagramPassword, driver);
            saveCookies(driver);
        }
    }

    @Override
    public boolean isPageUnavailable(WebDriver driver) {
        return !driver.findElements(By.xpath(PAGE_UNAVAILABLE_XPATH)).isEmpty();
    }

    @Override
    public boolean isProfilePrivate(WebDriver driver) {
        return !driver.findElements(By.xpath(PRIVATE_ICON_XPATH)).isEmpty();
    }

    @Override
    public boolean isCountOverLimit(String username, WebDriver driver, String taskId, String type) {
        try {
            String urlPart = type.equals("followers") ? "/followers/" : "/following/";
            WebElement countElement = driver.findElement(By.cssSelector("a[href*='/" + username + urlPart + "'] span"));
            String countText = countElement.getDomAttribute("title");
            if (countText == null || countText.isEmpty()) {
                countText = countElement.getText();
            }
            long count = Long.parseLong(countText.replaceAll("\\D", ""));
            int limit = type.equals("followers") ? FOLLOWER_LIMIT : FOLLOWING_LIMIT;
            if (count > limit) {
                log.warn("!!! [TASK ID: {}] TOO MANY {} ({} > {}). Skipping {}.", taskId, type.toUpperCase(), count, limit, username);
                updateService.sendLog(String.format("too many %s for %s (%d > %d)", type, username, count, limit), taskId);
                return true;
            }
        } catch (Exception e) {
            log.warn("... [TASK ID: {}] Failed to determine the number of {} for {}. Skipping.", taskId, type, username);
            updateService.sendLog("failed to determine the number of " + type + " for " + username, taskId);
            return true;
        }
        return false;
    }

    @Override
    public Set<String> getFollowers(String username, WebDriver driver, String taskId) {
        log.info("... [TASK ID: {}] Starting parsing of FOLLOWERS {}", taskId, username);
        String url = "/" + username + "/followers/";
        return scrapeUserList(url, driver, taskId);
    }

    @Override
    public Set<String> getFollowing(String username, WebDriver driver, String taskId) {
        log.info("... [TASK ID: {}] Starting parsing of FOLLOWING {}", taskId, username);
        String url = "/" + username + "/following/";
        return scrapeUserList(url, driver, taskId);
    }

    private boolean loadCookiesAndCheckSession(String username, WebDriver driver) {
        WebDriverWait wait = createWait(driver);
        try (Reader reader = new FileReader(COOKIES_FILE)) {
            Type cookieSetType = new TypeToken<Set<Cookie>>() {
            }.getType();
            Set<Cookie> cookies = gson.fromJson(reader, cookieSetType);
            if (cookies == null || cookies.isEmpty()) {
                return false;
            }
            driver.get("https://www.instagram.com/");
            sleep(1000, 2000);
            for (Cookie cookie : cookies) {
                driver.manage().addCookie(cookie);
            }
            log.info(">>> Cookies loaded from file {}.", COOKIES_FILE);
            driver.navigate().refresh();
            wait.until(ExpectedConditions.urlContains("instagram.com"));
            wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector("a[href*='/" + username + "/']")));
            log.info(">>> Cookie-based session is active. Skipping login.");
            return true;
        } catch (Exception e) {
            log.warn("... Failed to load or verify cookie-based session: {}", e.getMessage());
            return false;
        }
    }

    private void saveCookies(WebDriver driver) {
        try (Writer writer = new FileWriter(COOKIES_FILE)) {
            Set<Cookie> cookies = driver.manage().getCookies();
            gson.toJson(cookies, writer);
            log.info(">>> Cookies have been successfully saved to file {}.", COOKIES_FILE);
        } catch (Exception e) {
            log.error("### Error while saving cookies", e);
        }
    }

    private void loginToInstagram(String username, String password, WebDriver driver) {
        WebDriverWait wait = createWait(driver);
        driver.get("https://www.instagram.com/accounts/login/");
        sleep(3000, 5000);
        log.info(">>> Entering username and password...");
        wait.until(ExpectedConditions.visibilityOfElementLocated(By.name("username"))).sendKeys(username);
        wait.until(ExpectedConditions.visibilityOfElementLocated(By.name("password"))).sendKeys(password);
        sleep(1000, 2000);
        wait.until(ExpectedConditions.elementToBeClickable(By.cssSelector("button[type='submit']"))).click();
        log.info(">>> Waiting for login to complete...");
        wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector("a[href*='/" + username + "/']")));
        log.info(">>> Login successful!");
    }

    private Set<String> scrapeUserList(String urlPart, WebDriver driver, String taskId) {
        WebDriverWait wait = createWait(driver);
        try {
            wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector("a[href*='" + urlPart + "']"))).click();
            sleep(2000, 3000);
        } catch (TimeoutException e) {
            log.warn("[TASK ID: {}] Failed to find a button for {}", taskId, urlPart);
            return Collections.emptySet();
        }

        WebElement listContainer = wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector(DIALOG_WRAPPER_CSS)));
        Set<String> users = scrollAndCollectUsers(listContainer, driver);
        log.info(">>> [TASK ID: {}] Scrolling completed. Collected {} users from {}", taskId, users.size(), urlPart);

        closeDialog(driver);
        return users;
    }

    private Set<String> scrollAndCollectUsers(WebElement listContainer, WebDriver driver) {
        Set<String> users = new HashSet<>();
        long lastHeight = 0;
        int sameHeightCount = 0;
        final int MAX_SAME_HEIGHT_ATTEMPTS = 3;
        WebElement mainDialog = listContainer.findElement(By.xpath(".."));

        while (sameHeightCount < MAX_SAME_HEIGHT_ATTEMPTS) {
            List<WebElement> userElements = mainDialog.findElements(By.cssSelector(USERS_LIST_CSS));
            userElements.forEach(el -> users.add(el.getText()));

            JavascriptExecutor js = (JavascriptExecutor) driver;
            js.executeScript("arguments[0].scrollTop = arguments[0].scrollHeight", mainDialog);
            sleep(750, 1250);

            Object heightObj = js.executeScript("return arguments[0].scrollHeight", mainDialog);
            long newHeight = (heightObj instanceof Number) ? ((Number) heightObj).longValue() : 0L;
            if (newHeight == lastHeight) {
                sameHeightCount++;
                if (!mainDialog.findElements(By.cssSelector("a[href*='/explore/people/']")).isEmpty()) {
                    break;
                }
            } else {
                sameHeightCount = 0;
            }
            lastHeight = newHeight;
        }
        return users;
    }

    private void closeDialog(WebDriver driver) {
        try {
            driver.findElement(By.cssSelector(CLOSE_DIALOG_BUTTON_CSS)).click();
            sleep(500, 1000);
        } catch (Exception e) {
            log.warn("Failed to close the dialog window.");
        }
    }

    private WebDriverWait createWait(WebDriver driver) {
        return new WebDriverWait(driver, Duration.ofSeconds(20));
    }

    private void sleep(long min, long max) {
        try {
            Thread.sleep(ThreadLocalRandom.current().nextLong(min, max));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
