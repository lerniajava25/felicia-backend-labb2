package se.backend.labb2.helpdesk.user;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String username);

    boolean existsByUsername(String username);

    /**
     * Every enabled user with the role, locked until the transaction ends (SELECT ... FOR
     * UPDATE). A second caller waits here until the first commits, and then only gets the
     * rows that still match. Ordered by id so every caller locks in the same order and two
     * callers can't deadlock each other.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.role = :role and u.enabled = true order by u.id")
    List<User> findAndLockEnabledByRole(@Param("role") Role role);

    List<User> findAllByOrderByUsernameAsc();
}
