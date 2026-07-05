package com.userservice.dao;

import com.userservice.entity.User;
import com.userservice.util.TestHibernateUtil;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.*;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Integration tests for {@link UserDaoImpl} backed by a real PostgreSQL
 * instance started via Testcontainers.
 * <p>
 * The container is shared across all tests in this class (static field) to
 * avoid the cost of spinning it up per test. Isolation is achieved by
 * truncating the {@code users} table in {@link BeforeEach}.
 */
@Testcontainers
@DisplayName("UserDaoImpl (integration)")
class UserDaoImplIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("userdb_test")
                    .withUsername("test")
                    .withPassword("test");

    private static SessionFactory sessionFactory;
    private UserDao userDao;

    @BeforeAll
    static void setUpSessionFactory() {
        sessionFactory = TestHibernateUtil.buildSessionFactory(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    @AfterAll
    static void tearDownSessionFactory() {
        if (sessionFactory != null) {
            sessionFactory.close();
        }
    }

    @BeforeEach
    void setUp() {
        userDao = new UserDaoImpl(sessionFactory);
        cleanTable();
    }

    private void cleanTable() {
        try (var session = sessionFactory.openSession()) {
            var tx = session.beginTransaction();
            session.createMutationQuery("DELETE FROM User").executeUpdate();
            tx.commit();
        }
    }

    // =========================================================================
    // save
    // =========================================================================

    @Nested
    @DisplayName("save()")
    class Save {

        @Test
        @DisplayName("valid user → persists and returns with generated id and createdAt")
        void persistsUserAndAssignsIdAndCreatedAt() {
            LocalDateTime before = LocalDateTime.now().minusSeconds(1);
            User user = new User("Alice", "alice@example.com", 30);

            User saved = userDao.save(user);

            assertThat(saved.getId()).isNotNull().isPositive();
            assertThat(saved.getCreatedAt()).isNotNull().isAfter(before);
            assertThat(saved.getName()).isEqualTo("Alice");
            assertThat(saved.getEmail()).isEqualTo("alice@example.com");
            assertThat(saved.getAge()).isEqualTo(30);
        }

        @Test
        @DisplayName("duplicate email → throws DaoException, table stays at 1 row")
        void duplicateEmail_throwsDaoExceptionAndDoesNotPersist() {
            userDao.save(new User("Alice", "dup@example.com", 30));

            assertThrows(DaoException.class,
                    () -> userDao.save(new User("Bob", "dup@example.com", 25)));

            assertThat(userDao.findAll()).hasSize(1);
        }

        @Test
        @DisplayName("multiple users with different emails → all persist")
        void multipleUsers_differentEmails_allPersist() {
            userDao.save(new User("User1", "u1@example.com", 20));
            userDao.save(new User("User2", "u2@example.com", 21));
            userDao.save(new User("User3", "u3@example.com", 22));

            assertThat(userDao.findAll()).hasSize(3);
        }
    }

    // =========================================================================
    // findById
    // =========================================================================

    @Nested
    @DisplayName("findById()")
    class FindById {

        @Test
        @DisplayName("existing user → returns non-empty Optional with correct data")
        void existingUser_returnsUser() {
            User saved = userDao.save(new User("Carol", "carol@example.com", 22));

            Optional<User> found = userDao.findById(saved.getId());

            assertThat(found).isPresent();
            assertThat(found.get().getEmail()).isEqualTo("carol@example.com");
            assertThat(found.get().getName()).isEqualTo("Carol");
            assertThat(found.get().getAge()).isEqualTo(22);
        }

        @Test
        @DisplayName("non-existing id → returns empty Optional")
        void nonExistingId_returnsEmpty() {
            Optional<User> found = userDao.findById(999_999L);

            assertThat(found).isEmpty();
        }
    }

    // =========================================================================
    // findAll
    // =========================================================================

    @Nested
    @DisplayName("findAll()")
    class FindAll {

        @Test
        @DisplayName("populated table → returns all persisted users")
        void returnsAllPersistedUsers() {
            userDao.save(new User("Dave", "dave@example.com", 40));
            userDao.save(new User("Eve", "eve@example.com", 35));

            List<User> all = userDao.findAll();

            assertThat(all).hasSize(2)
                    .extracting(User::getEmail)
                    .containsExactlyInAnyOrder("dave@example.com", "eve@example.com");
        }

        @Test
        @DisplayName("empty table → returns empty list (not null)")
        void emptyTable_returnsEmptyList() {
            List<User> all = userDao.findAll();

            assertThat(all).isNotNull().isEmpty();
        }

        @Test
        @DisplayName("results are ordered by id ascending")
        void resultsOrderedById() {
            userDao.save(new User("First", "first@example.com", 20));
            userDao.save(new User("Second", "second@example.com", 21));
            userDao.save(new User("Third", "third@example.com", 22));

            List<User> all = userDao.findAll();

            assertThat(all).extracting(User::getId)
                    .isSortedAccordingTo(Long::compareTo);
        }
    }

    // =========================================================================
    // update
    // =========================================================================

    @Nested
    @DisplayName("update()")
    class Update {

        @Test
        @DisplayName("existing user → persists name and age changes, reloaded data matches")
        void existingUser_persistsChanges() {
            User saved = userDao.save(new User("Frank", "frank@example.com", 50));
            saved.setName("Franklin");
            saved.setAge(51);

            User updated = userDao.update(saved);

            assertThat(updated.getName()).isEqualTo("Franklin");
            assertThat(updated.getAge()).isEqualTo(51);

            // Verify the change is visible in a separate read
            Optional<User> reloaded = userDao.findById(saved.getId());
            assertThat(reloaded).isPresent();
            assertThat(reloaded.get().getName()).isEqualTo("Franklin");
            assertThat(reloaded.get().getAge()).isEqualTo(51);
        }

        @Test
        @DisplayName("update email to taken email → throws DaoException")
        void emailCollision_throwsDaoException() {
            userDao.save(new User("Grace", "grace@example.com", 28));
            User other = userDao.save(new User("Heidi", "heidi@example.com", 29));

            other.setEmail("grace@example.com");

            assertThrows(DaoException.class, () -> userDao.update(other));
        }

        @Test
        @DisplayName("createdAt is not changed by update (updatable=false)")
        void createdAt_notChangedOnUpdate() {
            User saved = userDao.save(new User("Karen", "karen@example.com", 35));
            LocalDateTime originalCreatedAt = userDao.findById(saved.getId())
                    .map(User::getCreatedAt)
                    .orElseThrow();

            saved.setName("Karen Updated");
            userDao.update(saved);

            LocalDateTime afterUpdate = userDao.findById(saved.getId())
                    .map(User::getCreatedAt)
                    .orElseThrow();

            assertThat(afterUpdate).isEqualTo(originalCreatedAt);
        }
    }

    // =========================================================================
    // delete
    // =========================================================================

    @Nested
    @DisplayName("delete()")
    class Delete {

        @Test
        @DisplayName("existing user → returns true and user no longer findable")
        void existingUser_removesItAndReturnsTrue() {
            User saved = userDao.save(new User("Ivan", "ivan@example.com", 33));

            boolean deleted = userDao.delete(saved.getId());

            assertThat(deleted).isTrue();
            assertThat(userDao.findById(saved.getId())).isEmpty();
        }

        @Test
        @DisplayName("non-existing id → returns false")
        void nonExistingId_returnsFalse() {
            assertThat(userDao.delete(999_999L)).isFalse();
        }

        @Test
        @DisplayName("delete one of several users → only that row removed")
        void deleteOneOfSeveral_onlyThatRowRemoved() {
            User first = userDao.save(new User("L1", "l1@example.com", 20));
            userDao.save(new User("L2", "l2@example.com", 21));

            userDao.delete(first.getId());

            List<User> remaining = userDao.findAll();
            assertThat(remaining).hasSize(1)
                    .extracting(User::getEmail)
                    .containsExactly("l2@example.com");
        }
    }

    // =========================================================================
    // existsByEmail
    // =========================================================================

    @Nested
    @DisplayName("existsByEmail()")
    class ExistsByEmail {

        @Test
        @DisplayName("known email → returns true")
        void knownEmail_returnsTrue() {
            userDao.save(new User("Judy", "judy@example.com", 27));

            assertThat(userDao.existsByEmail("judy@example.com")).isTrue();
        }

        @Test
        @DisplayName("unknown email → returns false")
        void unknownEmail_returnsFalse() {
            assertThat(userDao.existsByEmail("ghost@example.com")).isFalse();
        }

        @Test
        @DisplayName("empty table → always returns false")
        void emptyTable_returnsFalse() {
            assertThat(userDao.existsByEmail("any@example.com")).isFalse();
        }

        @Test
        @DisplayName("case-sensitive: different case → returns false")
        void caseSensitive_differentCase_returnsFalse() {
            userDao.save(new User("Mike", "mike@example.com", 25));

            assertThat(userDao.existsByEmail("MIKE@EXAMPLE.COM")).isFalse();
        }
    }
}
