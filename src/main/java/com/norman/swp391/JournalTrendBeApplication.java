package com.norman.swp391;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@EnableCaching
@SpringBootApplication
public class JournalTrendBeApplication {

    public static void main(String[] args) {
        SpringApplication.run(JournalTrendBeApplication.class, args);
    }

}
