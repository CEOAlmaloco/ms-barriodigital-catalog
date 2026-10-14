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

    @Column(name = "daily_quota")
    private Integer dailyQuota;

    @Column(name = "remaining_quota")
    private Integer remainingQuota;

    @Column(name = "quota_reset_date", nullable = false)
    private LocalDate quotaResetDate;

    protected ProcedureType() {
        // JPA
    }

    public ProcedureType(String value, String label, Integer dailyQuota, LocalDate quotaResetDate) {
        this(value, label, true, dailyQuota, quotaResetDate);
    }

    public ProcedureType(String value, String label, boolean active, Integer dailyQuota, LocalDate quotaResetDate) {
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

    public Integer getDailyQuota() {
        return dailyQuota;
    }

    public Integer getRemainingQuota() {
        return remainingQuota;
    }

    /** Corrige el texto visible sin tocar el cupo. El seed lo usa para la tilde de alumbrado. */
    public void replaceLabel(String label) {
        this.label = label;
    }

    public LocalDate getQuotaResetDate() {
        return quotaResetDate;
    }

    /**
     * Cambia lo editable. El value no se toca.
     * dailyQuota null es sin límite: remainingQuota también queda null.
     * Si había límite, lo ya usado hoy se conserva y, si el cupo nuevo no alcanza, queda en 0.
     */
    public void updateDetails(String label, boolean active, Integer dailyQuota) {
        this.label = label;
        this.active = active;
        if (dailyQuota == null) {
            this.dailyQuota = null;
            this.remainingQuota = null;
            return;
        }
        if (this.dailyQuota == null || this.remainingQuota == null) {
            this.dailyQuota = dailyQuota;
            this.remainingQuota = dailyQuota;
            return;
        }
        int used = Math.max(0, this.dailyQuota - this.remainingQuota);
        this.dailyQuota = dailyQuota;
        this.remainingQuota = Math.max(0, dailyQuota - used);
    }

    /**
     * Primer uso de un día nuevo: compara la fecha guardada con hoy y, si cambió,
     * repone remainingQuota a dailyQuota. El mismo día no toca lo ya usado.
     * No consulta a otros servicios.
     *
     * @return true si la fecha cambió y hay que persistir la fila
     */
    public boolean resetForToday(LocalDate today) {
        if (today.equals(quotaResetDate)) {
            return false;
        }
        if (this.dailyQuota != null) {
            this.remainingQuota = this.dailyQuota;
        }
        this.quotaResetDate = today;
        return true;
    }

    void consume(int used) {
        this.remainingQuota = Math.max(0, this.dailyQuota - used);
    }
}
