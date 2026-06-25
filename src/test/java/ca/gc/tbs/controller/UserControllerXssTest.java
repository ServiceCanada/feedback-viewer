package ca.gc.tbs.controller;

import ca.gc.tbs.domain.Role;
import ca.gc.tbs.domain.User;
import ca.gc.tbs.service.UserService;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;

class UserControllerXssTest {

  @Test
  void emailIsHtmlEscapedInGetData() throws Exception {
    UserService userService = Mockito.mock(UserService.class);
    UserController controller = new UserController();
    injectField(controller, "service", userService);

    User user = new User();
    user.setId("507f1f77bcf86cd799439011");
    user.setEmail("<script>alert(1)</script>");
    user.setInstitution("Safe Inst");
    user.setDateCreated("2024-01-01");
    user.setEnabled(true);

    Role role = new Role();
    role.setRole("USER");
    user.setRoles(Set.of(role));

    Mockito.when(userService.findAllUsers()).thenReturn(List.of(user));

    String output = controller.getData("en");

    assertThat(output).doesNotContain("<script>");
    assertThat(output).contains("&lt;script&gt;");
  }

  @Test
  void institutionIsHtmlEscapedInGetData() throws Exception {
    UserService userService = Mockito.mock(UserService.class);
    UserController controller = new UserController();
    injectField(controller, "service", userService);

    User user = new User();
    user.setId("507f1f77bcf86cd799439011");
    user.setEmail("safe@example.com");
    user.setInstitution("<img src=x onerror=alert(2)>");
    user.setDateCreated("2024-01-01");
    user.setEnabled(false);

    Role role = new Role();
    role.setRole("USER");
    user.setRoles(Set.of(role));

    Mockito.when(userService.findAllUsers()).thenReturn(List.of(user));

    String output = controller.getData("fr");

    assertThat(output).doesNotContain("<img");
    assertThat(output).contains("&lt;img");
  }

  /** Reflective field injection to avoid changing field visibility. */
  private static void injectField(Object target, String fieldName, Object value) throws Exception {
    var field = target.getClass().getDeclaredField(fieldName);
    field.setAccessible(true);
    field.set(target, value);
  }
}
