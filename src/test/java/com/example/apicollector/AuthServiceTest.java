package com.example.apicollector;

import com.example.controller.AuthService;
import com.example.db.entities.UserEntity;
import com.example.db.repository.UserRepository;
import com.example.exception.NotFoundException;
import com.example.jsonparser.dto.AuthDtos;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthServiceTest {

    private UserRepository userRepository;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        authService = new AuthService(userRepository);
    }

    @Test
    void registerCreatesFirstUserAsAdminAndNormalizesUsername() {
        when(userRepository.existsById("admin.user")).thenReturn(false);
        when(userRepository.count()).thenReturn(0L);

        AuthDtos.AuthResponse response = authService.register(new AuthDtos.AuthRequest(" Admin.User ", "secret1"));

        assertThat(response.username()).isEqualTo("admin.user");
        assertThat(response.role()).isEqualTo("ADMIN");
        assertThat(response.token()).isNotBlank();
        assertThat(authService.requireUser(" " + response.token() + " ").username()).isEqualTo("admin.user");
        verify(userRepository).save(any(UserEntity.class));
    }

    @Test
    void registerRejectsDuplicateAndInvalidInput() {
        when(userRepository.existsById("user")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(new AuthDtos.AuthRequest("user", "secret1")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("User already exists");
        assertThatThrownBy(() -> authService.register(new AuthDtos.AuthRequest("x", "secret1")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Username length must be between 3 and 64");
        assertThatThrownBy(() -> authService.register(new AuthDtos.AuthRequest("bad name", "secret1")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Username can contain latin letters, digits, dot, dash and underscore");
        assertThatThrownBy(() -> authService.register(new AuthDtos.AuthRequest("valid", "12345")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Password length must be at least 6");
    }

    @Test
    void loginCreatesSessionForValidPasswordAndRejectsInvalidCredentials() {
        UserEntity user = new UserEntity("user", "hash", "salt", "USER");
        AuthService setupService = new AuthService(userRepository);
        when(userRepository.existsById("user")).thenReturn(false);
        when(userRepository.count()).thenReturn(1L);
        AuthDtos.AuthResponse registered = setupService.register(new AuthDtos.AuthRequest("user", "secret1"));

        verify(userRepository).save(any(UserEntity.class));
        UserEntity saved = new UserEntity("user", registered.token(), "salt", "USER");
        assertThat(saved.getUsername()).isEqualTo(user.getUsername());

        UserEntity stored = captureStoredUserForPassword("user", "secret1");
        when(userRepository.findById("user")).thenReturn(Optional.of(stored));

        AuthDtos.AuthResponse response = authService.login(new AuthDtos.AuthRequest(" USER ", "secret1"));

        assertThat(response.username()).isEqualTo("user");
        assertThat(authService.requireUser(response.token()).role()).isEqualTo("USER");

        assertThatThrownBy(() -> authService.login(new AuthDtos.AuthRequest("user", "wrong1")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid username or password");

        when(userRepository.findById("missing")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> authService.login(new AuthDtos.AuthRequest("missing", "secret1")))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("User was not found");
    }

    @Test
    void requireAdminAndListUsersUseStoredSessionsAndRepository() {
        when(userRepository.existsById("admin")).thenReturn(false);
        when(userRepository.count()).thenReturn(0L);
        AuthDtos.AuthResponse admin = authService.register(new AuthDtos.AuthRequest("admin", "secret1"));

        when(userRepository.findAll()).thenReturn(List.of(
                new UserEntity("admin", "hash", "salt", "ADMIN"),
                new UserEntity("user", "hash", "salt", "USER")
        ));

        authService.requireAdmin(admin.token());
        assertThat(authService.listUsers())
                .extracting(AuthDtos.UserDto::username)
                .containsExactly("admin", "user");

        assertThatThrownBy(() -> authService.requireUser("missing"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Authentication token is missing or invalid");
    }

    private UserEntity captureStoredUserForPassword(String username, String password) {
        UserRepository repository = mock(UserRepository.class);
        AuthService service = new AuthService(repository);
        when(repository.existsById(username)).thenReturn(false);
        when(repository.count()).thenReturn(1L);
        service.register(new AuthDtos.AuthRequest(username, password));
        org.mockito.ArgumentCaptor<UserEntity> captor = org.mockito.ArgumentCaptor.forClass(UserEntity.class);
        verify(repository).save(captor.capture());
        return captor.getValue();
    }
}
