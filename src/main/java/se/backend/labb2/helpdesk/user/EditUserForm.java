package se.backend.labb2.helpdesk.user;

import jakarta.validation.constraints.NotNull;

/**
 * What the "Edit user" form may send: role, enabled and the version. There are no username
 * or password fields, so a request has nothing to bind them to and can't change them.
 */
public class EditUserForm {

    @NotNull
    private Role role;

    // th:field on the checkbox adds a hidden "_enabled" marker, so an unticked box binds to
    // false. Only a hand-made request leaves this null.
    @NotNull
    private Boolean enabled;

    @NotNull
    private Long version;

    /** The user's current values, for the edit page. */
    public static EditUserForm from(UserRow row) {
        EditUserForm form = new EditUserForm();
        form.setRole(row.role());
        form.setEnabled(row.enabled());
        form.setVersion(row.version());
        return form;
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }
}
