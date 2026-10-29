package ca.gc.tbs.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import ca.gc.tbs.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

@SpringBootTest(
    classes = {UserController.class, UserControllerSecurityTest.TestSecurityConfig.class})
@AutoConfigureMockMvc
class UserControllerSecurityTest {

  @Autowired private MockMvc mockMvc;

  @MockBean private UserService userService;

  @Test
  void anonymousUsersAreRedirectedFromEnableAdmin() throws Exception {
    mockMvc
        .perform(MockMvcRequestBuilders.get("/enableAdmin").param("email", "admin@example.com"))
        .andExpect(MockMvcResultMatchers.status().is3xxRedirection())
        .andExpect(MockMvcResultMatchers.redirectedUrl("/login"));

    verifyNoInteractions(userService);
  }

  @Test
  @org.springframework.security.test.context.support.WithMockUser(authorities = "USER")
  void nonAdminUsersAreForbiddenFromEnableAdmin() throws Exception {
    mockMvc
        .perform(MockMvcRequestBuilders.get("/enableAdmin").param("email", "admin@example.com"))
        .andExpect(MockMvcResultMatchers.status().isForbidden());

    verifyNoInteractions(userService);
  }

  @Test
  @org.springframework.security.test.context.support.WithMockUser(authorities = "ADMIN")
  void adminsCanPromoteUsers() throws Exception {
    mockMvc
        .perform(MockMvcRequestBuilders.get("/enableAdmin").param("email", "admin@example.com"))
        .andExpect(MockMvcResultMatchers.status().is3xxRedirection())
        .andExpect(MockMvcResultMatchers.redirectedUrl("/success"));

    verify(userService).enableAdmin("admin@example.com");
  }

  @Configuration
  @EnableWebSecurity
  @EnableMethodSecurity
  static class TestSecurityConfig {

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
      http.csrf(csrf -> csrf.disable())
          .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
          .exceptionHandling(
              ex ->
                  ex.authenticationEntryPoint(
                      (request, response, authException) -> response.sendRedirect("/login")));

      return http.build();
    }
  }
}
