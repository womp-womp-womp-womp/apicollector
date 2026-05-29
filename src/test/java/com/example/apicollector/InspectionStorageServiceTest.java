package com.example.apicollector;

import com.example.db.entities.ContractorEntity;
import com.example.db.entities.InspectionEntity;
import com.example.db.repository.ContractorRepository;
import com.example.db.repository.InspectionRepository;
import com.example.db.service.InspectionStorageService;
import com.example.jsonparser.dto.InspectionDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

@SpringBootTest(properties = {
        "app.database.enabled=false",
        "spring.datasource.url=jdbc:h2:mem:storage_service_test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class InspectionStorageServiceTest {

    @Autowired
    private InspectionStorageService storageService;

    @Autowired
    private InspectionRepository inspectionRepository;

    @Autowired
    private ContractorRepository contractorRepository;

    @BeforeEach
    void setUp() {
        storageService.clearStaging();
        storageService.clearAllData();
    }

    @Test
    void saveAllToStagingDoesNotChangeMainData() {
        InspectionDto existing = inspection(1L, "Old Contractor", 2);
        InspectionDto staged = inspection(2L, "New Contractor", 7);

        storageService.saveAll(List.of(existing));
        storageService.saveAllToStaging(List.of(staged));

        assertThat(inspectionRepository.findAll())
                .extracting(InspectionEntity::getPublicId)
                .containsExactly(1L);
        assertThat(contractorRepository.findAll())
                .extracting(ContractorEntity::getContractorName)
                .containsExactly("Old Contractor");
    }

    @Test
    void replaceMainDataFromStagingReplacesMainDataAndRebuildsContractorsInOneCall() {
        InspectionDto existing = inspection(1L, "Old Contractor", 2);
        InspectionDto staged = inspection(2L, "New Contractor", 7);

        storageService.saveAll(List.of(existing));
        storageService.saveAllToStaging(List.of(staged));
        storageService.replaceMainDataFromStaging();

        assertThat(inspectionRepository.findAll())
                .extracting(InspectionEntity::getPublicId)
                .containsExactly(2L);

        assertThat(contractorRepository.findAll())
                .extracting(
                        ContractorEntity::getContractorName,
                        ContractorEntity::getViolationsCount,
                        ContractorEntity::getInspectionsCount
                )
                .containsExactly(tuple("New Contractor", 7, 1));
    }

    @Test
    void saveAllSkipsInvalidAndDuplicateInspectionsAndNormalizesNegativeCounts() {
        InspectionDto validWithNegativeCounts = new InspectionDto(
                1001L,
                1L,
                LocalDateTime.of(2026, 1, 1, 12, 0),
                -5,
                -1,
                "Location",
                "Address",
                List.of(" Alpha ", "Alpha", " ")
        );
        InspectionDto duplicate = inspection(1L, "Alpha", 9);
        InspectionDto missingPublicId = new InspectionDto(1002L, null, LocalDateTime.now(), 1, 1, null, null, List.of());
        InspectionDto missingDate = new InspectionDto(1003L, 3L, null, 1, 1, null, null, List.of());

        storageService.saveAll(Arrays.asList(validWithNegativeCounts, duplicate, missingPublicId, missingDate, null));

        assertThat(inspectionRepository.findAll())
                .extracting(
                        InspectionEntity::getPublicId,
                        InspectionEntity::getViolationsCount,
                        InspectionEntity::getCriticalViolationsCount,
                        InspectionEntity::getContractorsText
                )
                .containsExactly(tuple(1L, 0, 0, "Alpha"));
        assertThat(contractorRepository.findAll())
                .extracting(
                        ContractorEntity::getContractorName,
                        ContractorEntity::getViolationsCount,
                        ContractorEntity::getCriticalViolationsCount
                )
                .containsExactly(tuple("Alpha", 0, 0));
    }

    @Test
    void saveAllToStagingSkipsDuplicatesAndInvalidRows() {
        InspectionDto valid = inspection(1L, "Alpha", 1);
        InspectionDto duplicate = inspection(1L, "Alpha", 2);
        InspectionDto invalid = new InspectionDto(1002L, null, LocalDateTime.now(), 1, 1, null, null, List.of());

        storageService.saveAllToStaging(Arrays.asList(valid, duplicate, invalid, null));
        storageService.replaceMainDataFromStaging();

        assertThat(inspectionRepository.findAll())
                .extracting(InspectionEntity::getPublicId, InspectionEntity::getViolationsCount)
                .containsExactly(tuple(1L, 1));
    }

    @Test
    void replaceAllClearAllAndFindMethodsWorkOnMainData() {
        InspectionDto oldInspection = inspection(1L, "Old Contractor", 1);
        InspectionDto newerInspection = inspection(2L, "New Contractor", 2);

        storageService.saveAll(List.of(oldInspection));
        storageService.replaceAll(List.of(newerInspection));

        assertThat(storageService.findAll())
                .extracting(InspectionEntity::getPublicId)
                .containsExactly(2L);
        assertThat(storageService.findLatestInspection())
                .isPresent()
                .get()
                .extracting(InspectionEntity::getPublicId)
                .isEqualTo(2L);

        storageService.clearAll();

        assertThat(storageService.findAll()).isEmpty();
    }

    private InspectionDto inspection(long publicId, String contractor, int violationsCount) {
        return new InspectionDto(
                publicId + 1000,
                publicId,
                LocalDateTime.of(2026, 1, 1, 12, 0).plusDays(publicId),
                violationsCount,
                0,
                "Location " + publicId,
                "Address " + publicId,
                List.of(contractor)
        );
    }
}
