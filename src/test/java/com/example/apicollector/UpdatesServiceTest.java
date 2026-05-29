package com.example.apicollector;

import com.example.apiparser.InspectionApiClient;
import com.example.db.entities.InspectionEntity;
import com.example.db.service.InspectionStorageService;
import com.example.exception.IncrementalUpdateUnavailableException;
import com.example.jsonparser.dto.InspectionDto;
import com.example.jsonparser.parser.InspectionJsonParser;
import com.example.controller.UpdatesService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UpdatesServiceTest {

    private static final String API_KEY = "api-secret";

    private InspectionApiClient inspectionApiClient;
    private InspectionJsonParser jsonParser;
    private InspectionStorageService storageService;
    private UpdatesService updatesService;

    @BeforeEach
    void setUp() {
        inspectionApiClient = mock(InspectionApiClient.class);
        jsonParser = mock(InspectionJsonParser.class);
        storageService = mock(InspectionStorageService.class);
        updatesService = new UpdatesService(inspectionApiClient, jsonParser, storageService);
        ReflectionTestUtils.setField(updatesService, "perPage", 10L);
        ReflectionTestUtils.setField(updatesService, "decayTime", 0L);
    }

    @Test
    void updateAllDataLoadsPagesToStagingBeforeReplacingMainData() {
        InspectionDto inspection = inspection(1L);
        List<InspectionDto> parsed = List.of(inspection);

        when(inspectionApiClient.fetchJson(1, 1, API_KEY)).thenReturn("total");
        when(inspectionApiClient.fetchJson(1, 10, API_KEY)).thenReturn("page");
        when(jsonParser.parseTotal("total")).thenReturn(1L);
        when(jsonParser.parse("page")).thenReturn(parsed);

        updatesService.updateAllData(API_KEY);

        InOrder inOrder = inOrder(storageService);
        inOrder.verify(storageService).clearStaging();
        inOrder.verify(storageService).saveAllToStaging(parsed);
        inOrder.verify(storageService).replaceMainDataFromStaging();
    }

    @Test
    void updateAllDataDoesNotReplaceMainDataWhenPageImportFails() {
        when(inspectionApiClient.fetchJson(1, 1, API_KEY)).thenReturn("total");
        when(inspectionApiClient.fetchJson(1, 10, API_KEY)).thenReturn("page");
        when(jsonParser.parseTotal("total")).thenReturn(1L);
        when(jsonParser.parse("page")).thenThrow(new IllegalStateException("broken page"));

        assertThatThrownBy(() -> updatesService.updateAllData(API_KEY))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("broken page");

        verify(storageService).clearStaging();
        verify(storageService, never()).replaceMainDataFromStaging();
        assertThat(updatesService.getStatus()).containsEntry("status", "failed");
    }

    @Test
    void updateToDateRejectsEmptyOrInvalidLocalState() {
        when(storageService.findLatestInspection()).thenReturn(Optional.empty());

        assertThatThrownBy(() -> updatesService.updateToDate(API_KEY))
                .isInstanceOf(IncrementalUpdateUnavailableException.class)
                .hasMessage("Incremental update cannot be started because local inspections are empty. Run full update as an admin first");

        when(storageService.findLatestInspection()).thenReturn(Optional.of(
                new InspectionEntity(1L, 2L, null, 0, 0, "Location", "Address", "Contractor")
        ));

        assertThatThrownBy(() -> updatesService.updateToDate(API_KEY))
                .isInstanceOf(IncrementalUpdateUnavailableException.class)
                .hasMessage("Incremental update cannot be started because latest local inspection has no date. Run full update as an admin first");
    }

    @Test
    void updateToDateSavesOnlyRecordsNewerThanDecayWindow() {
        LocalDateTime latest = LocalDateTime.of(2026, 1, 10, 12, 0);
        InspectionDto newer = inspection(2L, latest.plusMinutes(1));
        InspectionDto old = inspection(3L, latest.minusMinutes(1));
        ReflectionTestUtils.setField(updatesService, "decayTime", 0L);
        when(storageService.findLatestInspection()).thenReturn(Optional.of(
                new InspectionEntity(1L, 1001L, latest, 0, 0, "Location", "Address", "Contractor")
        ));
        when(inspectionApiClient.fetchJson(1, 1, API_KEY)).thenReturn("total");
        when(inspectionApiClient.fetchJson(1, 10, API_KEY)).thenReturn("page");
        when(jsonParser.parseTotal("total")).thenReturn(1L);
        when(jsonParser.parse("page")).thenReturn(List.of(newer, old));

        updatesService.updateToDate(API_KEY);

        verify(storageService).saveAllAndRebuildContractorStats(List.of(newer));
        assertThat(updatesService.getStatus()).containsEntry("status", "completed");
    }

    @Test
    void updateToDateSkipsNullDatesAndDoesNotSaveWhenNothingNew() {
        LocalDateTime latest = LocalDateTime.of(2026, 1, 10, 12, 0);
        when(storageService.findLatestInspection()).thenReturn(Optional.of(
                new InspectionEntity(1L, 1001L, latest, 0, 0, "Location", "Address", "Contractor")
        ));
        when(inspectionApiClient.fetchJson(1, 1, API_KEY)).thenReturn("total");
        when(inspectionApiClient.fetchJson(1, 10, API_KEY)).thenReturn("page");
        when(jsonParser.parseTotal("total")).thenReturn(1L);
        when(jsonParser.parse("page")).thenReturn(List.of(inspection(2L, null)));

        updatesService.updateToDate(API_KEY);

        verify(storageService, never()).saveAllAndRebuildContractorStats(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void invalidSettingsAreRejected() {
        ReflectionTestUtils.setField(updatesService, "perPage", 0L);

        assertThatThrownBy(() -> updatesService.updateAllData(API_KEY))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("app.per-page must be greater than 0");

        ReflectionTestUtils.setField(updatesService, "perPage", 10L);
        ReflectionTestUtils.setField(updatesService, "decayTime", -1L);

        assertThatThrownBy(() -> updatesService.updateAllData(API_KEY))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("app.decay-time must not be negative");
    }

    private InspectionDto inspection(long publicId) {
        return inspection(publicId, LocalDateTime.of(2026, 1, 1, 12, 0));
    }

    private InspectionDto inspection(long publicId, LocalDateTime date) {
        return new InspectionDto(
                publicId + 1000,
                publicId,
                date,
                1,
                0,
                "Location",
                "Address",
                List.of("Contractor")
        );
    }
}
