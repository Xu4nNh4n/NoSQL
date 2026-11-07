package com.api.supermarket.config;

import java.time.Clock;
import java.time.ZoneId;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TimeConfig {

    @Bean
    public Clock clock(@Value("${app.timezone:Asia/Ho_Chi_Minh}") String timezone) {
        return Clock.system(ZoneId.of(timezone));
    }
}
