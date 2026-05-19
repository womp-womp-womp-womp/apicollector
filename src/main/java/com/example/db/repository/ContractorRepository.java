package com.example.db.repository;


import com.example.db.entities.ContractorEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ContractorRepository extends JpaRepository<ContractorEntity, String> {

    List<ContractorEntity> findAllByInspectionsCountGreaterThanEqualOrderByRatingAscInspectionsCountDescTotalScoreAscViolationsCountAscCriticalViolationsCountAsc(
            int inspectionsCount,
            Pageable pageable
    );
}
