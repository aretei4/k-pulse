package com.kahga.pluse;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class PluseApplication {

    public static void main(String[] args) {
    	System.setProperty("user.timezone", "Asia/Kolkata");
        SpringApplication.run(PluseApplication.class, args);
    }
}
