package se.backend.labb2.helpdesk.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.event.AuthenticationFailureBadCredentialsEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import se.backend.labb2.helpdesk.TestcontainersConfiguration;
import se.backend.labb2.helpdesk.user.Role;
import se.backend.labb2.helpdesk.user.User;
import se.backend.labb2.helpdesk.user.UserRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;

@SpringBootTest(properties = "app.seed.enabled=false")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
@RecordApplicationEvents
@ExtendWith(OutputCaptureExtension.class)
class LoginAuditTest {

    private static final String TEST_PASSWORD = "made-up-test-password";
    private static final String WRONG_PASSWORD = "made-up-wrong-password";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ApplicationEvents events;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        userRepository.saveAndFlush(
                new User("billy", passwordEncoder.encode(TEST_PASSWORD), "Billy Test", Role.USER));
    }

    @Test
    void wrongPasswordPublishesExactlyOneFailureEvent() throws Exception {
        mockMvc.perform(formLogin("/login").user("billy").password(WRONG_PASSWORD));

        assertThat(events.stream(AuthenticationFailureBadCredentialsEvent.class).count()).isEqualTo(1);
    }

    @Test
    void correctPasswordPublishesExactlyOneSuccessEvent() throws Exception {
        mockMvc.perform(formLogin("/login").user("billy").password(TEST_PASSWORD));

        assertThat(events.stream(AuthenticationSuccessEvent.class).count()).isEqualTo(1);
    }

    @Test
    void failedLoginLogLineHasNoLineBreaksAndNoPassword(CapturedOutput output) throws Exception {
        mockMvc.perform(formLogin("/login").user("evil\nFAKE ENTRY").password(WRONG_PASSWORD));

        assertThat(output.getOut()).contains("Failed login for user 'evilFAKE ENTRY'");
        assertThat(output.getAll()).doesNotContain(WRONG_PASSWORD);
    }
}
