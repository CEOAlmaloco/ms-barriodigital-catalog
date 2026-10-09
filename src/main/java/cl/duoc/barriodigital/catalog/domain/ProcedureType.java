package cl.duoc.barriodigital.catalog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDate;

/**
 * Tipo de trámite. El value es el código que ya usa requests (bache, alumbrado, …) y no se cambia.
 * dailyQuota, remainingQuota y quotaResetDate nacen en la misma tabla para no migrar en el cupo diario.
 */
@Entity
@Table(name = "procedure_types")
public class ProcedureType {

    @Id
    @Column(length = 40, nullable = false)
    private String value;

    @Column(nullable = false, length = 120)
    private String label;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "daily_quota", nullable = false)
    private int dailyQuota;

    @Column(name = "remaining_quota", nullable = false)
    private int remainingQuota;

    @Column(name = "quota_reset_date", nullable = false)
    private LocalDate quotaResetDate;

    protected ProcedureType() {
        // JPA
    }

    public ProcedureType(String value, String label, int dailyQuota, LocalDate quotaResetDate) {
        this(value, label, true, dailyQuota, quotaResetDate);
    }

    public ProcedureType(String value, String label, boolean active, int dailyQuota, LocalDate quotaResetDate) {
        this.value = value;
        this.label = label;
        this.active = active;
        this.dailyQuota = dailyQuota;
        this.remainingQuota = dailyQuota;
        this.quotaResetDate = quotaResetDate;
    }

    public String getValue() {
        return value;
    }

    public String getLabel() {
        return label;
    }

    public boolean isActive() {
        return active;
    }

    public int getDailyQuota() {
        return dailyQuota;
    }

    public int getRemainingQuota() {
        return remainingQuota;
    }

    public LocalDate getQuotaResetDate() {
        return quotaResetDate;
    }
}
