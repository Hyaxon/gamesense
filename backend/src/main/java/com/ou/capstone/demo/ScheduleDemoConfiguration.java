package com.ou.capstone.demo;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/** Opt-in local demo; normal backend startup does not read schedule files. */
@Configuration
@Profile("schedule-demo")
public class ScheduleDemoConfiguration {
  @Bean
  public ScheduleDemoData scheduleDemoData(
      ObjectMapper mapper, @Value("${demo.data-directory:target/schedule-demo}") String directory)
      throws IOException {
    return ScheduleDemoData.read(Path.of(directory), mapper);
  }
}
