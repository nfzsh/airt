package com.airt;

import com.airt.config.AirtProperties;
import com.airt.config.LlmProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableConfigurationProperties({AirtProperties.class, LlmProperties.class})
@EnableScheduling
public class AirtApplication {

    public static void main(String[] args) {
        SpringApplication.run(AirtApplication.class, args);
    }
}
