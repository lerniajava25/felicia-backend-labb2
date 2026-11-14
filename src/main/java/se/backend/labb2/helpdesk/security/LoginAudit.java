package se.backend.labb2.helpdesk.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AbstractAuthenticationFailureEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.WebAuthenticationDetails;
import org.springframework.stereotype.Component;

/**
 * Writes one log line per login attempt. Spring Boot already publishes the events
 * (SecurityAutoConfiguration registers a DefaultAuthenticationEventPublisher).
 * Never logs the password or the whole Authentication object.
 */
@Component
public class LoginAudit {

    private static final Logger log = LoggerFactory.getLogger(LoginAudit.class);

    @EventListener
    public void onFailure(AbstractAuthenticationFailureEvent event) {
        Authentication auth = event.getAuthentication();
        log.warn("Failed login for user '{}' from {}: {}",
                sanitize(auth.getName()), remoteAddress(auth), event.getException().getMessage());
    }

    @EventListener
    public void onSuccess(AuthenticationSuccessEvent event) {
        Authentication auth = event.getAuthentication();
        log.info("Successful login for user '{}' from {}", sanitize(auth.getName()), remoteAddress(auth));
    }

    // The username is typed by the visitor: remove line breaks so it can't fake extra log lines.
    private static String sanitize(String value) {
        return value == null ? "?" : value.replace("\r", "").replace("\n", "");
    }

    private static String remoteAddress(Authentication auth) {
        return auth.getDetails() instanceof WebAuthenticationDetails details
                ? details.getRemoteAddress()
                : "unknown";
    }
}
