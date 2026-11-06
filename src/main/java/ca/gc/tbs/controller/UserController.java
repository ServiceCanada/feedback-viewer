package ca.gc.tbs.controller;

import ca.gc.tbs.domain.User;
import ca.gc.tbs.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.util.HtmlUtils;

@Controller
@PreAuthorize("hasAuthority('ADMIN')")
public class UserController {

  private static final Logger LOG = LoggerFactory.getLogger(UserController.class);

  @Autowired private UserService service;

  @PostMapping(value = "/u/update")
  public @ResponseBody String updateUser(HttpServletRequest request) {
    try {
      this.service.enable(request.getParameter("id"));
      return "Updated";
    } catch (Exception e) {
      LOG.error("Error updating user", e);
      return "Error: an internal error occurred";
    }
  }

  @PostMapping(value = "/u/delete")
  public @ResponseBody String deleteUser(HttpServletRequest request) {
    try {
      this.service.deleteUserById(request.getParameter("id"));
      return "deleted";
    } catch (Exception e) {
      LOG.error("Error deleting user", e);
      return "Error: an internal error occurred";
    }
  }

  public String getData(String lang) {
    try {
      StringBuilder builder = new StringBuilder();
      List<User> users = this.service.findAllUsers();
      boolean isEn = "en".equals(lang);

      for (User user : users) {
        String id = user.getId();
        String email = user.getEmail();
        String institution = user.getInstitution();
        String dateCreated = user.getDateCreated();
        boolean enabled = user.isEnabled();
        List<String> roles =
            user.getRoles() != null
                ? user.getRoles().stream().map(ca.gc.tbs.domain.Role::getRole).toList()
                : List.of();

        String status =
            isEn
                ? (enabled ? "Enabled" : "Awaiting approval")
                : (enabled ? "Activé" : "En attente d'approbation");

        String toggleLabel =
            isEn ? (enabled ? "Disable" : "Enable") : (enabled ? "Désactiver" : "Activer");

        String toggleClass = enabled ? "disableBtn" : "enableBtn";
        String toggleIdPrefix = enabled ? "disable" : "enable";
        String deleteLabel = isEn ? "Delete" : "Supprimer";

        builder.append(
            """
            <tr>
              <td>%s</td>
              <td>%s</td>
              <td>%s</td>
              <td>%s</td>
              <td>%s</td>
              <td>
                <div class='btn-group'>
                  <button id='%s%s' class='btn btn-xs %s'>%s</button>
                  <button id='delete%s' class='btn btn-xs deleteBtn'>%s</button>
                </div>
              </td>
            </tr>"""
                .formatted(
                    HtmlUtils.htmlEscape(email != null ? email : ""),
                    HtmlUtils.htmlEscape(institution != null ? institution : ""),
                    HtmlUtils.htmlEscape(roles.toString()),
                    dateCreated,
                    status,
                    toggleIdPrefix,
                    id,
                    toggleClass,
                    toggleLabel,
                    id,
                    deleteLabel));
      }
      return builder.toString();
    } catch (Exception e) {
      LOG.error(e.getMessage());
    }
    return "";
  }

  @GetMapping(value = "/u/index")
  public ModelAndView dashboard(HttpServletRequest request) throws Exception {
    ModelAndView mav = new ModelAndView();
    String lang = (String) request.getSession().getAttribute("lang");
    mav.addObject("data", this.getData(lang));
    mav.setViewName("users_" + lang);
    return mav;
  }
}
