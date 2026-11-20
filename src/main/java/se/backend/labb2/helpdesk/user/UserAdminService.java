package se.backend.labb2.helpdesk.user;

import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import se.backend.labb2.helpdesk.common.NotFoundException;
import se.backend.labb2.helpdesk.common.RuleViolationException;
import se.backend.labb2.helpdesk.security.CurrentUser;

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

    // Shown to the admin as flash messages, so written in plain words.
    static final String OWN_ACCOUNT_MESSAGE = "You can't change your own account here.";
    static final String LAST_ADMIN_MESSAGE = "The last enabled admin can't be demoted or disabled.";

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

    /** One user for the edit page. An unknown id is a 404. */
    public UserRow get(Long id) {
        return userRepository.findById(id).map(UserRow::from).orElseThrow(NotFoundException::new);
    }

    /**
     * Changes a user's role and enabled flag. Refuses an outdated version, the admin's own
     * account, and anything that would leave no enabled admin. Either way nothing is saved.
     */
    @Transactional
    public void update(Long id, EditUserForm form, CurrentUser current) {
        User user = userRepository.findById(id).orElseThrow(NotFoundException::new);
        // Someone changed the user after the page was loaded: change nothing (as in TicketService).
        if (!user.getVersion().equals(form.getVersion())) {
            throw new ObjectOptimisticLockingFailureException(User.class, id);
        }
        // An admin can't lock themselves out, by mistake or otherwise.
        if (user.getId().equals(current.id())) {
            throw new RuleViolationException(OWN_ACCOUNT_MESSAGE);
        }
        // Someone must always be able to manage users. Only matters when this user is an
        // enabled admin today and would stop being one.
        boolean losesAdmin = user.getRole() == Role.ADMIN && Boolean.TRUE.equals(user.getEnabled())
                && (form.getRole() != Role.ADMIN || !form.getEnabled());
        if (losesAdmin) {
            // One at a time: lock every enabled admin row (the same rows for every demotion,
            // since this user is one of them) and count after the lock. A plain count is not
            // enough: two admins demoting each other at the same moment would both see two
            // admins. With the lock the second one waits here, then sees the first one's
            // change and is refused. The lock lasts until this transaction commits.
            if (userRepository.findAndLockEnabledByRole(Role.ADMIN).size() <= 1) {
                throw new RuleViolationException(LAST_ADMIN_MESSAGE);
            }
        }
        user.changeRole(form.getRole());
        user.setEnabled(form.getEnabled());
        // No save(): the user is managed, so Hibernate writes "UPDATE ... WHERE version = ?"
        // at commit. If another admin got there first, that update hits 0 rows and Spring
        // throws ObjectOptimisticLockingFailureException.
    }

    /** Same as login (DbUserDetailsService), so a new user can sign in with what the admin typed. */
    private static String normalizeUsername(String username) {
        return username.trim().toLowerCase(Locale.ROOT);
    }
}
