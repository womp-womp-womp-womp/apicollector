package com.example.db.service;

import com.example.db.entities.ContractorEntity;
import com.example.db.repository.ContractorRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class ContractorStorageService {

    private static final Logger log = LoggerFactory.getLogger(ContractorStorageService.class);

    private final ContractorRepository contractorRepository;

    public ContractorStorageService(ContractorRepository contractorRepository) {
        this.contractorRepository = contractorRepository;
    }

    @Transactional
    public void saveOrUpdateContractor(String contractorName, int violationsCount, int criticalViolationsCount) {
        String normalizedName = normalizeContractorName(contractorName);

        if (normalizedName == null) {
            return;
        }

        ContractorEntity contractor = contractorRepository
                .findById(normalizedName)
                .orElseGet(() -> new ContractorEntity(normalizedName, 0, 0));

        contractor.addInspection(violationsCount, criticalViolationsCount);

        contractorRepository.save(contractor);
    }

    @Transactional
    public void clearAll() {
        contractorRepository.deleteAll();
    }

    @Transactional
    public void saveOrUpdateContractors(List<String> contractors, int violationsCount, int criticalViolationsCount) {
        if (contractors == null || contractors.isEmpty()) {
            return;
        }

        Set<String> uniqueContractors = new LinkedHashSet<>();

        for (String contractorName : contractors) {
            String normalizedName = normalizeContractorName(contractorName);
            if (normalizedName != null) {
                uniqueContractors.add(normalizedName);
            }
        }

        for (String contractorName : uniqueContractors) {
            saveOrUpdateContractor(contractorName, violationsCount, criticalViolationsCount);
        }
    }

    private String normalizeContractorName(String contractorName) {
        if (contractorName == null || contractorName.isBlank()) {
            return null;
        }

        String normalized = contractorName
                .trim()
                .replaceAll("\\s+", " ");

        if (normalized.isBlank()) {
            return null;
        }

        if (!normalized.equals(contractorName)) {
            log.debug("Contractor name was normalized. before='{}', after='{}'", contractorName, normalized);
        }

        return normalized;
    }
}
