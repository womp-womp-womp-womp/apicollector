package com.example.db.entities;

import jakarta.persistence.*;

@Entity
@Table(name = "contractors")
public class ContractorEntity {

    @Id
    @Column(name = "contractor_name", nullable = false, columnDefinition = "text")
    private String contractorName;

    @Column(name = "violations_count", nullable = false)
    private int violationsCount;

    @Column(name = "critical_violations_count", nullable = false)
    private int criticalViolationsCount;

    @Column(name = "inspections_count", nullable = false)
    private int inspectionsCount;

    @Column(name = "total_score", nullable = false)
    private int totalScore;

    @Column(name = "rating", nullable = false)
    private double rating;

    protected ContractorEntity() {
    }

    public ContractorEntity(String contractorName, int violationsCount, int criticalViolationsCount) {
        this.contractorName = contractorName;
        this.violationsCount = violationsCount;
        this.criticalViolationsCount = criticalViolationsCount;
        this.inspectionsCount = 0;
        this.totalScore = calculateTotalScore(violationsCount, criticalViolationsCount);
        this.rating = calculateRating(this.totalScore, this.inspectionsCount);
    }

    public String getContractorName() {
        return contractorName;
    }

    public int getViolationsCount() {
        return violationsCount;
    }

    public int getCriticalViolationsCount() {
        return criticalViolationsCount;
    }

    public int getInspectionsCount() {
        return inspectionsCount;
    }

    public int getTotalScore() {
        return totalScore;
    }

    public double getRating() {
        return rating;
    }

    public void addInspection(int violations, int criticalViolations) {
        this.violationsCount += violations;
        this.criticalViolationsCount += criticalViolations;
        this.inspectionsCount++;
        this.totalScore = calculateTotalScore(this.violationsCount, this.criticalViolationsCount);
        this.rating = calculateRating(this.totalScore, this.inspectionsCount);
    }

    private int calculateTotalScore(int violations, int criticalViolations) {
        return violations + criticalViolations * 5;
    }

    private double calculateRating(int totalScore, int inspectionsCount) {
        if (inspectionsCount <= 0) {
            return 0.0;
        }

        return (double) totalScore / inspectionsCount;
    }
}
