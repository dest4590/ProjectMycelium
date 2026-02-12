package com.mycelium.service;

import org.openqa.selenium.WebDriver;

import java.util.Set;

public interface InstagramClient {

    WebDriver setupSelenium();

    void ensureLoggedIn(WebDriver driver);

    boolean isPageUnavailable(WebDriver driver);

    boolean isProfilePrivate(WebDriver driver);

    boolean isCountOverLimit(String username, WebDriver driver, String taskId, String type);

    Set<String> getFollowers(String username, WebDriver driver, String taskId);

    Set<String> getFollowing(String username, WebDriver driver, String taskId);
}
