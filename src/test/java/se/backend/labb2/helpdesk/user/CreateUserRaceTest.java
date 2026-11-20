package se.backend.labb2.helpdesk.user;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import se.backend.labb2.helpdesk.TestcontainersConfiguration;
import se.backend.labb2.helpdesk.security.CurrentUser;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * Two admins create the same username at the same time: both pass existsByUsername, and the
 * database's unique constraint stops the second INSERT. That needs two committed transactions,
 * which a @Transactional MockMvc test can't produce, so the service is mocked to throw what
 * the database throws. UserRepositoryTest.duplicateUsernameIsRejected shows the database
 * really does; this shows the admin gets a form error for it, not a 500.
 */
@SpringBootTest(properties = "app.seed.enabled=false")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class CreateUserRaceTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserAdminService userAdminService;

    @Autowired
    private UserRepository userRepository;

    @Test
    @Transactional   // only the saved admin; the duplicate itself still comes from the mock
    void uniqueConstraintBecomesTheSameFormError() throws Exception {
        // Must exist in the database: CurrentUserRefreshFilter logs out a user it can't find.
        CurrentUser admin = CurrentUser.from(userRepository.save(new User("admin", "unused-hash", "Admin Test", Role.ADMIN)));
        doThrow(new DataIntegrityViolationException("duplicate key value violates unique constraint"))
                .when(userAdminService).create(any(NewUserForm.class));

        mockMvc.perform(post("/admin/users")
                        .param("username", "twin")
                        .param("displayName", "Twin")
                        .param("role", "USER")
                        .param("password", "a-long-and-valid-password")
                        .with(user(admin))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/new-user"))
                .andExpect(model().attributeHasFieldErrorCode("form", "username", "taken"))
                .andExpect(content().string(containsString(AdminUserController.USERNAME_TAKEN_MESSAGE)));
    }
}
