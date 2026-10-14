package cl.duoc.barriodigital.catalog.web.dto;

/**
 * Alta de un tipo. {@code active} se omite como true.
 * El cupo no se manda en el POST: nace en null, sin límite.
 */
public record CreateProcedureRequest(String value, String label, Boolean active) {
}
