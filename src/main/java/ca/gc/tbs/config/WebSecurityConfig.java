package ca.gc.tbs.config;

import jakarta.servlet.http.HttpServletResponse;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.thymeleaf.extras.springsecurity6.dialect.SpringSecurityDialect;

import ca.gc.tbs.security.JWTFilter;

@Configuration
@EnableWebSecurity
public class WebSecurityConfig {

  private final CustomizeAuthenticationSuccessHandler customizeAuthenticationSuccessHandler;

  private final JWTFilter jwtFilter;

  public WebSecurityConfig(
      CustomizeAuthenticationSuccessHandler customizeAuthenticationSuccessHandler,
      JWTFilter jwtFilter) {
    this.customizeAuthenticationSuccessHandler = customizeAuthenticationSuccessHandler;
    this.jwtFilter = jwtFilter;
  }

  @Bean
  public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    http
        .authorizeHttpRequests(auth -> auth
            .requestMatchers("/createApiUser").hasAuthority("ADMIN")
            .requestMatchers("/authenticate").permitAll()
            .requestMatchers("/api/user/**").hasRole("USER")
            .requestMatchers("/", "/checkExists", "/error", "/enableAdmin", "/login", "/signup", "/success").permitAll()
            .requestMatchers("/u/**").hasAnyAuthority("ADMIN")
            .requestMatchers("/keywords/**").hasAnyAuthority("ADMIN")
            .requestMatchers("/python/**", "/reports/**", "/dashboard/**").hasAnyAuthority("USER", "ADMIN")
            .anyRequest().authenticated()
        )
        .formLogin(form -> form
            .loginPage("/login")
            .permitAll()
            .successHandler(customizeAuthenticationSuccessHandler)
            .failureUrl("/login?error=true")
            .usernameParameter("email")
            .passwordParameter("password")
        )
        .logout(logout -> logout
            .logoutRequestMatcher(new AntPathRequestMatcher("/logout"))
            .logoutSuccessUrl("/login?logout=true")
        )
        .exceptionHandling(ex -> ex
            .authenticationEntryPoint(
                (request, response, authException) -> {
                  if ("XMLHttpRequest".equals(request.getHeader("X-Requested-With"))) {
                    response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized");
                  } else {
                    response.sendRedirect("/login");
                  }
                })
        )
        .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

    return http.build();
  }

  @Bean
  public WebSecurityCustomizer webSecurityCustomizer() {
    return web -> web.ignoring()
        .requestMatchers("/resources/**", "/static/**", "/css/**", "/js/**", "/images/**");
  }

  @Bean
  public SpringSecurityDialect springSecurityDialect() {
    return new SpringSecurityDialect();
  }

  @Bean
  public AuthenticationManager authenticationManager(AuthenticationConfiguration config)
      throws Exception {
    return config.getAuthenticationManager();
  }

}
