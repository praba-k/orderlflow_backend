package com.portfolio.oms;

import java.util.TimeZone;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class OrderPlatformApplication {
  static void main(String[] args) {
    // PostgreSQL JDBC sends the JVM timezone when opening connections, before Hibernate runs.
//    TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    SpringApplication.run(OrderPlatformApplication.class, args);
  }
}
