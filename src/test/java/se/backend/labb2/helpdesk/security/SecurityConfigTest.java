package se.backend.labb2.helpdesk.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import se.backend.labb2.helpdesk.TestcontainersConfiguration;
import se.backend.labb2.helpdesk.user.Role;
import se.backend.labb2.helpdesk.user.User;
import se.backend.labb2.helpdesk.user.UserRepository;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "app.seed.enabled=false")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class SecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Test
    void stylesheetLoadsWhenLoggedOut() throws Exception {
        mockMvc.perform(get("/css/app.css"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/css"));
    }

    @Test
    void homeRedirectsToLoginWhenLoggedOut() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    @Transactional   // the saved user is rolled back afterwards
    void homeUsesOwnStylesheetAndNoCdn() throws Exception {
        // The nav shows displayName, so the principal must be a CurrentUser (as in the real app),
        // and one that exists: CurrentUserRefreshFilter logs out a user it can't find.
        User saved = userRepository.save(new User("test", "unused-hash", "Test User", Role.USER));

        mockMvc.perform(get("/").with(user(CurrentUser.from(saved))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("href=\"/css/app.css\"")))
                .andExpect(content().string(not(containsString("cdn."))));
    }
}
