package com.example;

import com.example.apiparser.InspectionApiClient;

import com.example.jsonparser.dto.InspectionDto;
import com.example.jsonparser.parser.InspectionJsonParser;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;


//@Component
public class TestRunner implements CommandLineRunner {

    private final InspectionApiClient myService; // ваш сервис с методом
    private final InspectionJsonParser parser;

    public TestRunner(InspectionApiClient myService, InspectionJsonParser parser) {
        this.myService = myService;
        this.parser = parser;
    }

    @Override
    public void run(String... args) throws Exception {
        System.out.println("--- ТЕСТОВЫЙ ВЫЗОВ МЕТОДА ---");
                var result = myService.fetchJson(2, 1);
        var total = parser.parseTotal(result);
//        for (InspectionDto i : parser.parse(result)) {
//            i.printFields();
//        }
        //System.out.println("Результат: " + result);
        System.out.println("total: " + total);
    }
}
