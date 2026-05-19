package com.example.jsonparser.dto;

import java.time.LocalDateTime;

public record InspectionHistoryDto(
        Long publicId,
        Long apiId,
        LocalDateTime date,
        int violationsCount,
        int criticalViolationsCount,
        int totalScore,
        String locationName,
        String locationAddress
) {
}
