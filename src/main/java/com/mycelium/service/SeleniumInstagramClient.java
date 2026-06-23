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
import java.util.Set;

@Component
@Slf4j
public class SeleniumInstagramClient implements InstagramClient {

    private static final String COOKIES_FILE = "insta_cookies_main.json";
    private static final String PAGE_UNAVAILABLE_XPATH = "//*[text()=\"Sorry, this page isn't available.\"]";
    private static final String PRIVATE_ICON_PATH_D = "M60.931 70.001H35.065a5.036 5.036 0 0 1-5.068-5.004V46.005A5.036 5.036 0 0 1 35.065 41H60.93a5.035 5.035 0 0 1 5.066 5.004v18.992A5.035 5.035 0 0 1 60.93 70ZM37.999 39.996v-6.998a10 10 0 0 1 20 0v6.998";
    private static final String PRIVATE_ICON_XPATH = "//*[local-name()='svg']//*[local-name()='path' and @d='" + PRIVATE_ICON_PATH_D + "']";
    public static volatile int followerLimit = 700;
    public static volatile int followingLimit = 700;
    private final Gson gson = new Gson();

    private final UpdateService updateService;
    private final InstagramListScraper listScraper;
    @Value("${instagram.login.username}")
    private String instagramUsername;
    @Value("${instagram.login.password}")
    private String instagramPassword;

    public SeleniumInstagramClient(UpdateService updateService, InstagramListScraper listScraper) {
        this.updateService = updateService;
        this.listScraper = listScraper;
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
            SeleniumUtils.sleep(2000, 3000);
            saveCookies(driver);
        }
    }

    @Override
    public PageStatus navigateToProfile(String username, WebDriver driver) {
        driver.get("https://www.instagram.com/" + username + "/");
        WebDriverWait wait = SeleniumUtils.createWait(driver);

        try {
            wait.until(d -> isPageUnavailable(d) || isProfilePrivate(d) || isProfileReady(d));
        } catch (TimeoutException e) {
            log.warn("Page for {} did not stabilize within timeout", username);
        }

        if (isPageUnavailable(driver)) return PageStatus.UNAVAILABLE;
        if (isProfilePrivate(driver)) return PageStatus.PRIVATE;
        return PageStatus.OK;
    }

    private boolean isPageUnavailable(WebDriver driver) {
        return !driver.findElements(By.xpath(PAGE_UNAVAILABLE_XPATH)).isEmpty();
    }

    private boolean isProfilePrivate(WebDriver driver) {
        if (!driver.findElements(By.xpath(PRIVATE_ICON_XPATH)).isEmpty()) return true;
        try {
            return driver.findElement(By.xpath("//h2[contains(text(), 'This account is private')]")).isDisplayed();
        } catch (NoSuchElementException e) {
            return false;
        }
    }

    private boolean isProfileReady(WebDriver driver) {
        try {
            return driver.findElement(By.cssSelector("a[role='link'] img")).isDisplayed();
        } catch (NoSuchElementException e) {
            return false;
        }
    }

    @Override
    public boolean isCountOverLimit(String username, WebDriver driver, String taskId, String type) {
        WebElement countElement = SeleniumUtils.createWait(driver).until(
                ExpectedConditions.visibilityOfElementLocated(By.xpath("//a[contains(., '" + type + "')]"))
        );
        String countText = countElement.getText().replaceAll("[^0-9]", "");
        if (countText.isEmpty()) {
            throw new RuntimeException("Could not parse " + type + " count for " + username + " from text: " + countElement.getText());
        }
        long count = Long.parseLong(countText);
        int limit = type.equals("followers") ? followerLimit : followingLimit;
        if (count > limit) {
            log.warn("!!! [TASK ID: {}] TOO MANY {} ({} > {}). Skipping {}.", taskId, type.toUpperCase(), count, limit, username);
            updateService.sendLog(String.format("too many %s for %s (%d > %d)", type, username, count, limit), taskId);
            return true;
        }
        return false;
    }

    @Override
    public Set<String> getFollowers(String username, WebDriver driver, String taskId) {
        log.info("... [TASK ID: {}] Starting parsing of FOLLOWERS {}", taskId, username);
        return listScraper.scrapeUserList("/" + username + "/followers/", driver, taskId);
    }

    @Override
    public Set<String> getFollowing(String username, WebDriver driver, String taskId) {
        log.info("... [TASK ID: {}] Starting parsing of FOLLOWING {}", taskId, username);
        return listScraper.scrapeUserList("/" + username + "/following/", driver, taskId);
    }

    private boolean loadCookiesAndCheckSession(String username, WebDriver driver) {
        WebDriverWait wait = SeleniumUtils.createWait(driver);
        try (Reader reader = new FileReader(COOKIES_FILE)) {
            Type cookieSetType = new TypeToken<Set<Cookie>>() {
            }.getType();
            Set<Cookie> cookies = gson.fromJson(reader, cookieSetType);
            if (cookies == null || cookies.isEmpty()) {
                return false;
            }
            driver.get("https://www.instagram.com/");
            SeleniumUtils.sleep(1000, 2000);
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
        WebDriverWait wait = SeleniumUtils.createWait(driver);
        driver.get("https://www.instagram.com/accounts/login/");
        SeleniumUtils.sleep(3000, 5000);
        log.info(">>> Entering username and password...");
        wait.until(ExpectedConditions.visibilityOfElementLocated(By.name("email"))).sendKeys(username);
        wait.until(ExpectedConditions.visibilityOfElementLocated(By.name("pass"))).sendKeys(password);
        SeleniumUtils.sleep(1000, 2000);
        wait.until(ExpectedConditions.elementToBeClickable(By.cssSelector("div[role='button'][aria-label='Log In']"))).click();
        log.info(">>> Waiting for login to complete...");
        wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector("a[href*='/" + username + "/']")));
        log.info(">>> Login successful!");
    }


}
