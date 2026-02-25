package ca.gc.tbs.controller;

import ca.gc.tbs.domain.Role;
import ca.gc.tbs.domain.User;
import ca.gc.tbs.security.JWTUtil;
import ca.gc.tbs.service.UserService;
import java.util.Arrays;
import java.util.HashSet;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
public class AuthController {

  private final AuthenticationManager authenticationManager;

  private final JWTUtil jwtUtil;

  private final UserService userService;

  public AuthController(
      AuthenticationManager authenticationManager, JWTUtil jwtUtil, UserService userService) {
    this.authenticationManager = authenticationManager;
    this.jwtUtil = jwtUtil;
    this.userService = userService;
  }

  @PostMapping("/authenticate")
  public ResponseEntity<String> createAuthenticationToken(@RequestBody AuthRequest authRequest) {
    try {
      Authentication authentication =
          authenticationManager.authenticate(
              new UsernamePasswordAuthenticationToken(
                  authRequest.username(), authRequest.password()));
      UserDetails userDetails = (UserDetails) authentication.getPrincipal();

      // Check if the user is an admin
      if (!userService.isAdmin(userService.findUserByEmail(userDetails.getUsername()))
          && !userService.isAPI(userService.findUserByEmail(userDetails.getUsername()))) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
            .body("Access denied. Only API users & Admins can generate tokens.");
      }

      String token = jwtUtil.generateToken(userDetails);
      return ResponseEntity.ok(token);
    } catch (BadCredentialsException e) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid username or password");
    }
  }

  @GetMapping("/createApiUser")
  public ResponseEntity<String> createApiUser(
      @RequestParam String username, @RequestParam String password) {
    // Check if user already exists
    User existingUser = userService.findUserByEmail(username);
    if (existingUser != null) {
      return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("User already exists.");
    }

    User user = new User();
    user.setEmail(username);
    user.setPassword(password);
    user.setEnabled(true);

    Role apiRole = userService.findRoleByName("API");
    if (apiRole == null) {
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
          .body("API role does not exist.");
    }
    user.setRoles(new HashSet<>(Arrays.asList(apiRole)));

    userService.saveApiUser(user);
    return ResponseEntity.ok("API user created successfully.");
  }

  record CreateUserRequest(String username, String password) {}

  // Request body for authentication
  record AuthRequest(String username, String password) {}
}
