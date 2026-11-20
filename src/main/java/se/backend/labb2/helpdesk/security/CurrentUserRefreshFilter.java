package se.backend.labb2.helpdesk.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Re-reads the logged-in user from the database on every request, so a role change or a
 * disabled account applies at once, not only at the next login. Without this, the session
 * keeps the CurrentUser copied at login (role and enabled flag from that moment), and the
 * URL rules, @PreAuthorize and loadVisible would all go on trusting that old copy.
 *
 * Not a @Component on purpose: Spring Boot would then also register it as a plain servlet
 * filter, outside the security chain. SecurityConfig creates it and puts it in the chain.
 */
public class CurrentUserRefreshFilter extends OncePerRequestFilter {

    private final UserDetailsService userDetailsService;
    private final SecurityContextRepository securityContextRepository;

    public CurrentUserRefreshFilter(UserDetailsService userDetailsService,
                                    SecurityContextRepository securityContextRepository) {
        this.userDetailsService = userDetailsService;
        this.securityContextRepository = securityContextRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        // Only our own logins carry a CurrentUser; anonymous visitors are left alone.
        if (auth != null && auth.getPrincipal() instanceof CurrentUser stored) {
            CurrentUser fresh = reload(stored.username());
            if (fresh == null || !fresh.isEnabled()) {
                // Disabled or gone: end this session. The request goes on as anonymous, so a
                // protected page sends them to /login.
                new SecurityContextLogoutHandler().logout(request, response, auth);
            } else if (!fresh.equals(stored)) {
                // The role (or another field) changed: use the stored values from now on.
                // CurrentUser is a record, so equals compares every field.
                SecurityContext context = SecurityContextHolder.createEmptyContext();
                context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                        fresh, null, fresh.getAuthorities()));
                SecurityContextHolder.setContext(context);
                securityContextRepository.saveContext(context, request, response);
            }
        }
        chain.doFilter(request, response);
    }

    /** The same lookup as login (DbUserDetailsService), or null when the user no longer exists. */
    private CurrentUser reload(String username) {
        try {
            return (CurrentUser) userDetailsService.loadUserByUsername(username);
        } catch (UsernameNotFoundException e) {
            return null;
        }
    }

    // The stylesheet needs no database query.
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return request.getServletPath().startsWith("/css/");
    }
}
