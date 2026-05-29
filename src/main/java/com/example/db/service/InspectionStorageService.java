package com.example.db.service;

import com.example.db.entities.InspectionEntity;
import com.example.db.entities.InspectionStagingEntity;
import com.example.db.repository.InspectionRepository;
import com.example.db.repository.InspectionStagingRepository;
import com.example.jsonparser.dto.InspectionDto;
import org.springframework.jdbc.core.JdbcTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.StringJoiner;

@Service
public class InspectionStorageService {

    private static final Logger log = LoggerFactory.getLogger(InspectionStorageService.class);

    private final InspectionRepository inspectionRepository;
    private final InspectionStagingRepository inspectionStagingRepository;
    private final ContractorStorageService contractorStorageService;
    private final JdbcTemplate jdbcTemplate;

    public InspectionStorageService(
            InspectionRepository inspectionRepository,
            InspectionStagingRepository inspectionStagingRepository,
            ContractorStorageService contractorStorageService,
            JdbcTemplate jdbcTemplate
    ) {
        this.inspectionRepository = inspectionRepository;
        this.inspectionStagingRepository = inspectionStagingRepository;
        this.contractorStorageService = contractorStorageService;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public void replaceAll(List<InspectionDto> inspections) {
        contractorStorageService.clearAll();
        inspectionRepository.deleteAll();
        saveAll(inspections);
    }

    @Transactional
    public void saveAllAndRebuildContractorStats(List<InspectionDto> inspections) {
        saveAll(inspections);
        rebuildContractorStatsFromInspections();
    }

    @Transactional
    public void clearStaging() {
        inspectionStagingRepository.deleteAllInBatch();
    }

    @Transactional
    public void saveAllToStaging(List<InspectionDto> inspections) {
        if (inspections == null || inspections.isEmpty()) {
            log.info("No inspections to save to staging");
            return;
        }

        Map<Long, InspectionStagingEntity> validInspections = new LinkedHashMap<>();
        int invalid = 0;
        int duplicates = 0;

        for (InspectionDto dto : inspections) {
            InspectionStagingEntity entity = toStagingEntity(dto);

            if (entity == null) {
                invalid++;
                continue;
            }

            if (validInspections.putIfAbsent(entity.getPublicId(), entity) != null) {
                duplicates++;
            }
        }

        batchInsertStaging(validInspections.values().stream().toList());

        log.info("Inspection staging batch processed. saved={}, invalid={}, duplicates={}",
                validInspections.size(), invalid, duplicates);
    }

    @Transactional
    public void replaceMainDataFromStaging() {
        contractorStorageService.clearAll();
        inspectionRepository.deleteAllInBatch();

        List<InspectionEntity> inspections = inspectionStagingRepository.findAll()
                .stream()
                .map(this::toInspectionEntity)
                .toList();

        inspectionRepository.saveAll(inspections);
        rebuildContractorStatsFromInspections();

        log.info("Main inspection data was replaced from staging. inspections={}", inspections.size());
    }

    @Transactional
    public void saveAll(List<InspectionDto> inspections) {
        if (inspections == null || inspections.isEmpty()) {
            log.info("No inspections to save");
            return;
        }

        int saved = 0;
        int skipped = 0;

        for (InspectionDto dto : inspections) {
            if (save(dto)) {
                saved++;
            } else {
                skipped++;
            }
        }

        log.info("Inspection batch processed. saved={}, skipped={}", saved, skipped);
    }

    @Transactional
    public void clearAll() {
        inspectionRepository.deleteAll();
    }

    @Transactional
    public void clearAllData() {
        contractorStorageService.clearAll();
        inspectionRepository.deleteAll();
    }

    @Transactional(readOnly = true)
    public Optional<InspectionEntity> findLatestInspection() {
        return inspectionRepository.findFirstByOrderByDateDesc();
    }

    @Transactional(readOnly = true)
    public List<InspectionEntity> findAll() {
        return inspectionRepository.findAll();
    }

    @Transactional
    public boolean save(InspectionDto dto) {
        if (!isValid(dto)) {
            return false;
        }

        if (inspectionRepository.existsById(dto.publicId())) {
            log.debug("Inspection was skipped because it already exists. publicId={}", dto.publicId());
            return false;
        }

        int violations = normalizeViolationsCount(dto);
        int criticalViolations = normalizeCriticalViolationsCount(dto);

        InspectionEntity inspection = new InspectionEntity(
                dto.publicId(),
                dto.id(),
                dto.date(),
                violations,
                criticalViolations,
                dto.locationName(),
                dto.locationAddress(),
                contractorsToText(dto.contractors())
        );

        inspectionRepository.save(inspection);

        contractorStorageService.saveOrUpdateContractors(
                dto.contractors(),
                violations,
                criticalViolations
        );

        return true;
    }

    @Transactional
    public boolean saveToStaging(InspectionDto dto) {
        InspectionStagingEntity inspection = toStagingEntity(dto);

        if (inspection == null) {
            return false;
        }

        if (inspectionStagingRepository.existsById(inspection.getPublicId())) {
            log.debug("Inspection was skipped in staging because it already exists. publicId={}", inspection.getPublicId());
            return false;
        }

        inspectionStagingRepository.save(inspection);

        return true;
    }

    private InspectionStagingEntity toStagingEntity(InspectionDto dto) {
        if (!isValid(dto)) {
            return null;
        }

        int violations = normalizeViolationsCount(dto);
        int criticalViolations = normalizeCriticalViolationsCount(dto);

        return new InspectionStagingEntity(
                dto.publicId(),
                dto.id(),
                dto.date(),
                violations,
                criticalViolations,
                dto.locationName(),
                dto.locationAddress(),
                contractorsToText(dto.contractors())
        );
    }

    private void batchInsertStaging(List<InspectionStagingEntity> inspections) {
        if (inspections.isEmpty()) {
            return;
        }

        jdbcTemplate.batchUpdate(
                """
                        insert into inspections_staging (
                            public_id,
                            api_id,
                            inspection_date,
                            violations_count,
                            critical_violations_count,
                            location_name,
                            location_address,
                            contractors_text
                        ) values (?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                inspections,
                inspections.size(),
                (statement, inspection) -> {
                    statement.setLong(1, inspection.getPublicId());

                    if (inspection.getId() == null) {
                        statement.setObject(2, null);
                    } else {
                        statement.setLong(2, inspection.getId());
                    }

                    statement.setTimestamp(3, Timestamp.valueOf(inspection.getDate()));
                    statement.setInt(4, inspection.getViolationsCount());
                    statement.setInt(5, inspection.getCriticalViolationsCount());
                    statement.setString(6, inspection.getLocationName());
                    statement.setString(7, inspection.getLocationAddress());
                    statement.setString(8, inspection.getContractorsText());
                }
        );
    }

    @Transactional
    public void rebuildContractorStatsFromInspections() {
        contractorStorageService.clearAll();

        List<InspectionEntity> inspections = inspectionRepository.findAll();

        for (InspectionEntity inspection : inspections) {
            List<String> contractors = parseContractorsText(inspection.getContractorsText());

            contractorStorageService.saveOrUpdateContractors(
                    contractors,
                    inspection.getViolationsCount(),
                    inspection.getCriticalViolationsCount()
            );
        }

        log.info("Contractor stats were rebuilt from stored inspections. inspections={}", inspections.size());
    }

    private InspectionEntity toInspectionEntity(InspectionStagingEntity staging) {
        return new InspectionEntity(
                staging.getPublicId(),
                staging.getId(),
                staging.getDate(),
                staging.getViolationsCount(),
                staging.getCriticalViolationsCount(),
                staging.getLocationName(),
                staging.getLocationAddress(),
                staging.getContractorsText()
        );
    }

    private boolean isValid(InspectionDto dto) {
        if (dto == null) {
            log.warn("Inspection was skipped because DTO is null");
            return false;
        }

        if (dto.publicId() == null) {
            log.warn("Inspection was skipped because publicId is null. apiId={}", dto.id());
            return false;
        }

        if (dto.date() == null) {
            log.warn("Inspection was skipped because date is null. publicId={}, apiId={}", dto.publicId(), dto.id());
            return false;
        }

        return true;
    }

    private int normalizeViolationsCount(InspectionDto dto) {
        Integer violationsCount = dto.violationsCount();

        if (violationsCount == null) {
            return 0;
        }

        if (violationsCount < 0) {
            log.warn("Negative violations count was replaced with 0. publicId={}, violationsCount={}",
                    dto.publicId(), violationsCount);
            return 0;
        }

        return violationsCount;
    }

    private int normalizeCriticalViolationsCount(InspectionDto dto) {
        Integer criticalViolationsCount = dto.criticalViolationsCount();

        if (criticalViolationsCount == null) {
            return 0;
        }

        if (criticalViolationsCount < 0) {
            log.warn("Negative critical violations count was replaced with 0. publicId={}, criticalViolationsCount={}",
                    dto.publicId(), criticalViolationsCount);
            return 0;
        }

        return criticalViolationsCount;
    }

    private String contractorsToText(List<String> contractors) {
        if (contractors == null || contractors.isEmpty()) {
            return "";
        }

        StringJoiner joiner = new StringJoiner("; ");

        contractors.stream()
                .filter(contractor -> contractor != null && !contractor.isBlank())
                .map(String::trim)
                .map(contractor -> contractor.replaceAll("\\s+", " "))
                .distinct()
                .forEach(joiner::add);

        return joiner.toString();
    }

    private List<String> parseContractorsText(String contractorsText) {
        if (contractorsText == null || contractorsText.isBlank()) {
            return List.of();
        }

        return Arrays.stream(contractorsText.split(";"))
                .map(String::trim)
                .filter(contractor -> !contractor.isBlank())
                .toList();
    }
}
