package cl.duoc.barriodigital.catalog.web.dto;

/**
 * Cuerpo para editar nombre, estado activo y cupo diario.
 * El código va en la URL y no se cambia; dailyQuota null significa sin límite.
 * Si value viene y no coincide con la URL, se rechaza.
 */
public record UpdateProcedureRequest(String label, Boolean active, Integer dailyQuota, String value) {
}
