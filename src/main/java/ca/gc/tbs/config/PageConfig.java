/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ca.gc.tbs.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

@Configuration
public class PageConfig implements WebMvcConfigurer {

  @Value("${pagesuccess.pythonScriptPath}")
  private String pythonScriptPath;

  @Override
  public void addViewControllers(ViewControllerRegistry registry) {
    registry.addViewController("/home").setViewName("home");
    registry.addViewController("/").setViewName("home");
    registry.addViewController("/dashboard").setViewName("dashboard");
    registry.addViewController("/login").setViewName("login");
  }

  @Override
  public void addResourceHandlers(ResourceHandlerRegistry registry) {
    registry
        .addResourceHandler("/static/**") // « /static/css/myStatic.css
        .addResourceLocations("classpath:/static/") // Default Static Loaction
        .setCachePeriod(3600)
        .resourceChain(true) // 4.1
        .addResolver(new PathResourceResolver()); // 4.1

    // src/main/resources/templates/static/...
    registry
        .addResourceHandler("/templates/**") // « /templates/style.css
        .addResourceLocations("classpath:/templates/static/");

    // File located on disk
    registry
        .addResourceHandler("/python/**")
        .addResourceLocations("file://" + this.pythonScriptPath);
  }
}
