package com.userservice.service;

import com.userservice.dao.UserDao;
import com.userservice.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link UserServiceImpl}.
 * <p>
 * The DAO collaborator is mocked with Mockito so these tests never touch a
 * real database. A fresh mock is injected per test by {@link MockitoExtension}.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("UserServiceImpl")
class UserServiceImplTest {

    @Mock
    private UserDao userDao;

    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserServiceImpl(userDao);
    }

    // =========================================================================
    // createUser
    // =========================================================================

    @Nested
    @DisplayName("createUser()")
    class CreateUser {

        @Test
        @DisplayName("valid data → saves user and returns it with assigned id")
        void validData_savesAndReturnsUser() {
            when(userDao.existsByEmail("alice@example.com")).thenReturn(false);
            when(userDao.save(any(User.class))).thenAnswer(invocation -> {
                User u = invocation.getArgument(0);
                u.setId(1L);
                return u;
            });

            User result = userService.createUser("Alice", "alice@example.com", 30);

            assertThat(result.getId()).isEqualTo(1L);
            assertThat(result.getName()).isEqualTo("Alice");
            assertThat(result.getEmail()).isEqualTo("alice@example.com");
            assertThat(result.getAge()).isEqualTo(30);

            ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
            verify(userDao).save(captor.capture());
            assertThat(captor.getValue().getEmail()).isEqualTo("alice@example.com");
        }

        @Test
        @DisplayName("duplicate email → throws UserValidationException, DAO.save never called")
        void duplicateEmail_throwsAndDoesNotSave() {
            when(userDao.existsByEmail("dup@example.com")).thenReturn(true);

            assertThatThrownBy(() -> userService.createUser("Bob", "dup@example.com", 25))
                    .isInstanceOf(UserValidationException.class)
                    .hasMessageContaining("dup@example.com");

            verify(userDao, never()).save(any());
        }

        @Test
        @DisplayName("blank name → throws before touching DAO")
        void blankName_throwsWithoutTouchingDao() {
            assertThatThrownBy(() -> userService.createUser("  ", "x@example.com", 25))
                    .isInstanceOf(UserValidationException.class)
                    .hasMessageContaining("Имя");

            verify(userDao, never()).existsByEmail(any());
            verify(userDao, never()).save(any());
        }

        @Test
        @DisplayName("null name → throws UserValidationException")
        void nullName_throws() {
            assertThatThrownBy(() -> userService.createUser(null, "x@example.com", 25))
                    .isInstanceOf(UserValidationException.class)
                    .hasMessageContaining("Имя");

            verify(userDao, never()).save(any());
        }

        @ParameterizedTest(name = "bad email=''{0}''")
        @ValueSource(strings = {"not-an-email", "", "no-at-sign", "missing@", "@nodomain", "spaces in@email.com"})
        @DisplayName("invalid email formats → throws UserValidationException")
        void invalidEmail_throws(String badEmail) {
            assertThatThrownBy(() -> userService.createUser("Bob", badEmail, 25))
                    .isInstanceOf(UserValidationException.class)
                    .hasMessageContaining("email");

            verify(userDao, never()).save(any());
        }

        @Test
        @DisplayName("null email → throws UserValidationException")
        void nullEmail_throws() {
            assertThatThrownBy(() -> userService.createUser("Bob", null, 25))
                    .isInstanceOf(UserValidationException.class)
                    .hasMessageContaining("email");

            verify(userDao, never()).save(any());
        }

        @Test
        @DisplayName("negative age → throws UserValidationException")
        void negativeAge_throws() {
            assertThatThrownBy(() -> userService.createUser("Bob", "bob@example.com", -1))
                    .isInstanceOf(UserValidationException.class)
                    .hasMessageContaining("Возраст");

            verify(userDao, never()).save(any());
        }

        @Test
        @DisplayName("age > 150 → throws UserValidationException")
        void tooLargeAge_throws() {
            assertThatThrownBy(() -> userService.createUser("Bob", "bob@example.com", 151))
                    .isInstanceOf(UserValidationException.class)
                    .hasMessageContaining("Возраст");

            verify(userDao, never()).save(any());
        }

        @Test
        @DisplayName("age = 0 (boundary) → valid, saves successfully")
        void ageBoundaryZero_valid() {
            when(userDao.existsByEmail("young@example.com")).thenReturn(false);
            when(userDao.save(any())).thenAnswer(inv -> {
                User u = inv.getArgument(0);
                u.setId(1L);
                return u;
            });

            User result = userService.createUser("Young", "young@example.com", 0);

            assertThat(result.getAge()).isZero();
            verify(userDao).save(any());
        }

        @Test
        @DisplayName("age = 150 (boundary) → valid, saves successfully")
        void ageBoundaryMax_valid() {
            when(userDao.existsByEmail("old@example.com")).thenReturn(false);
            when(userDao.save(any())).thenAnswer(inv -> {
                User u = inv.getArgument(0);
                u.setId(1L);
                return u;
            });

            User result = userService.createUser("Old", "old@example.com", 150);

            assertThat(result.getAge()).isEqualTo(150);
        }
    }

    // =========================================================================
    // getUserById
    // =========================================================================

    @Nested
    @DisplayName("getUserById()")
    class GetUserById {

        @Test
        @DisplayName("existing id → returns Optional with user")
        void existingId_returnsUser() {
            User user = new User("Carol", "carol@example.com", 22);
            user.setId(5L);
            when(userDao.findById(5L)).thenReturn(Optional.of(user));

            Optional<User> result = userService.getUserById(5L);

            assertThat(result).contains(user);
        }

        @Test
        @DisplayName("null id → throws without calling DAO")
        void nullId_throwsWithoutCallingDao() {
            assertThatThrownBy(() -> userService.getUserById(null))
                    .isInstanceOf(UserValidationException.class);

            verify(userDao, never()).findById(anyLong());
        }

        @Test
        @DisplayName("unknown id → returns empty Optional")
        void unknownId_returnsEmpty() {
            when(userDao.findById(404L)).thenReturn(Optional.empty());

            assertThat(userService.getUserById(404L)).isEmpty();
        }
    }

    // =========================================================================
    // getAllUsers
    // =========================================================================

    @Nested
    @DisplayName("getAllUsers()")
    class GetAllUsers {

        @Test
        @DisplayName("delegates to DAO and returns its result")
        void delegatesToDao() {
            List<User> users = List.of(new User("A", "a@example.com", 20));
            when(userDao.findAll()).thenReturn(users);

            List<User> result = userService.getAllUsers();

            assertThat(result).isEqualTo(users);
            verify(userDao, times(1)).findAll();
        }

        @Test
        @DisplayName("empty table → returns empty list (not null)")
        void emptyTable_returnsEmptyList() {
            when(userDao.findAll()).thenReturn(Collections.emptyList());

            List<User> result = userService.getAllUsers();

            assertThat(result).isNotNull().isEmpty();
        }
    }

    // =========================================================================
    // updateUser
    // =========================================================================

    @Nested
    @DisplayName("updateUser()")
    class UpdateUser {

        @Test
        @DisplayName("partial update: only name provided → only name changes")
        void partialUpdate_onlyNameChanges() {
            User existing = new User("OldName", "old@example.com", 40);
            existing.setId(10L);
            when(userDao.findById(10L)).thenReturn(Optional.of(existing));
            when(userDao.update(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

            User updated = userService.updateUser(10L, "NewName", null, null);

            assertThat(updated.getName()).isEqualTo("NewName");
            assertThat(updated.getEmail()).isEqualTo("old@example.com");
            assertThat(updated.getAge()).isEqualTo(40);
            verify(userDao, never()).existsByEmail(any());
        }

        @Test
        @DisplayName("non-existing user → throws with id in message, update never called")
        void nonExistingUser_throws() {
            when(userDao.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> userService.updateUser(99L, "Name", null, null))
                    .isInstanceOf(UserValidationException.class)
                    .hasMessageContaining("99");

            verify(userDao, never()).update(any());
        }

        @Test
        @DisplayName("email changed to already taken email → throws, update never called")
        void emailChangedToExistingEmail_throws() {
            User existing = new User("Name", "current@example.com", 30);
            existing.setId(7L);
            when(userDao.findById(7L)).thenReturn(Optional.of(existing));
            when(userDao.existsByEmail("taken@example.com")).thenReturn(true);

            assertThatThrownBy(() -> userService.updateUser(7L, null, "taken@example.com", null))
                    .isInstanceOf(UserValidationException.class)
                    .hasMessageContaining("taken@example.com");

            verify(userDao, never()).update(any());
        }

        @Test
        @DisplayName("email unchanged → existsByEmail never called")
        void emailUnchanged_doesNotCheckExistence() {
            User existing = new User("Name", "same@example.com", 30);
            existing.setId(8L);
            when(userDao.findById(8L)).thenReturn(Optional.of(existing));
            when(userDao.update(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

            userService.updateUser(8L, null, "same@example.com", null);

            verify(userDao, never()).existsByEmail(anyString());
            verify(userDao).update(any(User.class));
        }

        @Test
        @DisplayName("invalid age in update → throws before calling update")
        void invalidAge_throwsBeforeCallingUpdate() {
            User existing = new User("Name", "name@example.com", 30);
            existing.setId(11L);
            when(userDao.findById(11L)).thenReturn(Optional.of(existing));

            assertThatThrownBy(() -> userService.updateUser(11L, null, null, -5))
                    .isInstanceOf(UserValidationException.class)
                    .hasMessageContaining("Возраст");

            verify(userDao, never()).update(any());
        }

        @Test
        @DisplayName("null id → throws without calling DAO")
        void nullId_throwsWithoutCallingDao() {
            assertThatThrownBy(() -> userService.updateUser(null, "Name", null, null))
                    .isInstanceOf(UserValidationException.class);

            verify(userDao, never()).findById(any());
            verify(userDao, never()).update(any());
        }

        @Test
        @DisplayName("all fields null/blank → no changes, update still called")
        void allFieldsNullOrBlank_updateCalledWithUnchangedEntity() {
            User existing = new User("Name", "name@example.com", 30);
            existing.setId(12L);
            when(userDao.findById(12L)).thenReturn(Optional.of(existing));
            when(userDao.update(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

            User result = userService.updateUser(12L, null, null, null);

            assertThat(result.getName()).isEqualTo("Name");
            assertThat(result.getEmail()).isEqualTo("name@example.com");
            assertThat(result.getAge()).isEqualTo(30);
            verify(userDao).update(any());
        }

        @Test
        @DisplayName("invalid email format in update → throws before calling update")
        void invalidEmailFormat_throwsBeforeCallingUpdate() {
            User existing = new User("Name", "name@example.com", 30);
            existing.setId(13L);
            when(userDao.findById(13L)).thenReturn(Optional.of(existing));

            assertThatThrownBy(() -> userService.updateUser(13L, null, "not-valid-email", null))
                    .isInstanceOf(UserValidationException.class)
                    .hasMessageContaining("email");

            verify(userDao, never()).update(any());
        }
    }

    // =========================================================================
    // deleteUser
    // =========================================================================

    @Nested
    @DisplayName("deleteUser()")
    class DeleteUser {

        @Test
        @DisplayName("existing id → returns true")
        void existingId_returnsTrue() {
            when(userDao.delete(3L)).thenReturn(true);

            assertThat(userService.deleteUser(3L)).isTrue();
            verify(userDao).delete(3L);
        }

        @Test
        @DisplayName("non-existing id → returns false")
        void nonExistingId_returnsFalse() {
            when(userDao.delete(eq(404L))).thenReturn(false);

            assertThat(userService.deleteUser(404L)).isFalse();
        }

        @Test
        @DisplayName("null id → throws without calling DAO")
        void nullId_throwsWithoutCallingDao() {
            assertThatThrownBy(() -> userService.deleteUser(null))
                    .isInstanceOf(UserValidationException.class);

            verify(userDao, never()).delete(any());
        }
    }
}
