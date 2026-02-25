package ca.gc.tbs.controller;

import ca.gc.tbs.domain.User;
import ca.gc.tbs.service.EmailService;
import ca.gc.tbs.service.UserService;
import java.text.SimpleDateFormat;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.View;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.view.RedirectView;

@Controller
public class LoginController {
  public static final String DATE_FORMAT = "yyyy-MM-dd";
  public static final SimpleDateFormat format = new SimpleDateFormat(DATE_FORMAT);

  @Autowired private UserService userService;

  @Autowired private EmailService emailService;

  @RequestMapping(value = "/login", method = RequestMethod.GET)
  public ModelAndView login(HttpServletRequest request) throws Exception {
    ModelAndView modelAndView = new ModelAndView();
    String lang = (String) request.getSession().getAttribute("lang");
    // System.out.println(lang);
    modelAndView.setViewName("login_" + lang);
    return modelAndView;
  }

  @RequestMapping(value = "/signup", method = RequestMethod.GET)
  public ModelAndView signup(HttpServletRequest request) {
    ModelAndView modelAndView = new ModelAndView();
    User user = new User();
    String lang = (String) request.getSession().getAttribute("lang");
    modelAndView.addObject("user", user);
    modelAndView.addObject("institutions", this.userService.findInstitutions());
    modelAndView.setViewName("signup_" + lang);
    return modelAndView;
  }

  @GetMapping("/checkExists")
  public @ResponseBody String checkExists(@RequestParam String email) {
    // verify that one has not already been created.
    User userExists = userService.findUserByEmail(email);
    if (userExists != null) {
      return "true";
    } else {
      return "false";
    }
  }

  @RequestMapping(value = "/signup", method = RequestMethod.POST)
  public RedirectView createNewUser(@Valid User user, RedirectAttributes atts) {
    userService.saveUser(user);
    emailService.sendUserActivationRequestEmail(user.getEmail());
    atts.addFlashAttribute(
        "successMessage",
        "User has been registered successfully. You will be notified when the account has been"
            + " activated.");
    return new RedirectView("success");
  }

  @RequestMapping(
      value = {"/success"},
      method = RequestMethod.GET)
  public ModelAndView success(HttpServletRequest request) {
    String lang = (String) request.getSession().getAttribute("lang");
    ModelAndView mav = new ModelAndView();
    mav.setViewName("success_" + lang);
    return mav;
  }

  // redirects localhost to sign in page.
  @RequestMapping(
      value = {"/"},
      method = RequestMethod.GET)
  public View home(@Valid User user) {
    ModelAndView modelAndView = new ModelAndView();
    modelAndView.setViewName("home");

    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    RedirectView view = new RedirectView("/pageFeedback");

    // if user is not logged in, redirect to signin
    if (auth.getName().equals("anonymousUser")) {
      view = new RedirectView("signin");
    }

    return view;
  }
}
