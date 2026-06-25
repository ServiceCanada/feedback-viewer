package ca.gc.tbs.filter;

import ca.gc.tbs.service.GcIpValidationService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.PrintWriter;
import java.io.StringWriter;

import static org.mockito.Mockito.*;

class GcIpFilterTest {

    private GcIpFilter filter;
    private GcIpValidationService gcIpValidationService;
    private HttpServletRequest request;
    private HttpServletResponse response;
    private FilterChain chain;

    private static final String WHITELISTED_IP = "205.193.96.10";
    private static final String NON_GC_IP = "1.2.3.4";

    @BeforeEach
    void setUp() throws Exception {
        gcIpValidationService = mock(GcIpValidationService.class);
        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);
        chain = mock(FilterChain.class);

        filter = new GcIpFilter();
        ReflectionTestUtils.setField(filter, "gcIpValidationService", gcIpValidationService);
        ReflectionTestUtils.setField(filter, "filterEnabled", true);
        ReflectionTestUtils.setField(filter, "whitelistIps", WHITELISTED_IP);
        ReflectionTestUtils.setField(filter, "whitelistFilePath", "");
        ReflectionTestUtils.setField(filter, "fileWhitelistIps", new java.util.HashSet<>());

        when(request.getRequestURI()).thenReturn("/some/path");
        when(gcIpValidationService.isGcIp(anyString())).thenReturn(false);

        PrintWriter writer = new PrintWriter(new StringWriter());
        when(response.getWriter()).thenReturn(writer);
    }

    // --- X_REAL_IP strategy ---

    @Test
    void xRealIp_spoofedForwardedForIsIgnored() throws Exception {
        ReflectionTestUtils.setField(filter, "clientIpSource", "X_REAL_IP");

        // Attacker sends a whitelisted GC IP in X-Forwarded-For
        when(request.getHeader("X-Forwarded-For")).thenReturn(WHITELISTED_IP);
        // X-Real-IP is not present (nginx didn't set it, so request is direct/untrusted)
        when(request.getHeader("X-Real-IP")).thenReturn(null);
        when(request.getRemoteAddr()).thenReturn(NON_GC_IP);

        filter.doFilter(request, response, chain);

        verify(response).setStatus(HttpServletResponse.SC_FORBIDDEN);
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    void xRealIp_trustedHeaderFromNginxIsAccepted() throws Exception {
        ReflectionTestUtils.setField(filter, "clientIpSource", "X_REAL_IP");

        when(request.getHeader("X-Real-IP")).thenReturn(WHITELISTED_IP);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verify(response, never()).setStatus(HttpServletResponse.SC_FORBIDDEN);
    }

    // --- X_FORWARDED_FOR_LAST strategy ---

    @Test
    void xForwardedForLast_attackerPrefixedGcIpIsIgnored() throws Exception {
        ReflectionTestUtils.setField(filter, "clientIpSource", "X_FORWARDED_FOR_LAST");

        // Attacker prepends a whitelisted GC IP; ALB appends the real (non-GC) client IP at the end
        when(request.getHeader("X-Forwarded-For")).thenReturn(WHITELISTED_IP + ", " + NON_GC_IP);
        when(request.getRemoteAddr()).thenReturn("10.0.0.1"); // ALB private IP

        filter.doFilter(request, response, chain);

        verify(response).setStatus(HttpServletResponse.SC_FORBIDDEN);
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    void xForwardedForLast_realGcIpAppendedByAlbIsAccepted() throws Exception {
        ReflectionTestUtils.setField(filter, "clientIpSource", "X_FORWARDED_FOR_LAST");

        // ALB appends the real client IP (a whitelisted GC IP) as the last entry
        when(request.getHeader("X-Forwarded-For")).thenReturn("198.51.100.1, " + WHITELISTED_IP);
        when(request.getRemoteAddr()).thenReturn("10.0.0.1");

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verify(response, never()).setStatus(HttpServletResponse.SC_FORBIDDEN);
    }

    // --- REMOTE_ADDR strategy ---

    @Test
    void remoteAddr_nonGcAddressIsBlocked() throws Exception {
        ReflectionTestUtils.setField(filter, "clientIpSource", "REMOTE_ADDR");

        // Even if headers claim a GC IP, we only trust remoteAddr
        when(request.getHeader("X-Forwarded-For")).thenReturn(WHITELISTED_IP);
        when(request.getHeader("X-Real-IP")).thenReturn(WHITELISTED_IP);
        when(request.getRemoteAddr()).thenReturn(NON_GC_IP);

        filter.doFilter(request, response, chain);

        verify(response).setStatus(HttpServletResponse.SC_FORBIDDEN);
        verify(chain, never()).doFilter(any(), any());
    }
}
