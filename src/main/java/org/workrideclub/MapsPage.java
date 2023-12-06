package org.workrideclub;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;

public class MapsPage {

    private final WebDriver driver;

    public MapsPage(WebDriver driver) {
        this.driver = driver;
    }

    public String getTime() {
        String time = "0 min";
        try{
            By by = By.xpath("//span[@id='section-directions-trip-travel-mode-0']/following-sibling::div//div[contains(@class,'fontHeadlineSmall')]");
            time = driver.findElement(by).getText();
            return time;
        }catch (Exception e){
            System.out.println("No time found");
            return time;
        }

    }

}
