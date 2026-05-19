package com.example.jsonparser.dto;

public record ContractorStatsDto(
        String contractorName,
        int violationsCount,
        int criticalViolationsCount,
        int inspectionsCount,
        int totalScore,
        double rating
) {
}
