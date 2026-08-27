package com.checador;

import jakarta.annotation.PostConstruct;
import java.util.TimeZone;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableJpaAuditing
@EnableScheduling
public class CheckadorApplication {

    @PostConstruct
    public void init() {
        TimeZone.setDefault(TimeZone.getTimeZone("America/Mexico_City"));
    }

    public static void main(String[] args) {
        TimeZone.setDefault(TimeZone.getTimeZone("America/Mexico_City"));
        SpringApplication.run(CheckadorApplication.class, args);
    }
}
