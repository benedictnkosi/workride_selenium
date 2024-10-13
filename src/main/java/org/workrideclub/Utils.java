package org.workrideclub;

import dev.failsafe.internal.util.Assert;
import okhttp3.*;
import org.json.JSONArray;
import org.json.JSONObject;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.LogManager;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Utils {

    static WebDriver driver;
    static MapsPage mapsPage;
    private static final Logger logger = Logger.getLogger(Utils.class.getName());

    static String hostName;

    static {
        if (isWindows()) {
            hostName = "http://localhost:8000";
        } else {
            hostName = "https://ride.hotelrunner.co.za";
        }
    }

    public static void matchCommuters() throws IOException {

        LogManager.getLogManager().readConfiguration(
                Utils.class.getResourceAsStream("/logging.properties"));

        while (true) {
            // removeBrokenStatus();
            addNewDriverTravelTime();
            processFirstUnmatched();
            try {
                Thread.sleep(10000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public static void addNewDriverTravelTime() {
        logger.info("Adding new driver travel time");
        String message = getNewDrivers();
        JSONObject jsonObj = new JSONObject(message);
        if (jsonObj.isNull("commuters")) {
            logger.info("No new drivers");
            return;
        }
        JSONArray newDrivers = new JSONArray(jsonObj.getString("commuters"));

        for (int i = 0; i < newDrivers.length(); i++) {
            JSONObject driver = newDrivers.getJSONObject(i);
            logger.info("Driver name and id is " + driver.getString("name") + " " + +driver.getInt("id"));
            if (!driver.isNull("result_message")) {
                Assert.isTrue(driver.getInt("result_code") == 0, driver.getString("result_message"));
            }

            JSONObject workAddress = driver.getJSONObject("work_address");
            JSONObject homeAddress = driver.getJSONObject("home_address");

            String url = "https://www.google.com/maps/dir/" + homeAddress.getString("latitude") + ","
                    + homeAddress.getString("longitude") + "/" + workAddress.getString("latitude") + ","
                    + workAddress.getString("longitude");
            String totalTime = convertToMinutes(getTotalTime(url));
            if (!totalTime.equals("0")) {
                saveDriverTravelTime(String.valueOf(driver.getInt("id")), totalTime);
            } else {
                updateCommuterStatus(String.valueOf(driver.getInt("id")), "broken_address");
            }
        }
    }

    public static void saveDriverTravelTime(String driver, String tripTime) {
        logger.info("Saving driver travel time");
        // body form data
        JSONObject body = new JSONObject();
        body.put("id", driver);
        body.put("travel_time", String.valueOf(tripTime));

        OkHttpClient client = new OkHttpClient().newBuilder()
                .build();
        Map<String, String> headers = new HashMap<>();
        Headers headerBuild = Headers.of(headers);
        MediaType mediaType = MediaType.parse("application/json");
        RequestBody requestBody = RequestBody.create(body.toString(), mediaType);
        Request request = new Request.Builder()
                .url(hostName + "/api/update/commuter/traveltime")
                .method("PUT", requestBody)
                .headers(headerBuild)
                .build();
        try {
            Response response = client.newCall(request).execute();
            String message = response.body().string();
            logger.info(message);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void updateCommuterStatus(String driver, String status) {
        logger.info("Saving driver travel time");
        // body form data
        JSONObject body = new JSONObject();
        body.put("id", driver);
        body.put("status", String.valueOf(status));

        OkHttpClient client = new OkHttpClient().newBuilder()
                .build();
        Map<String, String> headers = new HashMap<>();
        Headers headerBuild = Headers.of(headers);
        MediaType mediaType = MediaType.parse("application/json");
        RequestBody requestBody = RequestBody.create(body.toString(), mediaType);
        Request request = new Request.Builder()
                .url(hostName + "/api/update/commuter/status")
                .method("PUT", requestBody)
                .headers(headerBuild)
                .build();
        try {
            Response response = client.newCall(request).execute();
            String message = response.body().string();
            logger.info(message);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void removeBrokenStatus() {
        logger.info("removeBrokenStatus");
        // body form data
        JSONObject body = new JSONObject();

        OkHttpClient client = new OkHttpClient().newBuilder()
                .build();
        Map<String, String> headers = new HashMap<>();
        Headers headerBuild = Headers.of(headers);
        MediaType mediaType = MediaType.parse("application/json");
        RequestBody requestBody = RequestBody.create(body.toString(), mediaType);
        Request request = new Request.Builder()
                .url(hostName + "/api/remove/broken")
                .method("PUT", requestBody)
                .headers(headerBuild)
                .build();
        try {
            Response response = client.newCall(request).execute();
            String message = response.body().string();
            logger.info(message);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void processFirstUnmatched() {
        logger.info("Processing first unmatched");
        String message = getUnmatched();
        if (message.equals("[]")) {
            logger.info("No unmatched commuters");
            return;
        }
        try {
            JSONObject jsonObj = new JSONObject(message);
            String driver = String.valueOf(jsonObj.getInt("driver"));
            String passenger = String.valueOf(jsonObj.getInt("passenger"));
            String url = jsonObj.getString("url");
            String time = getTotalTime(url);
            String totalTime = convertToMinutes(time);
            if (!totalTime.equals("0")) {
                saveMatch(driver, passenger, totalTime, url);
            } else {
                updateCommuterStatus(passenger, "broken_address");
            }
        } catch (org.json.JSONException e) {
            logger.severe("Error parsing JSON: " + e.getMessage());
        }
    }

    public static String getTotalTime(String url) {
        logger.info("Getting total time");
        try {

            if (driver == null) {
                logger.info("Creating chrome driver");
                driver = createDriver();
                mapsPage = new MapsPage(driver);
                driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(60));
                driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(60));
                driver.manage().window().maximize();
                logger.info("Done Creating chrome driver");
            }

            driver.get(url);
            logger.info("Done getting url");
            return mapsPage.getTime();
        } catch (Exception ex) {
            logger.info("Error getting time " + ex.getMessage());
            return "0 min";
        }
    }

    public static String convertToMinutes(String input) {
        // Define the regular expression pattern
        Pattern pattern = Pattern.compile("(\\d+) hr(?: (\\d+) min)?|(\\d+) min");

        // Match the pattern against the input string
        Matcher matcher = pattern.matcher(input);

        if (matcher.find()) {
            // Extract hours and minutes from the matched groups
            String hoursGroup = matcher.group(1);
            String minutesGroup1 = matcher.group(2);
            String minutesGroup2 = matcher.group(3);

            // Convert hours and minutes to integers
            int hours = (hoursGroup != null) ? Integer.parseInt(hoursGroup) : 0;
            int minutes = (minutesGroup1 != null) ? Integer.parseInt(minutesGroup1)
                    : (minutesGroup2 != null) ? Integer.parseInt(minutesGroup2) : 0;

            // Calculate total minutes
            return String.valueOf(hours * 60 + minutes);
        } else {
            // Handle invalid input format
            throw new IllegalArgumentException("Invalid input format: " + input);
        }
    }

    public static String getUnmatched() {
        URL url;
        HttpURLConnection con;
        try {
            url = new URI(hostName + "/api/tomatch").toURL();
            con = (HttpURLConnection) url.openConnection();
            con.setRequestMethod("GET");
            con.setDoInput(true);

            con.setDoOutput(true);

            BufferedReader in = new BufferedReader(
                    new InputStreamReader(con.getInputStream()));
            String inputLine;
            StringBuilder content = new StringBuilder();
            while ((inputLine = in.readLine()) != null) {
                content.append(inputLine);
            }

            in.close();
            con.disconnect();
            logger.info(String.valueOf(content));
            return content.toString();

        } catch (IOException | URISyntaxException e) {
            throw new RuntimeException(e);
        }

    }

    public static String getNewDrivers() {
        URL url;
        HttpURLConnection con;
        try {
            url = new URI(hostName + "/api/newdrivers").toURL();
            con = (HttpURLConnection) url.openConnection();
            con.setRequestMethod("GET");
            con.setDoInput(true);

            con.setDoOutput(true);

            BufferedReader in = new BufferedReader(
                    new InputStreamReader(con.getInputStream()));
            String inputLine;
            StringBuilder content = new StringBuilder();
            while ((inputLine = in.readLine()) != null) {
                content.append(inputLine);
            }

            in.close();
            con.disconnect();
            logger.info(String.valueOf(content));
            return content.toString();

        } catch (IOException | URISyntaxException e) {
            throw new RuntimeException(e);
        }

    }

    public static void saveMatch(String driver, String passenger, String tripTime, String mapLink) {
        logger.info("Saving match");
        // body form data
        JSONObject body = new JSONObject();
        body.put("driver", driver);
        body.put("passenger", passenger);
        body.put("totalTrip", String.valueOf(tripTime));
        body.put("mapLink", mapLink);
        OkHttpClient client = new OkHttpClient().newBuilder()
                .build();
        Map<String, String> headers = new HashMap<>();
        Headers headerBuild = Headers.of(headers);
        MediaType mediaType = MediaType.parse("application/json");
        RequestBody requestBody = RequestBody.create(body.toString(), mediaType);
        Request request = new Request.Builder()
                .url(hostName + "/api/savematch")
                .method("POST", requestBody)
                .headers(headerBuild)
                .build();
        try {
            Response response = client.newCall(request).execute();
            String message = response.body().string();
            logger.info(message);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static WebDriver createDriver() {
        logger.info("Creating driver");
        if (isWindows()) {
            logger.info("This is a windows machine");
            String driverLocation = "src\\main\\resources\\windows\\chromedriver.exe";
            System.setProperty("webdriver.chrome.driver", driverLocation);
        } else {
            logger.info("This is a linux machine");
            String driverLocation = "./src/main/resources/linux/chromedriver";
            System.setProperty("webdriver.chrome.driver", driverLocation);
        }
        try {
            ChromeOptions options = new ChromeOptions();
            options.addArguments("--headless"); // Run in headless mode
            options.addArguments("--disable-gpu"); // Disable GPU in headless environments
            options.addArguments("--no-sandbox"); // Disable sandboxing
            options.addArguments("--disable-dev-shm-usage"); // Use /tmp for shared memory
            options.addArguments("--disable-software-rasterizer");
            options.addArguments("--remote-allow-origins=*");
            options.addArguments("--remote-debugging-port=9222");
            if (!isWindows()) {
                options.addArguments("--disable-gpu");
                options.addArguments("--headless");
            }
            driver = new ChromeDriver(options);
            driver.manage().window().maximize();
            return driver;
        } catch (Exception e) {
            logger.info("Error creating driver " + e.getMessage());
            throw new RuntimeException(e);
        }
    }

    public static boolean isWindows() {
        // Get the value of the "os.name" system property
        String osName = System.getProperty("os.name");

        // Check if the operating system is Windows
        return osName.toLowerCase().contains("win");
    }
}