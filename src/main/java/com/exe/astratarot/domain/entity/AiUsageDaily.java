package com.exe.astratarot.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "ai_usage_daily")
@IdClass(AiUsageDailyId.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiUsageDaily {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Id
    @Column(name = "usage_date")
    private LocalDate usageDate;

    @Column(name = "count_used", nullable = false)
    @Builder.Default
    private Integer countUsed = 0;

    @PrePersist
    protected void onCreate() {
        if (this.countUsed == null) {
            this.countUsed = 0;
        }
    }

    public boolean canUse() {
        return this.countUsed != null && this.countUsed >= 0;
    }

    public void increment() {
        if (this.countUsed == null) {
            this.countUsed = 1;
        } else {
            this.countUsed++;
        }
    }

    public void resetDaily() {
        this.countUsed = 0;
    }
}
