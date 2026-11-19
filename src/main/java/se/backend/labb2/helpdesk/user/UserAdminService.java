package se.backend.labb2.helpdesk.user;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * User management for admins. @PreAuthorize on the class guards every method, so a method
 * added later can't be left open by mistake; the /admin/** URL rule is the outer guard.
 */
@Service
@PreAuthorize("hasRole('ADMIN')")
@Transactional(readOnly = true)
public class UserAdminService {

    private final UserRepository userRepository;

    public UserAdminService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /** Every user, ordered by username, without password hashes. */
    public List<UserRow> list() {
        return userRepository.findAllByOrderByUsernameAsc().stream()
                .map(UserRow::from)
                .toList();
    }
}
