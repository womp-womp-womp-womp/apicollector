package com.example.apicollector;

import com.example.controller.StatsService;
import com.example.db.entities.ContractorEntity;
import com.example.db.entities.InspectionEntity;
import com.example.db.repository.ContractorRepository;
import com.example.db.repository.InspectionRepository;
import com.example.exception.NotFoundException;
import com.example.jsonparser.dto.ContractorStatsDto;
import com.example.jsonparser.dto.InspectionHistoryDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StatsServiceTest {

    private ContractorRepository contractorRepository;
    private InspectionRepository inspectionRepository;
    private StatsService statsService;

    @BeforeEach
    void setUp() {
        contractorRepository = mock(ContractorRepository.class);
        inspectionRepository = mock(InspectionRepository.class);
        statsService = new StatsService(contractorRepository, inspectionRepository);
    }

    @Test
    void getTopContractorsDelegatesToRepositoryWithMinimumChecksAndLimit() {
        ContractorEntity contractor = contractor("Alpha", 3, 6, 1);
        when(contractorRepository
                .findAllByInspectionsCountGreaterThanEqualOrderByRatingAscInspectionsCountDescTotalScoreAscViolationsCountAscCriticalViolationsCountAsc(
                        eq(3),
                        any(Pageable.class)
                ))
                .thenReturn(List.of(contractor));

        List<ContractorStatsDto> result = statsService.getTopContractorsByViolations(5);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().contractorName()).isEqualTo("Alpha");
        verify(contractorRepository)
                .findAllByInspectionsCountGreaterThanEqualOrderByRatingAscInspectionsCountDescTotalScoreAscViolationsCountAscCriticalViolationsCountAsc(
                        eq(3),
                        any(Pageable.class)
                );
    }

    @Test
    void findContractorsFiltersSortsAndLimitsResults() {
        ContractorEntity alpha = contractor("Alpha Build", 3, 3, 0);
        ContractorEntity beta = contractor("Beta Build", 4, 1, 1);
        ContractorEntity hidden = contractor("Hidden", 2, 0, 0);
        when(contractorRepository.findAll()).thenReturn(List.of(beta, hidden, alpha));

        List<ContractorStatsDto> result = statsService.findContractors("build", 1, null, "total", "desc", 1);

        assertThat(result)
                .extracting(ContractorStatsDto::contractorName)
                .containsExactly("Beta Build");
    }

    @Test
    void getContractorNormalizesNameAndThrowsWhenMissing() {
        ContractorEntity contractor = contractor("Alpha Build", 3, 1, 0);
        when(contractorRepository.findById("Alpha Build")).thenReturn(Optional.of(contractor));

        assertThat(statsService.getContractor(" Alpha   Build ").contractorName()).isEqualTo("Alpha Build");

        when(contractorRepository.findById("Missing")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> statsService.getContractor("Missing"))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Contractor was not found");
        assertThatThrownBy(() -> statsService.getContractor(" "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Contractor name must not be empty");
    }

    @Test
    void getInspectionHistoryFiltersExactContractorAndSortsByDateDescending() {
        LocalDateTime older = LocalDateTime.of(2026, 1, 1, 10, 0);
        LocalDateTime newer = LocalDateTime.of(2026, 1, 2, 10, 0);
        when(inspectionRepository.findAll()).thenReturn(List.of(
                inspection(1L, older, "Other; Alpha Build", 1, 1),
                inspection(2L, newer, "Alpha Build", 2, 0),
                inspection(3L, newer.plusDays(1), "Alpha", 9, 9),
                inspection(4L, null, "", 9, 9)
        ));

        List<InspectionHistoryDto> history = statsService.getInspectionHistory(" Alpha   Build ", 10);

        assertThat(history)
                .extracting(InspectionHistoryDto::publicId)
                .containsExactly(2L, 1L);
        assertThat(history.get(1).totalScore()).isEqualTo(6);
    }

    @Test
    void invalidLimitsAreRejected() {
        assertThatThrownBy(() -> statsService.getTopContractorsByViolations(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("limit must be between 1 and 1000");
        assertThatThrownBy(() -> statsService.findContractors(null, 3, null, "rating", "asc", 1001))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("limit must be between 1 and 1000");
        assertThatThrownBy(() -> statsService.getInspectionHistory("Alpha", -1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("limit must be between 1 and 1000");
    }

    private ContractorEntity contractor(String name, int inspections, int violations, int critical) {
        ContractorEntity entity = new ContractorEntity(name, 0, 0);
        for (int i = 0; i < inspections; i++) {
            entity.addInspection(violations, critical);
        }
        return entity;
    }

    private InspectionEntity inspection(
            long publicId,
            LocalDateTime date,
            String contractors,
            int violations,
            int criticalViolations
    ) {
        return new InspectionEntity(
                publicId,
                publicId + 1000,
                date,
                violations,
                criticalViolations,
                "Location",
                "Address",
                contractors
        );
    }
}
