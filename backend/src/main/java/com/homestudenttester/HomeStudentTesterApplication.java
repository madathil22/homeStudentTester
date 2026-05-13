package com.homestudenttester;

import com.homestudenttester.config.AppProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@EnableConfigurationProperties(AppProperties.class)
public class HomeStudentTesterApplication {
  public static void main(String[] args) {
    SpringApplication.run(HomeStudentTesterApplication.class, args);
  }
}
