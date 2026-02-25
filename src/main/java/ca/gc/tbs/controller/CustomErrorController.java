package ca.gc.tbs.controller;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
public class CustomErrorController implements ErrorController {

  @RequestMapping("/error")
  public String handleError(HttpServletRequest request) {
    String lang = (String) request.getSession().getAttribute("lang");
    // Default to English if lang is null
    if (lang == null || lang.isEmpty()) {
      lang = "en";
    }
    return "error_" + lang;
  }

}
