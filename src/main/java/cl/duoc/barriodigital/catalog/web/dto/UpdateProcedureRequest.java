package cl.duoc.barriodigital.catalog.web.dto;

/**
 * Cuerpo para editar nombre, estado activo, cupo diario y plazo.
 * El código va en la URL y no se cambia; dailyQuota o slaBusinessDays null quitan el límite o el plazo.
 * Si value viene y no coincide con la URL, se rechaza.
 */
public record UpdateProcedureRequest(
        String label,
        Boolean active,
        Integer dailyQuota,
        Integer slaBusinessDays,
        String value
) {
}
