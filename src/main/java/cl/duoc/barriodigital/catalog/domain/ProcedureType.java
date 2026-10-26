package cl.duoc.barriodigital.catalog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDate;

/**
 * Fila de un tipo de trámite. El código no se cambia porque requests ya lo usa.
 * Si hay cupo, se repone cada día en hora de Chile y baja en reserve al admitir; dailyQuota null significa sin límite.
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

    /** Lo usa JPA para instanciar la entidad sin argumentos. */
    protected ProcedureType() {
        // JPA
    }

    /** Crea un tipo activo. dailyQuota null lo deja sin límite. */
    public ProcedureType(String value, String label, Integer dailyQuota, LocalDate quotaResetDate) {
        this(value, label, true, dailyQuota, quotaResetDate);
    }

    /**
     * Crea un tipo y copia el cupo del día en el restante.
     * Con dailyQuota null, remainingQuota también queda null porque no hay límite que contar.
     */
    public ProcedureType(String value, String label, boolean active, Integer dailyQuota, LocalDate quotaResetDate) {
        this.value = value;
        this.label = label;
        this.active = active;
        this.dailyQuota = dailyQuota;
        this.remainingQuota = dailyQuota;
        this.quotaResetDate = quotaResetDate;
    }

    /** Devuelve el código estable, por ejemplo bache. No se renombra. */
    public String getValue() {
        return value;
    }

    /** Devuelve el nombre que ve quien consulta el catálogo. */
    public String getLabel() {
        return label;
    }

    /** Indica si el tipo se puede usar. */
    public boolean isActive() {
        return active;
    }

    /** Cupo máximo del día. Null significa sin límite. */
    public Integer getDailyQuota() {
        return dailyQuota;
    }

    /** Cupos que quedan hoy. Null cuando el tipo no tiene límite. */
    public Integer getRemainingQuota() {
        return remainingQuota;
    }

    /** Cambia solo el texto visible. No toca el cupo, para corregir una etiqueta sin alterar el conteo. */
    public void replaceLabel(String label) {
        this.label = label;
    }

    /** Día de Chile con el que se calculó el cupo guardado. Sirve para saber si hay que reponer. */
    public LocalDate getQuotaResetDate() {
        return quotaResetDate;
    }

    /**
     * Cambia nombre, estado activo y cupo; el código no se toca.
     * dailyQuota null deja el tipo sin límite. Si ya se usaron cupos hoy, esos usos se conservan.
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
     * Si today es otro día, repone el cupo al máximo. El mismo día no toca lo ya usado.
     * Devuelve true cuando cambió la fecha, para persistir la fila. today debe ser hora de Chile.
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

    /**
     * Descuenta un cupo al admitir. Con dailyQuota null no resta y responde éxito, porque no hay límite.
     * Si no queda cupo, no cambia el contador y devuelve false.
     */
    public boolean reserveOne() {
        if (dailyQuota == null) {
            return true;
        }
        if (remainingQuota == null || remainingQuota <= 0) {
            return false;
        }
        remainingQuota--;
        return true;
    }

    /** Devuelve un cupo sin pasar del máximo del día. Sin límite no hace nada. */
    public void releaseOne() {
        if (dailyQuota == null) {
            return;
        }
        int available = remainingQuota == null ? 0 : remainingQuota;
        remainingQuota = Math.min(dailyQuota, available + 1);
    }

    /** Fija el restante como si ya se hubieran usado varios cupos. Lo usan las pruebas del dominio. */
    void consume(int used) {
        this.remainingQuota = Math.max(0, this.dailyQuota - used);
    }
}
