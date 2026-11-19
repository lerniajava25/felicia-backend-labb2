package se.backend.labb2.helpdesk.user;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

/**
 * User management for admins. @PreAuthorize on the class guards every method, so a method
 * added later can't be left open by mistake; the /admin/** URL rule is the outer guard.
 */
@Service
@PreAuthorize("hasRole('ADMIN')")
@Transactional(readOnly = true)
public class UserAdminService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserAdminService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /** Every user, ordered by username, without password hashes. */
    public List<UserRow> list() {
        return userRepository.findAllByOrderByUsernameAsc().stream()
                .map(UserRow::from)
                .toList();
    }

    /**
     * Creates a user with a hashed password. Throws UsernameTakenException when the early
     * check finds the name, and DataIntegrityViolationException when the database's unique
     * constraint does (two admins at the same time). Nothing here logs the password.
     */
    @Transactional
    public void create(NewUserForm form) {
        String username = normalizeUsername(form.getUsername());
        // A friendlier early message, not the real guard: two admins can both pass this check.
        // The unique constraint on users.username is what really stops a duplicate.
        if (userRepository.existsByUsername(username)) {
            throw new UsernameTakenException();
        }
        // The delegating encoder adds the {bcrypt} prefix, so the algorithm can change later.
        String hash = passwordEncoder.encode(form.getPassword());
        // saveAndFlush: the INSERT runs now, so a duplicate fails in this call, not at commit.
        // Its DataIntegrityViolationException is not caught here: it has already marked the
        // transaction rollback-only, and catching it would only turn it into an
        // UnexpectedRollbackException at commit. The controller handles it instead.
        userRepository.saveAndFlush(new User(username, hash, form.getDisplayName().trim(), form.getRole()));
    }

    /** Same as login (DbUserDetailsService), so a new user can sign in with what the admin typed. */
    private static String normalizeUsername(String username) {
        return username.trim().toLowerCase(Locale.ROOT);
    }
}
