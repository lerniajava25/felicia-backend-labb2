package se.backend.labb2.helpdesk.security;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class LoginController {

    // currentUser is null for anonymous visitors (their principal is not a CurrentUser).
    @GetMapping("/login")
    public String login(@AuthenticationPrincipal CurrentUser currentUser) {
        return currentUser != null ? "redirect:/" : "login";
    }
}
