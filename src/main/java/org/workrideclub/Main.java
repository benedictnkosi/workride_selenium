package org.workrideclub;


import dev.failsafe.internal.util.Assert;
import okhttp3.*;
import org.json.JSONArray;
import org.json.JSONObject;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.edge.EdgeDriver;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxOptions;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Main {

    static WebDriver driver;
    static MapsPage mapsPage;

    public static void main(String[] args) {

        while(true){
            addNewDriverTravelTime();
            processFirstUnmatched();
            try {
                Thread.sleep(10000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public static void addNewDriverTravelTime(){
        String message = getNewDrivers();
        JSONObject jsonObj = new JSONObject(message);
        if(jsonObj.isNull("commuters")){
            System.out.println("No new drivers");
            return;
        }
        JSONArray newDrivers = new JSONArray(jsonObj.getString("commuters"));

        for(int i=0; i < newDrivers.length(); i++) {
            JSONObject driver = newDrivers.getJSONObject(i);
            System.out.println("Driver name and id is " + driver.getString("name") + " " + + driver.getInt("id"));
            if(!driver.isNull("result_message")){
                Assert.isTrue(driver.getInt("result_code") == 0, driver.getString("result_message"));
            }

            JSONObject workAddress = driver.getJSONObject("work_address");
            JSONObject homeAddress = driver.getJSONObject("home_address");

            String url = "https://www.google.com/maps/dir/"+homeAddress.getString("latitude")+","+homeAddress.getString("longitude")+"/"+workAddress.getString("latitude")+","+workAddress.getString("longitude");
            String totalTime = convertToMinutes(getTotalTime(url));
            if(!totalTime.equals("0")){
                saveDriverTravelTime(String.valueOf(driver.getInt("id")), totalTime);
            }
        }
    }

    public static void saveDriverTravelTime(String driver, String tripTime){
        //body form data
        JSONObject body =new JSONObject();
        body.put("id", driver);
        body.put("travel_time",String.valueOf(tripTime));

        OkHttpClient client = new OkHttpClient().newBuilder()
                .build();
        Map<String, String> headers = new HashMap<>();
        Headers headerBuild = Headers.of(headers);
        MediaType mediaType = MediaType.parse("application/json");
        RequestBody requestBody = RequestBody.create(mediaType, body.toString());
        Request request = new Request.Builder()
                .url("https://workride.co.za/api/update/commuter/traveltime")
                .method("PUT", requestBody)
                .headers(headerBuild)
                .build();
        try {
            Response response = client.newCall(request).execute();
            String message = response.body().string();
            System.out.println(message);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void processFirstUnmatched(){
        String message = getUnmatched();
        JSONObject jsonObj = new JSONObject(message);
        String driver = String.valueOf(jsonObj.getInt("driver"));
        String passenger = String.valueOf(jsonObj.getInt("passenger"));
        String url = jsonObj.getString("url");
        String totalTime = convertToMinutes(getTotalTime(url));
        saveMatch(driver, passenger, totalTime, url);
    }

    public static String getTotalTime(String url){
        if(driver == null){
            driver = createLocalDriver();
            mapsPage = new MapsPage(driver);
            driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(60));
            driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(60));
            driver.manage().window().maximize();
        }

        driver.get(url);
        return mapsPage.getTime();
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
            int minutes = (minutesGroup1 != null) ? Integer.parseInt(minutesGroup1) :
                    (minutesGroup2 != null) ? Integer.parseInt(minutesGroup2) : 0;


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
            url = new URL("https://workride.co.za/api/tomatch");
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
            System.out.println(content);
            return content.toString();

        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }

    public static String getNewDrivers() {
        URL url;
        HttpURLConnection con;
        try {
            url = new URL("https://workride.co.za/api/newdrivers");
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
            System.out.println(content);
            return content.toString();

        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }
    public static void saveMatch(String driver, String passenger, String tripTime, String mapLink){
        //body form data
        JSONObject body =new JSONObject();
        body.put("driver", driver);
        body.put("passenger",passenger);
        body.put("totalTrip",String.valueOf(tripTime));
        body.put("mapLink",mapLink);
        OkHttpClient client = new OkHttpClient().newBuilder()
                .build();
        Map<String, String> headers = new HashMap<>();
        Headers headerBuild = Headers.of(headers);
        MediaType mediaType = MediaType.parse("application/json");
        RequestBody requestBody = RequestBody.create(mediaType, body.toString());
        Request request = new Request.Builder()
                .url("https://workride.co.za/api/savematch")
                .method("POST", requestBody)
                .headers(headerBuild)
                .build();
        try {
            Response response = client.newCall(request).execute();
            String message = response.body().string();
            System.out.println(message);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static WebDriver createLocalDriver() {
        if (isWindows()) {
            System.setProperty("webdriver.chrome.driver", "C:\\Users\\Dell\\IdeaProjects\\gs-serving-web-content\\getTravelTime\\src\\main\\resources\\windows\\chromedriver.exe");
        } else {
            System.setProperty("webdriver.chrome.driver", "/src/src/main/resources/linux/chromedriver");
        }

        ChromeOptions options = new ChromeOptions();
        options.addArguments("--remote-allow-origins=*");
        //the sandbox removes unnecessary privileges from the processes that don't need them in Chrome, for security purposes. Disabling the sandbox makes your PC more vulnerable to exploits via webpages, so Google don't recommend it.
        options.addArguments("--no-sandbox");
        //"--disable-dev-shm-usage" Only added when CI system environment variable is set or when inside a docker instance. The /dev/shm partition is too small in certain VM environments, causing Chrome to fail or crash.
        options.addArguments("--disable-dev-shm-usage");
        options.addArguments("--headless");
        return new ChromeDriver(options);
    }

    public static boolean isWindows() {
        // Get the value of the "os.name" system property
        String osName = System.getProperty("os.name");

        // Check if the operating system is Windows
        return osName.toLowerCase().contains("win");
    }

    private static String getDriverAbsolutePath(){
        URL res = com.sun.tools.javac.Main.class.getClassLoader().getResource("chromedriver.exe");
        File file = null;
        try {
            assert res != null;
            file = Paths.get(res.toURI()).toFile();
        } catch (URISyntaxException e) {
            //logger.error(String.format("Error %1$s", e.getMessage()));
        }
        assert file != null;
        return file.getAbsolutePath();
    }
}