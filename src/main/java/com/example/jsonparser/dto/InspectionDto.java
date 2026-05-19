package com.example.jsonparser.dto;

import java.time.LocalDateTime;
import java.util.List;

public record InspectionDto(
        Long id,
        Long publicId,
        LocalDateTime date,
        Integer violationsCount,
        Integer criticalViolationsCount,
        String locationName,
        String locationAddress,
        List<String> contractors
) {
    public void printFields() {
        for (var component : this.getClass().getRecordComponents()) {
            try {
                Object value = component.getAccessor().invoke(this);
                System.out.println(component.getName() + ": " + value);
            } catch (Exception e) {
                System.err.println("Ошибка при чтении поля " + component.getName());
            }
        }
    }
}
