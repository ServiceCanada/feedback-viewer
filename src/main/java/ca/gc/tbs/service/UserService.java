// UserService.java
package ca.gc.tbs.service;

import ca.gc.tbs.domain.Role;
import ca.gc.tbs.domain.User;
import ca.gc.tbs.repository.RoleRepository;
import ca.gc.tbs.repository.UserRepository;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class UserService implements UserDetailsService {

  public static final String USER_ROLE = "USER";
  public static final String ADMIN_ROLE = "ADMIN";
  public static final String API_ROLE = "API";

  public static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

  private final UserRepository userRepository;
  private final RoleRepository roleRepository;
  private final PasswordEncoder bCryptPasswordEncoder;

  public UserService(
      UserRepository userRepository,
      RoleRepository roleRepository,
      @Autowired(required = false) PasswordEncoder bCryptPasswordEncoder) {
    this.userRepository = userRepository;
    this.roleRepository = roleRepository;
    this.bCryptPasswordEncoder = bCryptPasswordEncoder;
  }

  public User findUserByEmail(String email) {
    return userRepository.findByEmail(email);
  }

  public User findUserById(String Id) {
    return userRepository.findById(Id).get();
  }

  public List<User> findUserByRole(String role) {
    var oRole = this.roleRepository.findByRole(role);
    return userRepository.findByRolesContaining(oRole);
  }

  public void deleteUserById(String Id) {
    userRepository.deleteById(Id);
  }

  public List<User> findAllUsers() {
    return userRepository.findAll();
  }

  public List<String> findInstitutions() {
    return userRepository.findAllInstitutions();
  }

  public User getCurrentUser() {
    var auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth.getPrincipal()
        instanceof org.springframework.security.core.userdetails.User springUser) {
      return this.findUserByEmail(springUser.getUsername());
    }
    return null;
  }

  public void saveUser(User user) {
    String hashedPassword = bCryptPasswordEncoder.encode(user.getPassword());
    if (userRepository.findByEmail(user.getEmail()) != null) {
      return;
    }
    boolean firstUser = this.userRepository.count() <= 0;
    user.setId(null);
    user.setEnabled(firstUser);
    user.setPassword(hashedPassword);
    user.setDateCreated(DATE_FORMATTER.format(LocalDate.now()));
    Role role = roleRepository.findByRole(firstUser ? ADMIN_ROLE : USER_ROLE);
    user.setRoles(new HashSet<>(Arrays.asList(role)));
    userRepository.save(user);
  }

  public void saveApiUser(User user) {
    user.setPassword(bCryptPasswordEncoder.encode(user.getPassword()));
    user.setDateCreated(DATE_FORMATTER.format(LocalDate.now()));
    var apiRole = roleRepository.findByRole(API_ROLE);
    user.setRoles(new HashSet<>(Arrays.asList(apiRole)));
    userRepository.save(user);
  }

  public Role findRoleByName(String roleName) {
    return roleRepository.findByRole(roleName);
  }

  public boolean isAdmin(User user) {
    for (Role role : user.getRoles()) {
      if (role.getRole().contentEquals(ADMIN_ROLE)) {
        return true;
      }
    }
    return false;
  }

  public boolean isAPI(User user) {
    for (Role role : user.getRoles()) {
      if (role.getRole().contentEquals(API_ROLE)) {
        return true;
      }
    }
    return false;
  }

  public void enable(String id) {
    var user = this.findUserById(id);
    user.setEnabled(true);
    userRepository.save(user);
  }

  @Override
  public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {

    var user = userRepository.findByEmail(email);
    if (user != null && user.isEnabled()) {
      var authorities = getUserAuthority(user.getRoles());
      return buildUserForAuthentication(user, authorities);
    } else {
      throw new UsernameNotFoundException("username not found");
    }
  }

  private List<GrantedAuthority> getUserAuthority(Set<Role> userRoles) {
    var roles = new HashSet<GrantedAuthority>();
    userRoles.forEach(
        (role) -> {
          roles.add(new SimpleGrantedAuthority(role.getRole()));
        });

    var grantedAuthorities = new ArrayList<>(roles);
    return grantedAuthorities;
  }

  private UserDetails buildUserForAuthentication(User user, List<GrantedAuthority> authorities) {
    return new org.springframework.security.core.userdetails.User(
        user.getEmail(), user.getPassword(), authorities);
  }
}
