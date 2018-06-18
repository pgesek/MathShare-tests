package env;

import io.appium.java_client.android.AndroidDriver;
import io.appium.java_client.ios.IOSDriver;
import org.openqa.selenium.By;
import org.openqa.selenium.NoSuchSessionException;
import org.openqa.selenium.SessionNotCreatedException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.edge.EdgeDriver;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxOptions;
import org.openqa.selenium.remote.DesiredCapabilities;
import org.openqa.selenium.remote.ErrorHandler;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.openqa.selenium.safari.SafariDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Enumeration;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

public class DriverUtil {
    public static long DEFAULT_WAIT = 20;
    protected static WebDriver driver = null;

    private static String currentPath = System.getProperty("user.dir");
    private static Properties prop = new Properties();
    private static DesiredCapabilities capability = null;

    public static WebDriver getDefaultDriver() {
        if (driver != null) {
            return driver;
        }

        Properties webDriverProps = new Properties();

        try (InputStream in = DriverUtil.class.getClassLoader().getResourceAsStream("webdriver.properties")) {
            webDriverProps.load(in);
            webDriverProps.putAll(System.getProperties());
            System.setProperties(webDriverProps);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to load WebDriver properties", e);
        }

        String enviroment = "desktop";
        String platform = "";
        String config = System.getProperty("config", "");

        if (!config.isEmpty()) {
            enviroment = config.split("_")[0].toLowerCase();
            platform = config.split("_")[1].toLowerCase();
            try (InputStream input = new FileInputStream(currentPath + "/src/main/java/browserConfigs/" + config
                    + ".properties")) {
                capability = getCapability(input);
            } catch (IOException e) {
                throw new IllegalStateException("Unable to load browser config properties", e);
            }
        }

        try {
            switch (enviroment) {
                case "local":
                    switch (platform) {
                        case "android":
                            driver = androidDriver(capability);
                            break;
                        case "ios":
                            driver = iosDriver(capability);
                            break;
                        default:
                            System.out.println("unsupported platform");
                            System.exit(0);
                    }
                    break;

                case "browserstack":
                    driver = browserStackDriver();
                    break;

                case "desktop":
                    DesiredCapabilities capabilities = DesiredCapabilities.firefox();
                    capabilities.setJavascriptEnabled(true);
                    capabilities.setCapability("takesScreenshot", true);
                    driver = chooseDriver(capabilities);
                    driver.manage().timeouts().setScriptTimeout(DEFAULT_WAIT, TimeUnit.SECONDS);
                    driver.manage().window().maximize();
                    break;
                default:
                    throw new IllegalStateException("Inalid environment: " + enviroment);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Unable to create driver", e);
        }

        return driver;
    }

    /*

    /*
     * Returns browserStack remote driver instance by reading browserStack configuration
     * from platformConfigs/browserstack.properties
     * 
     * @param DesiredCapabilities create capabilities by reading browser config.
     * @return RemoteWebDriver
     */
    private static WebDriver browserStackDriver() throws IOException {
        URL remoteDriverURL;
        try (InputStream input = new FileInputStream(currentPath +
                "/src/main/java/platformConfigs/browserstack.properties")) {

            prop.load(input);

            String url = prop.getProperty("protocol") +
                    "://" +
                    prop.getProperty("username") +
                    ":" +
                    prop.getProperty("access_key") +
                    prop.getProperty("url");

            input.close();
            prop.clear();
            remoteDriverURL = new URL(url);
        }
        return new RemoteWebDriver(remoteDriverURL, capability);
    }

    private static WebDriver androidDriver(DesiredCapabilities capabilities) throws MalformedURLException {
        String port = "4723";
        driver = new AndroidDriver(new URL("http://127.0.0.1:" + port + "/wd/hub"), capabilities);
        return driver;
    }

    private static WebDriver iosDriver(DesiredCapabilities capabilities) throws MalformedURLException {
        String port = "4723";
        driver = new IOSDriver(new URL("http://127.0.0.1:" + port + "/wd/hub"), capabilities);
        return driver;
    }

    private static WebDriver chooseDriver(DesiredCapabilities capabilities) {
        String preferredDriver = System.getProperty("browser", "Firefox");
        boolean headless = System.getProperty("headless", "false").equals("true");

        switch (preferredDriver.toLowerCase()) {
            case "safari":
                driver = new SafariDriver();
                return driver;
            case "edge":
                driver = new EdgeDriver();
                return driver;
            case "chrome":
                final ChromeOptions chromeOptions = new ChromeOptions();
                if (headless) {
                    chromeOptions.addArguments("--headless");
                }
                capabilities.setCapability(ChromeOptions.CAPABILITY, chromeOptions);
                driver = new ChromeDriver(capabilities);
                ErrorHandler handler = new ErrorHandler();
                handler.setIncludeServerErrors(false);
                return driver;
            default:
                FirefoxOptions options = new FirefoxOptions();
                if (headless) {
                    options.addArguments("-headless", "-safe-mode");
                }
                capabilities.setCapability(FirefoxOptions.FIREFOX_OPTIONS, options);
                driver = new FirefoxDriver(capabilities);
                return driver;
        }
    }

    public static WebElement waitAndGetElementByCssSelector(WebDriver driver, String selector,
                                                            int seconds) {
        By selection = By.cssSelector(selector);
        return (new WebDriverWait(driver, seconds)).until( // ensure element is visible!
                ExpectedConditions.visibilityOfElementLocated(selection));
    }

    public static void closeDriver() {
        if (driver != null) {
            try {
                driver.close();
                //driver.quit(); // fails in current geckodriver! TODO: Fixme
            } catch (NoSuchMethodError | SessionNotCreatedException | NoSuchSessionException e) {
                throw new IllegalStateException("Unable to close web driver", e);
            }
            driver = null;
        }
    }

    private static DesiredCapabilities getCapability(InputStream input) throws IOException {
        DesiredCapabilities capability = new DesiredCapabilities();
        prop.load(input);
        if (prop.containsKey("app")) {
            String appName = prop.getProperty("app");
            if (!appName.contains("sauce-storage")) {
                String appPath = currentPath + "/src/main/java/appUnderTest/" + appName;
                prop.setProperty("app", appPath);
            }
        }

        // set capabilities
        Enumeration<Object> enuKeys = prop.keys();
        while (enuKeys.hasMoreElements()) {
            String key = (String) enuKeys.nextElement();
            String value = prop.getProperty(key);
            capability.setCapability(key, value);
        }
        return capability;
    }
}
