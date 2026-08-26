package com.checador;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@SpringBootApplication
@EnableJpaAuditing
public class CheckadorApplication {
    public static void main(String[] args) {
        SpringApplication.run(CheckadorApplication.class, args);
    }
}
