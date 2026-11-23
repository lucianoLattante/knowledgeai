package io.github.lucianolattante.knowledgeai;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;

@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class KnowledgeAiApplication {
    public static void main(String[] args) {
        SpringApplication.run(KnowledgeAiApplication.class, args);
    }
}
