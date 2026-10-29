package ca.gc.tbs.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.web.savedrequest.RequestCache;
import org.springframework.security.web.savedrequest.SavedRequest;

class CustomizeAuthenticationSuccessHandlerTest {

  @Test
  void externalSavedUrlIsNotFollowed() throws Exception {
    CustomizeAuthenticationSuccessHandler handler = new CustomizeAuthenticationSuccessHandler();

    // Saved request points to an external host
    SavedRequest savedRequest = mock(SavedRequest.class);
    when(savedRequest.getRedirectUrl()).thenReturn("http://evil.com/harvest");

    RequestCache requestCache = mock(RequestCache.class);
    HttpServletRequest request = mock(HttpServletRequest.class);
    HttpServletResponse response = mock(HttpServletResponse.class);
    when(requestCache.getRequest(request, response)).thenReturn(savedRequest);
    when(request.getServerName()).thenReturn("myapp.canada.ca");
    Authentication auth = mockAdminAuthentication();

    handler.setRequestCache(requestCache);
    handler.onAuthenticationSuccess(request, response, auth);

    // Must redirect to the role-based default, not evil.com
    ArgumentCaptor<String> redirectCaptor = ArgumentCaptor.forClass(String.class);
    verify(response).sendRedirect(redirectCaptor.capture());
    assertThat(redirectCaptor.getValue()).isEqualTo("/u/index");
    assertThat(redirectCaptor.getValue()).doesNotContain("evil.com");
  }

  @Test
  void sameOriginSavedUrlIsFollowed() throws Exception {
    CustomizeAuthenticationSuccessHandler handler = new CustomizeAuthenticationSuccessHandler();

    // Saved request is a relative URL — always same-origin
    SavedRequest savedRequest = mock(SavedRequest.class);
    when(savedRequest.getRedirectUrl()).thenReturn("/dashboard");

    RequestCache requestCache = mock(RequestCache.class);
    HttpServletRequest request = mock(HttpServletRequest.class);
    HttpServletResponse response = mock(HttpServletResponse.class);
    when(requestCache.getRequest(request, response)).thenReturn(savedRequest);
    when(request.getServerName()).thenReturn("myapp.canada.ca");
    when(request.getContextPath()).thenReturn("");
    when(response.encodeRedirectURL(any())).thenAnswer(inv -> inv.getArgument(0));

    // Use a non-admin user so the role-based branch would go to /pageFeedback;
    // if the handler correctly follows the saved URL instead, we see /dashboard.
    Authentication auth = mockUserAuthentication();

    handler.setRequestCache(requestCache);
    handler.onAuthenticationSuccess(request, response, auth);

    ArgumentCaptor<String> redirectCaptor = ArgumentCaptor.forClass(String.class);
    // sendRedirect is called via DefaultRedirectStrategy — capture via response mock
    verify(response, atLeastOnce()).sendRedirect(redirectCaptor.capture());
    assertThat(redirectCaptor.getAllValues()).contains("/dashboard");
  }

  private static Authentication mockAdminAuthentication() {
    Authentication auth = mock(Authentication.class);
    GrantedAuthority adminAuthority = () -> "ADMIN";
    doReturn(List.of(adminAuthority)).when(auth).getAuthorities();
    return auth;
  }

  private static Authentication mockUserAuthentication() {
    Authentication auth = mock(Authentication.class);
    GrantedAuthority userAuthority = () -> "USER";
    doReturn(List.of(userAuthority)).when(auth).getAuthorities();
    return auth;
  }
}
