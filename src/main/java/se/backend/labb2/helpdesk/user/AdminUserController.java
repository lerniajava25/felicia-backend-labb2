package se.backend.labb2.helpdesk.user;

import jakarta.validation.Valid;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

/** Admin pages for users. Access is checked by the /admin/** URL rule and @PreAuthorize in the service. */
@Controller
public class AdminUserController {

    static final String USERNAME_TAKEN_MESSAGE = "That username is taken";

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
}
