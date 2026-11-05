package se.backend.labb2.helpdesk.user;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import se.backend.labb2.helpdesk.TestcontainersConfiguration;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
@Import(TestcontainersConfiguration.class)
class UserRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Test
    void duplicateUsernameIsRejected() {
        userRepository.saveAndFlush(new User("kalle", "{noop}x", "Kalle Anka", Role.USER));

        assertThatThrownBy(() ->
                userRepository.saveAndFlush(new User("kalle", "{noop}y", "Another Kalle", Role.USER)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
