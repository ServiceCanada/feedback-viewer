package ca.gc.tbs.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ca.gc.tbs.domain.Role;
import ca.gc.tbs.domain.User;
import ca.gc.tbs.repository.RoleRepository;
import ca.gc.tbs.repository.UserRepository;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.security.crypto.password.PasswordEncoder;

class UserServiceSaveUserTest {

  private UserRepository userRepository;
  private UserService service;
  private final Role userRole = role(UserService.USER_ROLE);
  private final Role adminRole = role(UserService.ADMIN_ROLE);

  @BeforeEach
  void setUp() {
    userRepository = Mockito.mock(UserRepository.class);
    RoleRepository roleRepository = Mockito.mock(RoleRepository.class);
    PasswordEncoder encoder = Mockito.mock(PasswordEncoder.class);
    when(encoder.encode(anyString())).thenAnswer(inv -> "hashed:" + inv.getArgument(0));
    when(roleRepository.findByRole(UserService.USER_ROLE)).thenReturn(userRole);
    when(roleRepository.findByRole(UserService.ADMIN_ROLE)).thenReturn(adminRole);
    service = new UserService(userRepository, roleRepository, encoder);
  }

  @Test
  void laterSignupsAreNewDisabledUsersEvenIfTheCallerSaysOtherwise() {
    when(userRepository.count()).thenReturn(5L);
    User user = signup();
    user.setId("existing-admin-id");
    user.setEnabled(true);
    user.setRoles(Set.of(adminRole));

    service.saveUser(user);

    User saved = captureSaved();
    assertThat(saved.getId()).isNull();
    assertThat(saved.isEnabled()).isFalse();
    assertThat(saved.getRoles()).containsExactly(userRole);
    assertThat(saved.getPassword()).isEqualTo("hashed:pw");
    assertThat(saved.getEmail()).isEqualTo("new@example.com");
  }

  @Test
  void firstSignupOnAnEmptyDatabaseIsAnEnabledAdmin() {
    when(userRepository.count()).thenReturn(0L);

    service.saveUser(signup());

    User saved = captureSaved();
    assertThat(saved.isEnabled()).isTrue();
    assertThat(saved.getRoles()).containsExactly(adminRole);
  }

  private static User signup() {
    User user = new User();
    user.setEmail("new@example.com");
    user.setPassword("pw");
    user.setInstitution("TBS");
    return user;
  }

  private User captureSaved() {
    ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
    verify(userRepository).save(captor.capture());
    return captor.getValue();
  }

  private static Role role(String name) {
    Role role = new Role();
    role.setRole(name);
    return role;
  }
}
