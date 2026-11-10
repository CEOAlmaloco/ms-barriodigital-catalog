package cl.duoc.barriodigital.catalog.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import cl.duoc.barriodigital.catalog.messaging.AuditActor;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;

class ActorContextFilterTest {

    @Test
    void decodificaNombreYTomaSourceIp() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader(ActorContextFilter.HEADER_USER_ID)).thenReturn("user-1");
        when(request.getHeader(ActorContextFilter.HEADER_USER_NAME)).thenReturn("Admin%20Test");
        when(request.getHeader(AdminCaller.HEADER_USER_ROLES)).thenReturn("Admin");
        when(request.getHeader(ActorContextFilter.HEADER_SOURCE_IP)).thenReturn("203.0.113.25");

        AuditActor actor = ActorContextFilter.from(request);

        assertThat(actor.id()).isEqualTo("user-1");
        assertThat(actor.name()).isEqualTo("Admin Test");
        assertThat(actor.role()).isEqualTo("Admin");
        assertThat(actor.sourceIp()).isEqualTo("203.0.113.25");
    }

    @Test
    void sinNombreUsaElIdYSinIpQuedaNull() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader(ActorContextFilter.HEADER_USER_ID)).thenReturn("user-2");
        when(request.getHeader(ActorContextFilter.HEADER_USER_NAME)).thenReturn(null);
        when(request.getHeader(AdminCaller.HEADER_USER_ROLES)).thenReturn("ROLE_Admin");
        when(request.getHeader(ActorContextFilter.HEADER_SOURCE_IP)).thenReturn("  ");

        AuditActor actor = ActorContextFilter.from(request);

        assertThat(actor.name()).isEqualTo("user-2");
        assertThat(actor.role()).isEqualTo("Admin");
        assertThat(actor.sourceIp()).isNull();
    }
}
