package se.backend.labb2.helpdesk.security;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import se.backend.labb2.helpdesk.user.UserRepository;

import java.util.Locale;

/**
 * Loads users from the database at login. Spring Security then compares the typed
 * password with the stored {bcrypt} hash using the PasswordEncoder bean.
 */
@Service
@Transactional(readOnly = true)
public class DbUserDetailsService implements UserDetailsService {

    // Generic on purpose: never reveal which usernames exist.
    private static final String NOT_FOUND = "Bad credentials";

    private final UserRepository userRepository;

    public DbUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) {
        if (username == null) {
            throw new UsernameNotFoundException(NOT_FOUND);
        }
        String normalized = username.trim().toLowerCase(Locale.ROOT);
        return userRepository.findByUsername(normalized)
                .map(CurrentUser::from)
                .orElseThrow(() -> new UsernameNotFoundException(NOT_FOUND));
    }
}
