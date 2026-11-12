package se.backend.labb2.helpdesk.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import se.backend.labb2.helpdesk.user.Role;
import se.backend.labb2.helpdesk.user.User;

import java.util.Collection;
import java.util.List;

/**
 * The logged-in user as Spring Security sees it. A plain copy of the User entity,
 * so no JPA entity ends up in the HTTP session.
 */
public record CurrentUser(Long id,
                          String username,
                          String passwordHash,
                          String displayName,
                          Role role,
                          Boolean enabled) implements UserDetails {

    public static CurrentUser from(User user) {
        return new CurrentUser(
                user.getId(),
                user.getUsername(),
                user.getPasswordHash(),
                user.getDisplayName(),
                user.getRole(),
                user.getEnabled());
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override
    public boolean isEnabled() {
        return Boolean.TRUE.equals(enabled);
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    // A record's default toString() prints every field; keep the hash out of logs.
    @Override
    public String toString() {
        return "CurrentUser[id=" + id + ", username=" + username + ", passwordHash=[PROTECTED]"
                + ", displayName=" + displayName + ", role=" + role + ", enabled=" + enabled + "]";
    }
}
