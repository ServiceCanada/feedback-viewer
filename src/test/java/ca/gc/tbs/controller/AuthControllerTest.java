package ca.gc.tbs.controller;

import ca.gc.tbs.domain.Role;
import ca.gc.tbs.security.JWTUtil;
import ca.gc.tbs.service.UserService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

@SpringBootTest(classes = {AuthController.class, AuthControllerTest.TestSecurityConfig.class})
@AutoConfigureMockMvc
class AuthControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockBean private AuthenticationManager authenticationManager;
  @MockBean private JWTUtil jwtUtil;
  @MockBean private UserService userService;

  @Test
  @WithMockUser(authorities = "ADMIN")
  void createApiUserRejectsGet() throws Exception {
    mockMvc
        .perform(
            MockMvcRequestBuilders.get("/createApiUser")
                .param("username", "test@example.com")
                .param("password", "secret"))
        .andExpect(MockMvcResultMatchers.status().isMethodNotAllowed());
  }

  @Test
  @WithMockUser(authorities = "ADMIN")
  void createApiUserAcceptsPost() throws Exception {
    Role apiRole = new Role();
    apiRole.setRole("API");

    Mockito.when(userService.findUserByEmail("test@example.com")).thenReturn(null);
    Mockito.when(userService.findRoleByName("API")).thenReturn(apiRole);

    mockMvc
        .perform(
            MockMvcRequestBuilders.post("/createApiUser")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"test@example.com\",\"password\":\"secret\"}"))
        .andExpect(result ->
            org.assertj.core.api.Assertions.assertThat(result.getResponse().getStatus())
                .isNotEqualTo(405));
  }

  @Configuration
  @EnableWebSecurity
  static class TestSecurityConfig {

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
      http
          .csrf(csrf -> csrf.disable())
          .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
          .exceptionHandling(ex -> ex.authenticationEntryPoint(
              (request, response, authException) -> response.sendError(401)));
      return http.build();
    }
  }
}
