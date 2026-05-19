package com.example.controller;

import com.example.apiparser.InspectionApiClient;
import com.example.db.entities.InspectionEntity;
import com.example.db.service.InspectionStorageService;
import com.example.exception.IncrementalUpdateUnavailableException;
import com.example.exception.UpdateAlreadyRunningException;
import com.example.exception.UpdateInterruptedException;
import com.example.jsonparser.dto.InspectionDto;
import com.example.jsonparser.parser.InspectionJsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;

@Service
public class UpdatesService {

    private static final Logger log = LoggerFactory.getLogger(UpdatesService.class);
    private static final Duration API_PAGE_DELAY = Duration.ofSeconds(2);

    private final InspectionApiClient inspectionApiClient;
    private final InspectionJsonParser jsonParser;
    private final InspectionStorageService storageService;
    private final ReentrantLock updateLock = new ReentrantLock();
    private volatile String status = "idle";
    private volatile String operation;
    private volatile LocalDateTime startedAt;
    private volatile LocalDateTime finishedAt;
    private volatile String message = "No update has been started";

    @Value("${app.per-page}")
    private long perPage;

    @Value("${app.decay-time}")
    private long decayTime;

    public UpdatesService(
            InspectionApiClient inspectionApiClient,
            InspectionJsonParser jsonParser,
            InspectionStorageService storageService
    ) {
        this.inspectionApiClient = inspectionApiClient;
        this.jsonParser = jsonParser;
        this.storageService = storageService;
    }

    public void updateAllData() {
        runWithUpdateLock(this::doUpdateAllData);
    }

    public void updateToDate() {
        runWithUpdateLock(this::doUpdateToDate);
    }

    public Map<String, Object> getStatus() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", status);
        result.put("operation", operation);
        result.put("startedAt", startedAt);
        result.put("finishedAt", finishedAt);
        result.put("message", message);
        return result;
    }

    private void doUpdateAllData() {
        validateSettings();

        inspectionApiClient.checkApiAvailability();

        long lastPage = getPagesCount();
        long processedRecords = 0;

        log.info("Full update started. pages={}, perPage={}", lastPage, perPage);

        storageService.clearStaging();

        for (long currentPage = 1; currentPage <= lastPage; currentPage++) {
            String json = inspectionApiClient.fetchJson(currentPage, perPage);
            List<InspectionDto> parsed = jsonParser.parse(json);
            storageService.saveAllToStaging(parsed);
            processedRecords += parsed.size();

            log.info("Full update page was downloaded and saved to staging. page={}/{}, records={}",
                    currentPage, lastPage, parsed.size());

            sleepBetweenApiRequests(currentPage, lastPage);
        }

        storageService.replaceMainDataFromStaging();

        log.info("Full update finished successfully. processedRecords={}", processedRecords);
    }

    private void doUpdateToDate() {
        validateSettings();

        Optional<InspectionEntity> latestInspectionOptional = storageService.findLatestInspection();

        if (latestInspectionOptional.isEmpty()) {
            throw new IncrementalUpdateUnavailableException(
                    "Incremental update cannot be started because local inspections are empty. Run full update as an admin first"
            );
        }

        InspectionEntity latestInspection = latestInspectionOptional.get();
        LocalDateTime latestLoadedDate = latestInspection.getDate();

        if (latestLoadedDate == null) {
            throw new IncrementalUpdateUnavailableException(
                    "Incremental update cannot be started because latest local inspection has no date. Run full update as an admin first"
            );
        }

        long lastPage = getPagesCount();
        boolean reachedOldData = false;
        List<InspectionDto> newInspections = new ArrayList<>();

        log.info("Incremental update started. latestLoadedDate={}, pages={}, perPage={}",
                latestLoadedDate, lastPage, perPage);

        for (long currentPage = 1; currentPage <= lastPage && !reachedOldData; currentPage++) {
            String json = inspectionApiClient.fetchJson(currentPage, perPage);
            List<InspectionDto> parsed = jsonParser.parse(json);

            for (InspectionDto dto : parsed) {
                if (dto.date() == null) {
                    log.warn("Inspection was ignored during incremental update because date is null. publicId={}, apiId={}",
                            dto.publicId(), dto.id());
                    continue;
                }

                if (!dto.date().isAfter(latestLoadedDate.minusMinutes(decayTime))) {
                    reachedOldData = true;
                    break;
                }

                newInspections.add(dto);
            }

            log.info("Incremental update page was processed. page={}/{}, accumulatedNewRecords={}",
                    currentPage, lastPage, newInspections.size());

            sleepBetweenApiRequests(currentPage, lastPage);
        }

        if (newInspections.isEmpty()) {
            log.info("Incremental update finished. No new inspections found");
            return;
        }

        storageService.saveAllAndRebuildContractorStats(newInspections);

        log.info("Incremental update finished successfully. newRecords={}", newInspections.size());
    }

    private long getPagesCount() {
        String data = inspectionApiClient.fetchJson(1, 1);
        long total = jsonParser.parseTotal(data);
        return (total + perPage - 1) / perPage;
    }

    private void runWithUpdateLock(Runnable updateOperation) {
        if (!updateLock.tryLock()) {
            throw new UpdateAlreadyRunningException("Update is already running. Try again later");
        }

        try {
            status = "running";
            operation = Thread.currentThread().getStackTrace()[2].getMethodName();
            startedAt = LocalDateTime.now();
            finishedAt = null;
            message = "Update is running";
            updateOperation.run();
            status = "completed";
            message = "Update completed successfully";
        } catch (RuntimeException e) {
            status = "failed";
            message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            throw e;
        } finally {
            finishedAt = LocalDateTime.now();
            updateLock.unlock();
        }
    }

    private void validateSettings() {
        if (perPage <= 0) {
            throw new IllegalArgumentException("app.per-page must be greater than 0");
        }

        if (decayTime < 0) {
            throw new IllegalArgumentException("app.decay-time must not be negative");
        }
    }

    private void sleepBetweenApiRequests(long currentPage, long lastPage) {
        if (currentPage >= lastPage) {
            return;
        }

        try {
            Thread.sleep(API_PAGE_DELAY.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new UpdateInterruptedException("Update was interrupted", e);
        }
    }
}
