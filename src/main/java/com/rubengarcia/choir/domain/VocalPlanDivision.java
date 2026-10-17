package com.rubengarcia.choir.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

@Table("vocal_plan_divisions")
public class VocalPlanDivision {

    @Id
    private Long id;

    @Column("vocal_plan_id")
    private Long vocalPlanId;

    @Column("choir_id")
    private Long choirId;

    @Column("vocal_section_id")
    private Long vocalSectionId;

    @Column("division_number")
    private int divisionNumber;

    @Column("created_at")
    private LocalDateTime createdAt;

    protected VocalPlanDivision() {
    }

    public Long getId() {
        return id;
    }

    public Long getVocalPlanId() {
        return vocalPlanId;
    }

    public void setVocalPlanId(Long vocalPlanId) {
        this.vocalPlanId = vocalPlanId;
    }

    public Long getChoirId() {
        return choirId;
    }

    public void setChoirId(Long choirId) {
        this.choirId = choirId;
    }

    public Long getVocalSectionId() {
        return vocalSectionId;
    }

    public void setVocalSectionId(Long vocalSectionId) {
        this.vocalSectionId = vocalSectionId;
    }

    public int getDivisionNumber() {
        return divisionNumber;
    }

    public void setDivisionNumber(int divisionNumber) {
        this.divisionNumber = divisionNumber;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
