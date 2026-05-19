package com.example.apicollector;

import com.example.controller.AuthService;
import com.example.controller.RestController;
import com.example.controller.StatsService;
import com.example.controller.UpdatesService;
import com.example.jsonparser.dto.AuthDtos;
import com.example.jsonparser.dto.ContractorDetailDto;
import com.example.jsonparser.dto.ContractorStatsDto;
import com.example.jsonparser.dto.InspectionHistoryDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RestControllerTest {

    private UpdatesService updatesService;
    private StatsService statsService;
    private AuthService authService;
    private RestController controller;

    @BeforeEach
    void setUp() {
        updatesService = mock(UpdatesService.class);
        statsService = mock(StatsService.class);
        authService = mock(AuthService.class);
        controller = new RestController(updatesService, statsService, authService);
    }

    @Test
    void authEndpointsDelegateToAuthService() {
        AuthDtos.AuthRequest request = new AuthDtos.AuthRequest("user", "secret1");
        AuthDtos.AuthResponse response = new AuthDtos.AuthResponse("user", "USER", "token");
        AuthDtos.UserDto user = new AuthDtos.UserDto("user", "USER");
        when(authService.register(request)).thenReturn(response);
        when(authService.login(request)).thenReturn(response);
        when(authService.requireUser("token")).thenReturn(user);

        assertThat(controller.register(request)).isSameAs(response);
        assertThat(controller.login(request)).isSameAs(response);
        assertThat(controller.me("token")).isSameAs(user);
    }

    @Test
    void updateEndpointsDelegateAndReturnOk() {
        assertThat(controller.updateAllData().getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(controller.updateToDate().getStatusCode()).isEqualTo(HttpStatus.OK);
        when(updatesService.getStatus()).thenReturn(Map.of("status", "idle"));

        assertThat(controller.getUpdateStatus()).containsEntry("status", "idle");
        verify(updatesService).updateAllData();
        verify(updatesService).updateToDate();
    }

    @Test
    void statsEndpointsRequireAuthWhereNeededAndDelegate() {
        ContractorStatsDto stats = new ContractorStatsDto("Alpha", 1, 0, 3, 1, 0.33);
        ContractorDetailDto detail = new ContractorDetailDto("Alpha", 1, 0, 3, 1, 0.33);
        InspectionHistoryDto history = new InspectionHistoryDto(1L, 2L, null, 1, 0, 1, "Loc", "Addr");
        when(statsService.getTopContractorsByViolations(5)).thenReturn(List.of(stats));
        when(statsService.findContractors("a", 3, 1.0, "rating", "asc", 10)).thenReturn(List.of(stats));
        when(statsService.getContractor("Alpha")).thenReturn(detail);
        when(statsService.getInspectionHistory("Alpha", 10)).thenReturn(List.of(history));

        assertThat(controller.getTopContractors(5)).containsExactly(stats);
        assertThat(controller.getContractors("token", "a", 3, 1.0, "rating", "asc", 10)).containsExactly(stats);
        assertThat(controller.getContractor("token", "Alpha")).isSameAs(detail);
        assertThat(controller.getContractorByQuery("token", "Alpha")).isSameAs(detail);
        assertThat(controller.getContractorHistory("token", "Alpha", 10)).containsExactly(history);
        assertThat(controller.getContractorHistoryByQuery("token", "Alpha", 10)).containsExactly(history);
        verify(authService, times(5)).requireUser("token");
    }

    @Test
    void adminEndpointsRequireAdminAndDelegate() {
        AuthDtos.UserDto admin = new AuthDtos.UserDto("admin", "ADMIN");
        when(authService.listUsers()).thenReturn(List.of(admin));

        assertThat(controller.users("admin-token")).containsExactly(admin);
        assertThat(controller.adminUpdateAll("admin-token").getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(controller.adminUpdate("admin-token").getStatusCode()).isEqualTo(HttpStatus.OK);

        verify(authService, times(3)).requireAdmin("admin-token");
        verify(updatesService).updateAllData();
        verify(updatesService).updateToDate();
    }

    @Test
    void localRuntimeHandlerReturnsErrorPayload() {
        assertThat(controller.handleRuntimeException(new RuntimeException()).getBody())
                .containsEntry("error", "RuntimeException")
                .containsEntry("message", "Unexpected error");
    }
}
