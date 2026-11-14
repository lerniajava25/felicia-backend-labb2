package se.backend.labb2.helpdesk.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import se.backend.labb2.helpdesk.TestcontainersConfiguration;
import se.backend.labb2.helpdesk.user.Role;
import se.backend.labb2.helpdesk.user.User;
import se.backend.labb2.helpdesk.user.UserRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
@Import({TestcontainersConfiguration.class, DbUserDetailsService.class, PasswordConfig.class})
class DbUserDetailsServiceTest {

    private static final String TEST_PASSWORD = "made-up-test-password";

    @Autowired
    private DbUserDetailsService userDetailsService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private User billy;

    @BeforeEach
    void setUp() {
        billy = userRepository.saveAndFlush(
                new User("billy", passwordEncoder.encode(TEST_PASSWORD), "Billy Test", Role.USER));
    }

    @Test
    void usernameIsTrimmedAndCaseInsensitive() {
        CurrentUser plain = (CurrentUser) userDetailsService.loadUserByUsername("billy");
        CurrentUser messy = (CurrentUser) userDetailsService.loadUserByUsername(" BILLY ");

        assertThat(plain.id()).isEqualTo(billy.getId());
        assertThat(messy.id()).isEqualTo(billy.getId());
        assertThat(messy.getUsername()).isEqualTo("billy");
        assertThat(messy.getPassword())
                .startsWith("{bcrypt}")
                .isEqualTo(billy.getPasswordHash());
    }

    @Test
    void unknownUsernameThrowsWithoutRevealingIt() {
        assertThatThrownBy(() -> userDetailsService.loadUserByUsername("nobody"))
                .isInstanceOf(UsernameNotFoundException.class)
                .message().doesNotContain("nobody");
    }

    @Test
    void userHasExactlyRoleUser() {
        UserDetails details = userDetailsService.loadUserByUsername("billy");

        assertThat(details.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_USER");
    }

    @Test
    void disabledUserLoadsAsDisabled() {
        billy.setEnabled(false);
        userRepository.saveAndFlush(billy);

        assertThat(userDetailsService.loadUserByUsername("billy").isEnabled()).isFalse();
    }
}
