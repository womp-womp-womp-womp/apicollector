package com.example.db.entities;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "inspections")
public class InspectionEntity {

    @Id
    @Column(name = "public_id", nullable = false)
    private Long publicId;

    @Column(name = "api_id")
    private Long id;

    @Column(name = "inspection_date")
    private LocalDateTime date;

    @Column(name = "violations_count", nullable = false)
    private int violationsCount;

    @Column(name = "critical_violations_count", nullable = false)
    private int criticalViolationsCount;

    @Column(name = "location_name", columnDefinition = "text")
    private String locationName;

    @Column(name = "location_address", columnDefinition = "text")
    private String locationAddress;


    @Column(name = "contractors_text", columnDefinition = "text")
    private String contractorsText;

    protected InspectionEntity() {
    }

    public InspectionEntity(
            Long publicId,
            Long id,
            LocalDateTime date,
            int violationsCount,
            int criticalViolationsCount,
            String locationName,
            String locationAddress,
            String contractorsText
    ) {
        this.publicId = publicId;
        this.id = id;
        this.date = date;
        this.violationsCount = violationsCount;
        this.criticalViolationsCount = criticalViolationsCount;
        this.locationName = locationName;
        this.locationAddress = locationAddress;
        this.contractorsText = contractorsText;
    }

    public Long getId() {
        return id;
    }

    public LocalDateTime getDate() {
        return date;
    }

    public int getViolationsCount() {
        return violationsCount;
    }

    public int getCriticalViolationsCount() {
        return criticalViolationsCount;
    }

    public String getLocationName() {
        return locationName;
    }

    public String getLocationAddress() {
        return locationAddress;
    }

    public String getContractorsText() {
        return contractorsText;
    }

    public Long getPublicId() {
        return publicId;
    }
}
