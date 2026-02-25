package ca.gc.tbs.security;

import ca.gc.tbs.service.UserService;
import java.io.IOException;
import java.util.List;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class JWTFilter extends OncePerRequestFilter {

  @Autowired private JWTUtil jwtUtil;

  @Autowired private UserService userService;

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    String authorizationHeader = request.getHeader("Authorization");

    String token = null;
    String userName = null;

    if (authorizationHeader != null && authorizationHeader.startsWith("Bearer ")) {
      token = authorizationHeader.substring(7);
      try {
        userName = jwtUtil.extractUsername(token);
      } catch (RuntimeException ex) {
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        response.getWriter().write(ex.getMessage());
        return;
      }
    }

    if (userName != null && SecurityContextHolder.getContext().getAuthentication() == null) {
      UserDetails userDetails = userService.loadUserByUsername(userName);
      if (jwtUtil.validateToken(token, userDetails)) {
        List<String> authorities =
            ((List<?>) jwtUtil.extractClaim(token, claims -> claims.get("authorities")))
                .stream().map(Object::toString).toList();
        if (authorities.contains("ADMIN") || authorities.contains("API")) {
          UsernamePasswordAuthenticationToken usernamePasswordAuthenticationToken =
              new UsernamePasswordAuthenticationToken(
                  userDetails, null, userDetails.getAuthorities());
          usernamePasswordAuthenticationToken.setDetails(
              new WebAuthenticationDetailsSource().buildDetails(request));
          SecurityContextHolder.getContext().setAuthentication(usernamePasswordAuthenticationToken);
        } else {
          response.setStatus(HttpServletResponse.SC_FORBIDDEN);
          response
              .getWriter()
              .write("Access denied. Only API users & Admins can access this endpoint.");
          return;
        }
      }
    }

    filterChain.doFilter(request, response);
  }
}
