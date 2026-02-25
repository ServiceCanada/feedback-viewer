package ca.gc.tbs.filter;

import java.io.IOException;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;

@Component
public class LanguageFilter implements Filter {

  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
      throws IOException, ServletException {

    HttpServletRequest req = (HttpServletRequest) request;
    HttpSession session = req.getSession();

    String lang = getSelectedLang(req);

    session.setAttribute("lang", lang.equals("en") ? "en" : "fr");

    // build alt lang
    String altLang;
    String altLangText;
    if (lang.equals("en")) {
      altLang = "fr";
      altLangText = "Français";
    } else {
      altLang = "en";
      altLangText = "English";
    }

    String requestURL = req.getRequestURL().toString();
    String queryString = cleanQueryStringLangParam(req, lang);

    if (queryString.equals("")) {
      requestURL = requestURL + "?lang=" + altLang;
    } else {
      requestURL = requestURL + "?" + queryString + "&lang=" + altLang;
    }

    session.setAttribute("langUrl", requestURL);
    session.setAttribute("altLang", altLang);
    session.setAttribute("altLangText", altLangText);

    chain.doFilter(request, response);
  }

  private String cleanQueryStringLangParam(HttpServletRequest req, String lang) {
    String queryString = req.getQueryString();
    if (queryString == null) {
      return "";
    }

    queryString = queryString.replaceAll("(^|&)lang=" + lang + "($|&)", "&");
    queryString = queryString.replaceAll("&&+", "&");
    queryString = queryString.replaceAll("^&|&$", "");

    return queryString;
  }

  private String getSelectedLang(HttpServletRequest req) {
    String lang = (String) req.getSession().getAttribute("lang");

    // if lang query param set -> set to selected language
    String langParam = req.getParameter("lang");
    if (langParam != null) {
      lang = langParam;
    }
    // find default if needed
    if (lang == null) {
      lang = LocaleContextHolder.getLocale().getLanguage();
    }
    return lang;
  }
}
