/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ca.gc.tbs.config;

import java.io.IOException;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.security.web.savedrequest.RequestCache;
import org.springframework.security.web.savedrequest.SavedRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;

@Component
public class CustomizeAuthenticationSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

  private RequestCache requestCache = new HttpSessionRequestCache();

  @Override
  @GetMapping(value = "/login")
  public void onAuthenticationSuccess(
      HttpServletRequest request, HttpServletResponse response, Authentication authentication)
      throws IOException, ServletException {

    SavedRequest savedRequest = requestCache.getRequest(request, response);

    if (savedRequest == null || savedRequest.getRedirectUrl().contains("signin")) {
      for (GrantedAuthority auth : authentication.getAuthorities()) {
        if ("ADMIN".equals(auth.getAuthority())) {
          response.sendRedirect("/u/index");
        } else {
          response.sendRedirect("/pageFeedback");
        }
      }
      return;
    }
    clearAuthenticationAttributes(request);

    // Use the DefaultSavedRequest URL
    String targetUrl = savedRequest.getRedirectUrl();
    logger.debug("Redirecting to DefaultSavedRequest Url: " + targetUrl);
    getRedirectStrategy().sendRedirect(request, response, targetUrl);
  }

  public void setRequestCache(RequestCache requestCache) {
    this.requestCache = requestCache;
  }
}
