package se.backend.labb2.helpdesk.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import se.backend.labb2.helpdesk.common.MaxUtf8Bytes;

import java.util.Locale;

/**
 * What the "New user" form may send. Max lengths match the columns in User.
 * The messages never repeat the typed value, so a password can't come back through one.
 * No toString() on purpose: the form must never end up in a log line with its password.
 */
public class NewUserForm {

    // Checked after the setter has trimmed and lowercased it, so the rule matches exactly
    // what is stored and what login looks up ("BILLY2" is fine and becomes "billy2").
    @NotBlank(message = "Username is required")
    @Size(min = 3, max = 50, message = "Username must be 3–50 characters")
    @Pattern(regexp = "[a-z0-9._-]*", message = "Username may only contain a–z, 0–9, dot, dash and underscore")
    private String username;

    @NotBlank(message = "Display name is required")
    @Size(max = 100, message = "Display name can be at most 100 characters")
    private String displayName;

    @NotNull(message = "Choose a role")
    private Role role;

    @NotBlank(message = "Password is required")
    @Size(min = 12, max = 64, message = "Password must be 12–64 characters")
    // BCrypt only takes 72 bytes and refuses more. Letters like å, ä, ö take 2 bytes each, so
    // 64 characters can be too many. Checked here, before the service hands it to the encoder.
    @MaxUtf8Bytes(value = 72, message = "Password is too long: use fewer special characters")
    private String password;

    public String getUsername() {
        return username;
    }

    /** Same normalization as login (DbUserDetailsService): trim, then lowercase with Locale.ROOT. */
    public void setUsername(String username) {
        this.username = username == null ? null : username.trim().toLowerCase(Locale.ROOT);
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}
