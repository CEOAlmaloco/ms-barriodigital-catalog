package cl.duoc.barriodigital.catalog.web;

/**
 * El BFF valida el JWT y reenvía el claim {@code roles} en este header.
 * Catalog no lee el token.
 */
public final class AdminCaller {

    public static final String HEADER_USER_ROLES = "X-User-Roles";

    private AdminCaller() {
    }

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
