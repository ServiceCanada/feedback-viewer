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

    String returnData = "";
    try {
      StringBuilder builder = new StringBuilder();
      List<User> users = this.service.findAllUsers();
      if (lang.equals("en")) {
        for (User user : users) {
          builder.append("<tr><td>" + user.getEmail() + "</td>");

          builder.append("<td>" + user.getInstitution() + "</td>");
          List<String> roles =
              user.getRoles().stream().map(role -> role.getRole()).collect(Collectors.toList());

          builder.append("<td>" + roles + "</td>");
          builder.append("<td>" + user.getDateCreated() + "</td>");
          builder.append("<td>" + (user.isEnabled() ? "Enabled" : "Awaiting approval") + "</td>");
          builder.append("<td><div class='btn-group'>");
          if (!user.isEnabled()) {
            builder.append(
                "<button id='enable"
                    + user.getId()
                    + "' class='btn btn-xs enableBtn'>Enable</button>");
          } else {
            builder.append(
                "<button id='disable"
                    + user.getId()
                    + "' class='btn btn-xs disableBtn'>Disable</button>");
          }
          builder.append(
              "<button id='delete"
                  + user.getId()
                  + "' class='btn btn-xs deleteBtn'>Delete</button>");

          builder.append("</div></td>");
          builder.append("</tr>");
        }
      } else {
        for (User user : users) {
          builder.append("<tr><td>" + user.getEmail() + "</td>");

          builder.append("<td>" + user.getInstitution() + "</td>");
          List<String> roles =
              user.getRoles().stream().map(role -> role.getRole()).collect(Collectors.toList());

          builder.append("<td>" + roles + "</td>");
          builder.append("<td>" + user.getDateCreated() + "</td>");
          builder.append(
              "<td>" + (user.isEnabled() ? "Activé" : "En attente d'approbation") + "</td>");
          builder.append("<td><div class='btn-group'>");
          if (!user.isEnabled()) {
            builder.append(
                "<button id='enable"
                    + user.getId()
                    + "' class='btn btn-xs enableBtn'>Activer</button>");
          } else {
            builder.append(
                "<button id='disable"
                    + user.getId()
                    + "' class='btn btn-xs disableBtn'>Désactiver</button>");
          }
          builder.append(
              "<button id='delete"
                  + user.getId()
                  + "' class='btn btn-xs deleteBtn'>Supprimer</button>");

          builder.append("</div></td>");
          builder.append("</tr>");
        }
      }

      returnData = builder.toString();
    } catch (Exception e) {
      LOG.error(e.getMessage());
    }
    return returnData;
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
