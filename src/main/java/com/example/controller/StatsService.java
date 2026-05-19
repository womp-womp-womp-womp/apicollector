package com.example.controller;

import com.example.db.entities.ContractorEntity;
import com.example.db.entities.InspectionEntity;
import com.example.db.repository.ContractorRepository;
import com.example.db.repository.InspectionRepository;
import com.example.exception.NotFoundException;
import com.example.jsonparser.dto.ContractorDetailDto;
import com.example.jsonparser.dto.ContractorStatsDto;
import com.example.jsonparser.dto.InspectionHistoryDto;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@Service
public class StatsService {

    private static final int MAX_LIMIT = 1000;
    private static final int MIN_INSPECTIONS_COUNT = 3;

    private final ContractorRepository contractorRepository;
    private final InspectionRepository inspectionRepository;

    public StatsService(ContractorRepository contractorRepository, InspectionRepository inspectionRepository) {
        this.contractorRepository = contractorRepository;
        this.inspectionRepository = inspectionRepository;
    }

    @Transactional(readOnly = true)
    public List<ContractorStatsDto> getTopContractorsByViolations(int limit) {
        if (limit <= 0 || limit > MAX_LIMIT) {
            throw new IllegalArgumentException("limit must be between 1 and " + MAX_LIMIT);
        }

        return contractorRepository
                .findAllByInspectionsCountGreaterThanEqualOrderByRatingAscInspectionsCountDescTotalScoreAscViolationsCountAscCriticalViolationsCountAsc(
                        MIN_INSPECTIONS_COUNT,
                        PageRequest.of(0, limit)
                )
                .stream()
                .map(this::toStatsDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ContractorStatsDto> findContractors(
            String query,
            int minInspections,
            Double maxRating,
            String sort,
            String direction,
            int limit
    ) {
        if (limit <= 0 || limit > MAX_LIMIT) {
            throw new IllegalArgumentException("limit must be between 1 and " + MAX_LIMIT);
        }

        int effectiveMinInspections = Math.max(MIN_INSPECTIONS_COUNT, minInspections);
        String normalizedQuery = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        Comparator<ContractorEntity> comparator = comparator(sort);

        if ("desc".equalsIgnoreCase(direction)) {
            comparator = comparator.reversed();
        }

        return contractorRepository.findAll()
                .stream()
                .filter(contractor -> contractor.getInspectionsCount() >= effectiveMinInspections)
                .filter(contractor -> normalizedQuery.isBlank()
                        || contractor.getContractorName().toLowerCase(Locale.ROOT).contains(normalizedQuery))
                .filter(contractor -> maxRating == null || contractor.getRating() <= maxRating)
                .sorted(comparator.thenComparing(ContractorEntity::getContractorName))
                .limit(limit)
                .map(this::toStatsDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public ContractorDetailDto getContractor(String contractorName) {
        ContractorEntity entity = contractorRepository.findById(normalizeContractorName(contractorName))
                .orElseThrow(() -> new NotFoundException("Contractor was not found"));

        return new ContractorDetailDto(
                entity.getContractorName(),
                entity.getViolationsCount(),
                entity.getCriticalViolationsCount(),
                entity.getInspectionsCount(),
                entity.getTotalScore(),
                entity.getRating()
        );
    }

    @Transactional(readOnly = true)
    public List<InspectionHistoryDto> getInspectionHistory(String contractorName, int limit) {
        if (limit <= 0 || limit > MAX_LIMIT) {
            throw new IllegalArgumentException("limit must be between 1 and " + MAX_LIMIT);
        }

        String normalizedName = normalizeContractorName(contractorName);

        return inspectionRepository.findAll()
                .stream()
                .filter(inspection -> hasContractor(inspection, normalizedName))
                .sorted(Comparator.comparing(InspectionEntity::getDate, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(limit)
                .map(this::toHistoryDto)
                .toList();
    }

    private ContractorStatsDto toStatsDto(ContractorEntity entity) {
        return new ContractorStatsDto(
                entity.getContractorName(),
                entity.getViolationsCount(),
                entity.getCriticalViolationsCount(),
                entity.getInspectionsCount(),
                entity.getTotalScore(),
                entity.getRating()
        );
    }

    private Comparator<ContractorEntity> comparator(String sort) {
        return switch (sort == null ? "" : sort.trim().toLowerCase(Locale.ROOT)) {
            case "name" -> Comparator.comparing(ContractorEntity::getContractorName);
            case "checks", "inspections" -> Comparator.comparingInt(ContractorEntity::getInspectionsCount);
            case "regular" -> Comparator.comparingInt(ContractorEntity::getViolationsCount);
            case "critical" -> Comparator.comparingInt(ContractorEntity::getCriticalViolationsCount);
            case "total" -> Comparator.comparingInt(ContractorEntity::getTotalScore);
            default -> Comparator
                    .comparingDouble(ContractorEntity::getRating)
                    .thenComparing(Comparator.comparingInt(ContractorEntity::getInspectionsCount).reversed())
                    .thenComparingInt(ContractorEntity::getTotalScore);
        };
    }

    private String normalizeContractorName(String contractorName) {
        if (contractorName == null || contractorName.isBlank()) {
            throw new IllegalArgumentException("Contractor name must not be empty");
        }

        return contractorName.trim().replaceAll("\\s+", " ");
    }

    private boolean hasContractor(InspectionEntity inspection, String contractorName) {
        String contractorsText = inspection.getContractorsText();

        if (contractorsText == null || contractorsText.isBlank()) {
            return false;
        }

        for (String candidate : contractorsText.split(";")) {
            if (normalizeContractorName(candidate).equals(contractorName)) {
                return true;
            }
        }

        return false;
    }

    private InspectionHistoryDto toHistoryDto(InspectionEntity entity) {
        int totalScore = entity.getViolationsCount() + entity.getCriticalViolationsCount() * 5;

        return new InspectionHistoryDto(
                entity.getPublicId(),
                entity.getId(),
                entity.getDate(),
                entity.getViolationsCount(),
                entity.getCriticalViolationsCount(),
                totalScore,
                entity.getLocationName(),
                entity.getLocationAddress()
        );
    }
}
