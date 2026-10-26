package cl.duoc.barriodigital.catalog.web;

/**
 * Lee el encabezado de roles que envía el BFF.
 * El catálogo no ve el JWT: el BFF ya lo validó y reenvía los roles.
 */
public final class AdminCaller {

    public static final String HEADER_USER_ROLES = "X-User-Roles";

    /** Impide instanciar la clase: solo se usa isAdmin. */
    private AdminCaller() {
    }

    /** Indica si el encabezado incluye el rol Admin. */
    public static boolean isAdmin(String rolesHeader) {
        if (rolesHeader == null || rolesHeader.isBlank()) {
            return false;
        }
        String[] parts = rolesHeader.split(",");
        for (String part : parts) {
            String role = part.trim();
            if (role.equalsIgnoreCase("Admin") || role.equalsIgnoreCase("ROLE_Admin")) {
                return true;
            }
        }
        return false;
    }
}
