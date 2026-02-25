package ca.gc.tbs.controller;

import ca.gc.tbs.domain.User;
import ca.gc.tbs.service.UserService;
import java.util.List;
import java.util.stream.Collectors;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.View;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.view.RedirectView;

@Controller
public class UserController {

  private static final Logger LOG = LoggerFactory.getLogger(UserController.class);

  @Autowired private UserService service;

  @GetMapping(value = "/u/update")
  public @ResponseBody String updateUser(HttpServletRequest request) {
    try {
      this.service.enable(request.getParameter("id"));
      return "Updated";
    } catch (Exception e) {
      return "Error:" + e.getMessage();
    }
  }

  @GetMapping(value = "/u/delete")
  public @ResponseBody String deleteUser(HttpServletRequest request) {
    try {
      this.service.deleteUserById(request.getParameter("id"));
      return "deleted";
    } catch (Exception e) {
      return "Error:" + e.getMessage();
    }
  }

  /* TODO check for prod environment and disable */
  @GetMapping(value = "/enableAdmin")
  public View enableAdmin(HttpServletRequest request, RedirectAttributes atts) {
    try {
      this.service.enableAdmin(request.getParameter("email"));
      atts.addFlashAttribute("successMessage", "User has been enabled and admin");
      return new RedirectView("/success");
    } catch (Exception e) {
      atts.addFlashAttribute("errorMessage", "Failed to enable admin." + e.getMessage());
      return new RedirectView("/error");
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
        List<String> roles = user.getRoles().stream()
            .map(ca.gc.tbs.domain.Role::getRole)
            .collect(Collectors.toList());

        String status = isEn
            ? (enabled ? "Enabled" : "Awaiting approval")
            : (enabled ? "Activé" : "En attente d'approbation");

        String toggleLabel = isEn
            ? (enabled ? "Disable" : "Enable")
            : (enabled ? "Désactiver" : "Activer");

        String toggleClass = enabled ? "disableBtn" : "enableBtn";
        String toggleIdPrefix = enabled ? "disable" : "enable";
        String deleteLabel = isEn ? "Delete" : "Supprimer";

        builder.append("""
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
            </tr>""".formatted(
            email, institution, roles, dateCreated, status,
            toggleIdPrefix, id, toggleClass, toggleLabel,
            id, deleteLabel));
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
