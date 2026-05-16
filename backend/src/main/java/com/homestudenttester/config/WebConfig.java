package com.homestudenttester.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {
  @Override
  public void addCorsMappings(CorsRegistry registry) {
    registry.addMapping("/api/**")
        .allowedOriginPatterns("http://localhost:*", "http://127.0.0.1:*", "http://10.*:*", "http://172.*:*",
            "http://192.*:*")
        .allowedMethods("GET", "POST", "DELETE", "OPTIONS")
        .allowedHeaders("*");
  }
}
