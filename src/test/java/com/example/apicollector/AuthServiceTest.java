package com.example.apicollector;

import com.example.apiparser.InspectionApiClient;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthServiceTest {

    private UserRepository userRepository;
    private InspectionApiClient inspectionApiClient;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        inspectionApiClient = mock(InspectionApiClient.class);
        authService = new AuthService(userRepository, inspectionApiClient);
    }

    @Test
    void registerCreatesFirstUserAsAdminAndNormalizesUsername() {
        when(userRepository.existsById("admin.user")).thenReturn(false);
        when(userRepository.count()).thenReturn(0L);

        AuthDtos.AuthResponse response = authService.register(new AuthDtos.AuthRequest(" Admin.User ", "secret1", null));

        assertThat(response.username()).isEqualTo("admin.user");
        assertThat(response.role()).isEqualTo("ADMIN");
        assertThat(response.token()).isNotBlank();
        assertThat(authService.requireUser(" " + response.token() + " ").username()).isEqualTo("admin.user");
        verify(userRepository).save(any(UserEntity.class));
        verify(inspectionApiClient, never()).checkApiAvailability(any());
    }

    @Test
    void registerRejectsDuplicateAndInvalidInput() {
        when(userRepository.existsById("user")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(new AuthDtos.AuthRequest("user", "secret1", "api-secret")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("User already exists");
        assertThatThrownBy(() -> authService.register(new AuthDtos.AuthRequest("x", "secret1", null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Username length must be between 3 and 64");
        assertThatThrownBy(() -> authService.register(new AuthDtos.AuthRequest("bad name", "secret1", null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Username can contain latin letters, digits, dot, dash and underscore");
        assertThatThrownBy(() -> authService.register(new AuthDtos.AuthRequest("valid", "12345", null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Password length must be at least 6");
    }

    @Test
    void loginCreatesSessionForValidPasswordAndRejectsInvalidCredentials() {
        UserEntity user = new UserEntity("user", "hash", "salt", "USER");
        AuthService setupService = new AuthService(userRepository, inspectionApiClient);
        when(userRepository.existsById("user")).thenReturn(false);
        when(userRepository.count()).thenReturn(1L);
        AuthDtos.AuthResponse registered = setupService.register(new AuthDtos.AuthRequest("user", null, "api-secret"));

        verify(userRepository).save(any(UserEntity.class));
        UserEntity saved = new UserEntity("user", registered.token(), "salt", "USER");
        assertThat(saved.getUsername()).isEqualTo(user.getUsername());

        UserEntity stored = captureStoredUserForApiKey("user", "api-secret");
        when(userRepository.findById("user")).thenReturn(Optional.of(stored));
        reset(inspectionApiClient);

        AuthDtos.AuthResponse response = authService.login(new AuthDtos.AuthRequest(" USER ", null, "api-secret"));

        assertThat(response.username()).isEqualTo("user");
        assertThat(authService.requireUser(response.token()).role()).isEqualTo("USER");
        assertThat(authService.requireUserApiKey(response.token())).isEqualTo("api-secret");
        verify(inspectionApiClient).checkApiAvailability("api-secret");

        assertThatThrownBy(() -> authService.login(new AuthDtos.AuthRequest("user", null, "wrong-api-key")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid username or password");

        when(userRepository.findById("missing")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> authService.login(new AuthDtos.AuthRequest("missing", null, "api-secret")))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("User was not found");
    }

    @Test
    void requireAdminAndListUsersUseStoredSessionsAndRepository() {
        when(userRepository.existsById("admin")).thenReturn(false);
        when(userRepository.count()).thenReturn(0L);
        AuthDtos.AuthResponse admin = authService.register(new AuthDtos.AuthRequest("admin", "secret1", null));

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

    @Test
    void userRegistrationValidatesExternalApiKeyBeforeSaving() {
        when(userRepository.existsById("user")).thenReturn(false);
        when(userRepository.count()).thenReturn(1L);

        AuthDtos.AuthResponse response = authService.register(new AuthDtos.AuthRequest("user", null, "api-secret"));

        assertThat(response.role()).isEqualTo("USER");
        verify(inspectionApiClient).checkApiAvailability("api-secret");
        verify(userRepository).save(any(UserEntity.class));
    }

    private UserEntity captureStoredUserForApiKey(String username, String apiKey) {
        UserRepository repository = mock(UserRepository.class);
        InspectionApiClient apiClient = mock(InspectionApiClient.class);
        AuthService service = new AuthService(repository, apiClient);
        when(repository.existsById(username)).thenReturn(false);
        when(repository.count()).thenReturn(1L);
        service.register(new AuthDtos.AuthRequest(username, null, apiKey));
        org.mockito.ArgumentCaptor<UserEntity> captor = org.mockito.ArgumentCaptor.forClass(UserEntity.class);
        verify(repository).save(captor.capture());
        return captor.getValue();
    }
}
