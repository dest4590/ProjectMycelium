package com.mycelium.service;

import org.openqa.selenium.WebDriver;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

public final class SeleniumUtils {

    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);

    private SeleniumUtils() {}

    public static WebDriverWait createWait(WebDriver driver) {
        return new WebDriverWait(driver, DEFAULT_TIMEOUT);
    }

    public static void sleep(long min, long max) {
        try {
            Thread.sleep(ThreadLocalRandom.current().nextLong(min, max));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
