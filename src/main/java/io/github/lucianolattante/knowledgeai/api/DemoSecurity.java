package io.github.lucianolattante.knowledgeai.api;

import java.util.HashSet;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

@Configuration(proxyBeanMethods = false)
class DemoSecurity {
    static final String ALPHA = "TENANT_ALPHA";
    static final String BETA = "TENANT_BETA";

    @Bean
    @Profile("demo")
    SecurityFilterChain authenticatedDemo(HttpSecurity http) throws Exception {
        // Uploads require a custom title header. DELETE is not a simple cross-origin request.
        return http.csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.requestMatchers("/api/**").authenticated()
                        .anyRequest().denyAll())
                .httpBasic(Customizer.withDefaults())
                .build();
    }

    @Bean
    @Profile("demo")
    UserDetailsService accounts(
            @Value("${knowledgeai.demo.alice-password}") String alice,
            @Value("${knowledgeai.demo.alex-password}") String alex,
            @Value("${knowledgeai.demo.bob-password}") String bob) {
        var credentials = List.of(alice, alex, bob);
        if (credentials.stream().anyMatch(String::isBlank) || new HashSet<>(credentials).size() != 3) {
            throw new IllegalArgumentException("Configure three distinct demo passwords");
        }
        var encoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();
        return new InMemoryUserDetailsManager(
                User.withUsername("alice").password(encoder.encode(alice)).authorities(ALPHA).build(),
                User.withUsername("alex").password(encoder.encode(alex)).authorities(ALPHA).build(),
                User.withUsername("bob").password(encoder.encode(bob)).authorities(BETA).build());
    }

    @Bean
    @Profile("!demo")
    SecurityFilterChain disabledApi(HttpSecurity http) throws Exception {
        return http.authorizeHttpRequests(auth -> auth.anyRequest().denyAll()).build();
    }
}
