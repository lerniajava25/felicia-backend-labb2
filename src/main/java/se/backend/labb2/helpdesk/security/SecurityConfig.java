package se.backend.labb2.helpdesk.security;

import jakarta.servlet.DispatcherType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

import static org.springframework.security.config.Customizer.withDefaults;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        // Error pages and forwards inherit the original request's check;
                        // without this a 404 inside a protected page would become a 403.
                        .dispatcherTypeMatchers(DispatcherType.ERROR, DispatcherType.FORWARD).permitAll()
                        .requestMatchers("/login", "/css/**", "/error").permitAll()
                        .requestMatchers("/admin/**").hasRole("ADMIN")
                        .requestMatchers("/agent/**").hasRole("AGENT")
                        .requestMatchers("/", "/tickets/**").authenticated()
                        // Deny by default: a new URL is blocked until it gets a rule above.
                        .anyRequest().denyAll())
                // Our own page. Defaults kept: POST /login, failure /login?error, logout /login?logout.
                .formLogin(form -> form.loginPage("/login"))
                .logout(withDefaults());
        // CSRF is on by default and stays on.
        return http.build();
    }

    // One bean, used by both the URL rules above and @PreAuthorize.
    // Static because method security reads it very early during startup.
    @Bean
    static RoleHierarchy roleHierarchy() {
        return RoleHierarchyImpl.withDefaultRolePrefix()
                .role("ADMIN").implies("AGENT")
                .role("AGENT").implies("USER")
                .build();
    }
}
