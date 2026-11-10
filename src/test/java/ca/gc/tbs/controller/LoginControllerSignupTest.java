package ca.gc.tbs.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import ca.gc.tbs.domain.User;
import ca.gc.tbs.service.UserService;
import java.lang.reflect.Field;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class LoginControllerSignupTest {

  private UserService userService;
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() throws Exception {
    userService = Mockito.mock(UserService.class);
    LoginController controller = new LoginController();
    Field field = LoginController.class.getDeclaredField("userService");
    field.setAccessible(true);
    field.set(controller, userService);
    mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
  }

  @Test
  void signupIgnoresFieldsTheFormDoesNotOffer() throws Exception {
    mockMvc
        .perform(
            MockMvcRequestBuilders.post("/signup")
                .param("email", "attacker@example.com")
                .param("password", "pw")
                .param("institution", "TBS")
                .param("enabled", "true")
                .param("id", "existing-admin-id")
                .param("dateCreated", "1999-01-01"))
        .andExpect(MockMvcResultMatchers.status().is3xxRedirection());

    User bound = captureSavedUser();
    assertThat(bound.getEmail()).isEqualTo("attacker@example.com");
    assertThat(bound.getPassword()).isEqualTo("pw");
    assertThat(bound.getInstitution()).isEqualTo("TBS");
    assertThat(bound.isEnabled()).isFalse();
    assertThat(bound.getId()).isNull();
    assertThat(bound.getDateCreated()).isNull();
  }

  @Test
  void checkExistsEndpointIsGone() throws Exception {
    mockMvc
        .perform(MockMvcRequestBuilders.get("/checkExists").param("email", "someone@example.com"))
        .andExpect(MockMvcResultMatchers.status().isNotFound());

    Mockito.verifyNoInteractions(userService);
  }

  private User captureSavedUser() {
    ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
    verify(userService).saveUser(captor.capture());
    return captor.getValue();
  }
}
