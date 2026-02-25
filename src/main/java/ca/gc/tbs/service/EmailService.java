package ca.gc.tbs.service;

import ca.gc.tbs.domain.User;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import uk.gov.service.notify.NotificationClient;

@Service
public class EmailService {

  private final String userActivationRequestKey;

  private final String accountEnabledKey;

  private final String loginURL;

  private final UserService userService;

  public EmailService(
      @Value("${notify.templateid.accountenabled}") String userActivationRequestKey,
      @Value("${notify.templateid.useractivationrequest}") String accountEnabledKey,
      @Value("${pagesuccess.loginURL}") String loginURL,
      UserService userService) {
    this.userActivationRequestKey = userActivationRequestKey;
    this.accountEnabledKey = accountEnabledKey;
    this.loginURL = loginURL;
    this.userService = userService;
  }

  public String getUserActivationRequestKey() {
    return userActivationRequestKey;
  }

  public String getAccountEnabledKey() {
    return accountEnabledKey;
  }

  public NotificationClient getNotificationClient() {
    return new NotificationClient(getAPIKey(), "https://api.notification.alpha.canada.ca");
  }

  private String getAPIKey() {
    try {
      File file =
          new File(
              getClass()
                  .getClassLoader()
                  .getResource("static/secrets/notification.secret")
                  .getFile());
      return new String(
          Files.readAllBytes(Paths.get(file.getCanonicalPath())), StandardCharsets.UTF_8);
    } catch (Exception e) {

    }
    return "";
  }

  public void sendUserActivationRequestEmail(String email) {
    Map<String, String> personalisation = new HashMap<>();
    personalisation.put("email", email);
    personalisation.put("loginURL", loginURL);
    List<User> admins = this.userService.findUserByRole(UserService.ADMIN_ROLE);
    for (User user : admins) {
      try {
        this.getNotificationClient()
            .sendEmail(this.userActivationRequestKey, user.getEmail(), personalisation, "");
      } catch (Exception e) {
        System.out.println(e.getMessage());
      }
    }
  }
}
