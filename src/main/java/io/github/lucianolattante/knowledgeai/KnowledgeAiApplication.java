package io.github.lucianolattante.knowledgeai;

import java.time.Clock;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.context.annotation.Bean;

@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class KnowledgeAiApplication {
    public static void main(String[] args) {
        SpringApplication.run(KnowledgeAiApplication.class, args);
    }

    @Bean
    Clock requestClock() {
        return Clock.systemUTC();
    }
}
