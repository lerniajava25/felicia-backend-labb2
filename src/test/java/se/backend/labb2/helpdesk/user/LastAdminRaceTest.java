package se.backend.labb2.helpdesk.user;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.support.TransactionTemplate;
import se.backend.labb2.helpdesk.TestcontainersConfiguration;
import se.backend.labb2.helpdesk.common.RuleViolationException;
import se.backend.labb2.helpdesk.security.CurrentUser;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;

/**
 * Two admins demote each other at the same moment. Without a lock both would count two
 * enabled admins and both demotions would go through, leaving none. This needs two
 * transactions that really commit, so the class is NOT @Transactional: it saves its own
 * users and deletes them afterwards.
 */
@SpringBootTest(properties = "app.seed.enabled=false")
@Import(TestcontainersConfiguration.class)
class LastAdminRaceTest {

    @Autowired
    private UserAdminService userAdminService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private User adminA;
    private User adminB;

    @BeforeEach
    void setUp() {
        // The only enabled admins: every other test rolls back its data.
        adminA = userRepository.save(new User("race-a", "unused-hash", "Race A", Role.ADMIN));
        adminB = userRepository.save(new User("race-b", "unused-hash", "Race B", Role.ADMIN));
    }

    @AfterEach
    void cleanUp() {
        userRepository.deleteAll(userRepository.findAllById(List.of(adminA.getId(), adminB.getId())));
    }

    @Test
    void secondDemotionWaitsForTheFirstAndIsRefused() throws Exception {
        CountDownLatch firstHoldsLock = new CountDownLatch(1);
        CountDownLatch secondIsWaiting = new CountDownLatch(1);
        ExecutorService threads = Executors.newFixedThreadPool(2);
        try {
            // 1: "A demotes B", doing what update() does, and keeping the lock until told.
            Future<?> first = threads.submit(() -> transactionTemplate.executeWithoutResult(status -> {
                userRepository.findAndLockEnabledByRole(Role.ADMIN);
                userRepository.findById(adminB.getId()).orElseThrow().changeRole(Role.AGENT);
                firstHoldsLock.countDown();
                await(secondIsWaiting);
            }));
            assertThat(firstHoldsLock.await(10, TimeUnit.SECONDS)).as("first took the lock").isTrue();

            // 2: "B demotes A" through the real service, while the first still holds the lock.
            Future<?> second = threads.submit(() -> {
                CurrentUser actingB = CurrentUser.from(adminB);
                SecurityContextHolder.getContext().setAuthentication(
                        UsernamePasswordAuthenticationToken.authenticated(actingB, null, actingB.getAuthorities()));
                try {
                    userAdminService.update(adminA.getId(), demote(adminA), actingB);
                } finally {
                    SecurityContextHolder.clearContext();
                }
            });

            // Only let the first commit once the second is really blocked on the lock.
            waitUntilATransactionWaitsForALock();
            secondIsWaiting.countDown();
            first.get(10, TimeUnit.SECONDS);

            assertThatThrownBy(() -> second.get(10, TimeUnit.SECONDS))
                    .isInstanceOf(ExecutionException.class)
                    .cause()
                    .isInstanceOf(RuleViolationException.class)
                    .hasMessage(UserAdminService.LAST_ADMIN_MESSAGE);
        } finally {
            secondIsWaiting.countDown();   // never leave the first thread hanging
            threads.shutdownNow();
        }

        User a = userRepository.findById(adminA.getId()).orElseThrow();
        User b = userRepository.findById(adminB.getId()).orElseThrow();
        assertThat(a.getRole()).isEqualTo(Role.ADMIN);
        assertThat(a.getEnabled()).isTrue();
        assertThat(b.getRole()).isEqualTo(Role.AGENT);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from users where role = 'ADMIN' and enabled = true", Integer.class))
                .as("enabled admins left")
                .isEqualTo(1);
    }

    private static EditUserForm demote(User admin) {
        EditUserForm form = new EditUserForm();
        form.setRole(Role.AGENT);
        form.setEnabled(true);
        form.setVersion(admin.getVersion());
        return form;
    }

    /** Polls PostgreSQL until some transaction is waiting for a row lock; fails after 10 s. */
    private void waitUntilATransactionWaitsForALock() throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            Integer waiting = jdbcTemplate.queryForObject(
                    "select count(*) from pg_stat_activity where wait_event_type = 'Lock' and datname = current_database()",
                    Integer.class);
            if (waiting != null && waiting > 0) {
                return;
            }
            Thread.sleep(50);
        }
        fail("The second demotion never waited for the lock, so the two ran at the same time");
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
