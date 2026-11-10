package cl.duoc.barriodigital.catalog.web.dto;

/**
 * Cuerpo para crear un tipo: código y nombre.
 * Si active no viene, el servicio lo trata como activo. El cupo queda en null, sin límite.
 * slaBusinessDays es opcional: null u omitido deja el tipo sin plazo.
 */
public record CreateProcedureRequest(String value, String label, Boolean active, Integer slaBusinessDays) {
}
