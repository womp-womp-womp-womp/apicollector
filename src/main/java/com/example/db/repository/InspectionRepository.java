package com.example.db.repository;


import com.example.db.entities.InspectionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface InspectionRepository extends JpaRepository<InspectionEntity, Long> {
    Optional<InspectionEntity> findFirstByOrderByDateDesc();
}