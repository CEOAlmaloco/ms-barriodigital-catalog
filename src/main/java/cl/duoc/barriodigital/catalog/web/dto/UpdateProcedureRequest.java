package cl.duoc.barriodigital.catalog.web.dto;

/**
 * Reemplazo de lo editable. El código va en la URL y no se cambia.
 * {@code value} en el cuerpo, si viene y no coincide con la URL, se rechaza.
 */
public record UpdateProcedureRequest(String label, Boolean active, Integer dailyQuota, String value) {
}
