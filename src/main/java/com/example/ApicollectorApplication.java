package com.example;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestClient;


@SpringBootApplication
public class ApicollectorApplication {
    @Bean
    public RestClient.Builder restClientBuilder() {
        return RestClient.builder();
    }

    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        // Обязательно добавьте этот модуль, так как у вас в DTO есть LocalDateTime
        mapper.registerModule(new JavaTimeModule());
        return mapper;
    }

    public static void main(String[] args) {
        SpringApplication.run(ApicollectorApplication.class, args);
    }

}
