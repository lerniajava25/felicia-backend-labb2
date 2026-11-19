package se.backend.labb2.helpdesk.user;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/** Admin pages for users. Access is checked by the /admin/** URL rule and @PreAuthorize in the service. */
@Controller
public class AdminUserController {

    private final UserAdminService userAdminService;

    public AdminUserController(UserAdminService userAdminService) {
        this.userAdminService = userAdminService;
    }

    @GetMapping("/admin/users")
    public String list(Model model) {
        model.addAttribute("users", userAdminService.list());
        return "admin/users";
    }
}
