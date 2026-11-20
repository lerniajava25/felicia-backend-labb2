package se.backend.labb2.helpdesk.user;

import jakarta.validation.Valid;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import se.backend.labb2.helpdesk.common.FlashFeedback;
import se.backend.labb2.helpdesk.security.CurrentUser;

/** Admin pages for users. Access is checked by the /admin/** URL rule and @PreAuthorize in the service. */
@Controller
public class AdminUserController {

    static final String USERNAME_TAKEN_MESSAGE = "That username is taken";
    static final String CONFLICT_MESSAGE = "Someone else changed this user. Check it and try again.";

    private final UserAdminService userAdminService;

    public AdminUserController(UserAdminService userAdminService) {
        this.userAdminService = userAdminService;
    }

    @GetMapping("/admin/users")
    public String list(Model model) {
        model.addAttribute("users", userAdminService.list());
        return "admin/users";
    }

    // The choices for the role dropdown, on every page of this controller (form and errors).
    @ModelAttribute("roles")
    public Role[] roles() {
        return Role.values();
    }

    @GetMapping("/admin/users/new")
    public String newForm(Model model) {
        model.addAttribute("form", new NewUserForm());
        return "admin/new-user";
    }

    @PostMapping("/admin/users")
    public String create(@Valid @ModelAttribute("form") NewUserForm form, BindingResult result) {
        if (!result.hasErrors()) {
            try {
                userAdminService.create(form);
                return "redirect:/admin/users";
            } catch (UsernameTakenException | DataIntegrityViolationException e) {
                // Username is the only unique column, so a constraint error here means "taken".
                // The exception is not logged: it is an expected case, not an error.
                result.rejectValue("username", "taken", USERNAME_TAKEN_MESSAGE);
            }
        }
        // Same page with the errors and the typed values, except the password: it is never
        // sent back (the template has no th:field on it either).
        form.setPassword(null);
        return "admin/new-user";
    }

    // Always the user's current values and version from the database: after a refused save
    // the admin sees what is really stored, and a second click doesn't reuse an old version.
    @GetMapping("/admin/users/{id}/edit")
    public String editForm(@PathVariable Long id, @AuthenticationPrincipal CurrentUser current, Model model) {
        UserRow user = userAdminService.get(id);
        model.addAttribute("user", user);
        model.addAttribute("form", EditUserForm.from(user));
        // Only a hint for the page; the service refuses changes to your own account anyway.
        model.addAttribute("isSelf", id.equals(current.id()));
        return "admin/edit-user";
    }

    @PostMapping("/admin/users/{id}")
    public String update(@PathVariable Long id,
                         @Valid @ModelAttribute("form") EditUserForm form,
                         BindingResult result,
                         @AuthenticationPrincipal CurrentUser current,
                         RedirectAttributes flash) {
        if (result.hasErrors()) {
            // The form always sends a role, the checkbox marker and the version; only a
            // hand-made request doesn't.
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        }
        if (FlashFeedback.attempt(() -> userAdminService.update(id, form, current), flash, null, CONFLICT_MESSAGE)) {
            return "redirect:/admin/users";
        }
        // Refused (a guard rule or another admin's change): back to the form, which shows the
        // message and the values that are really stored.
        return "redirect:/admin/users/" + id + "/edit";
    }
}
