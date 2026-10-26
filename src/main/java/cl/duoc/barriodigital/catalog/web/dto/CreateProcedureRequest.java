package cl.duoc.barriodigital.catalog.web.dto;

/**
 * Cuerpo para crear un tipo: código y nombre.
 * Si active no viene, el servicio lo trata como activo. El cupo no se envía: queda en null, sin límite.
 */
public record CreateProcedureRequest(String value, String label, Boolean active) {
}
