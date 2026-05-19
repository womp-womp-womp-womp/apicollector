package com.example.apicollector;

import com.example.db.entities.ContractorEntity;
import com.example.db.repository.ContractorRepository;
import com.example.db.service.ContractorStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ContractorStorageServiceTest {

    private ContractorRepository contractorRepository;
    private ContractorStorageService service;

    @BeforeEach
    void setUp() {
        contractorRepository = mock(ContractorRepository.class);
        service = new ContractorStorageService(contractorRepository);
    }

    @Test
    void saveOrUpdateContractorNormalizesNameAndAccumulatesStats() {
        ContractorEntity existing = new ContractorEntity("Alpha", 0, 0);
        existing.addInspection(2, 1);
        when(contractorRepository.findById("Alpha")).thenReturn(Optional.of(existing));

        service.saveOrUpdateContractor(" Alpha  ", 3, 0);

        ArgumentCaptor<ContractorEntity> captor = ArgumentCaptor.forClass(ContractorEntity.class);
        verify(contractorRepository).save(captor.capture());
        assertThat(captor.getValue().getContractorName()).isEqualTo("Alpha");
        assertThat(captor.getValue().getViolationsCount()).isEqualTo(5);
        assertThat(captor.getValue().getCriticalViolationsCount()).isEqualTo(1);
        assertThat(captor.getValue().getInspectionsCount()).isEqualTo(2);
    }

    @Test
    void saveOrUpdateContractorsSkipsBlankNamesAndDeduplicatesNormalizedNames() {
        when(contractorRepository.findById("Alpha")).thenReturn(Optional.empty());
        when(contractorRepository.findById("Beta")).thenReturn(Optional.empty());

        service.saveOrUpdateContractors(List.of(" Alpha ", "Alpha", " ", "Beta"), 1, 2);

        verify(contractorRepository).findById("Alpha");
        verify(contractorRepository).findById("Beta");
    }

    @Test
    void nullAndBlankInputsDoNotWrite() {
        service.saveOrUpdateContractor(null, 1, 1);
        service.saveOrUpdateContractors(null, 1, 1);
        service.saveOrUpdateContractors(List.of(), 1, 1);

        verify(contractorRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void clearAllDeletesAllContractors() {
        service.clearAll();

        verify(contractorRepository).deleteAll();
    }
}
