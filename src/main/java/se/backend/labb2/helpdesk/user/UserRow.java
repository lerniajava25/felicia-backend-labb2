package se.backend.labb2.helpdesk.user;

/**
 * One line in the admin's user list. A plain copy of what the table shows, so no entity
 * reaches the view. There is deliberately no password hash: it never leaves the service.
 *
 * @param version sent back by the edit form, to detect another admin's change
 */
public record UserRow(Long id, String username, String displayName, Role role, Boolean enabled, Long version) {

    public static UserRow from(User user) {
        return new UserRow(
                user.getId(),
                user.getUsername(),
                user.getDisplayName(),
                user.getRole(),
                user.getEnabled(),
                user.getVersion());
    }
}
