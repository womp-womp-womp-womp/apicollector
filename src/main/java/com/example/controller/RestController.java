package com.example.controller;

import com.example.jsonparser.dto.AuthDtos;
import com.example.jsonparser.dto.ContractorDetailDto;
import com.example.jsonparser.dto.ContractorStatsDto;
import com.example.jsonparser.dto.InspectionHistoryDto;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Map;

@org.springframework.web.bind.annotation.RestController
public class RestController {

    private final UpdatesService updatesService;
    private final StatsService statsService;
    private final AuthService authService;

    public RestController(UpdatesService updatesService, StatsService statsService, AuthService authService) {
        this.updatesService = updatesService;
        this.statsService = statsService;
        this.authService = authService;
    }

    @PostMapping("/auth/register")
    public AuthDtos.AuthResponse register(@RequestBody AuthDtos.AuthRequest request) {
        return authService.register(request);
    }

    @PostMapping("/auth/login")
    public AuthDtos.AuthResponse login(@RequestBody AuthDtos.AuthRequest request) {
        return authService.login(request);
    }

    @GetMapping("/me")
    public AuthDtos.UserDto me(@RequestHeader("X-Auth-Token") String token) {
        return authService.requireUser(token);
    }

    @PostMapping("/updateAll")
    public ResponseEntity<Void> updateAllData(
            @RequestHeader("X-Auth-Token") String token,
            @RequestBody(required = false) AuthDtos.UpdateRequest request
    ) {
        authService.requireAdmin(token);
        updatesService.updateAllData(requireRequestApiKey(request));
        return ResponseEntity.ok().build();
    }

    @PostMapping("/update")
    public ResponseEntity<Void> updateToDate(
            @RequestHeader("X-Auth-Token") String token,
            @RequestBody(required = false) AuthDtos.UpdateRequest request
    ) {
        AuthDtos.UserDto user = authService.requireUser(token);
        String apiKey = "ADMIN".equals(user.role())
                ? requireRequestApiKey(request)
                : authService.requireUserApiKey(token);
        updatesService.updateToDate(apiKey);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/update/status")
    public Map<String, Object> getUpdateStatus() {
        return updatesService.getStatus();
    }

    @GetMapping("/top")
    public List<ContractorStatsDto> getTopContractors(
            @RequestParam(defaultValue = "10") int limit
    ) {
        return statsService.getTopContractorsByViolations(limit);
    }

    @GetMapping("/contractors")
    public List<ContractorStatsDto> getContractors(
            @RequestHeader("X-Auth-Token") String token,
            @RequestParam(required = false) String query,
            @RequestParam(defaultValue = "3") int minInspections,
            @RequestParam(required = false) Double maxRating,
            @RequestParam(defaultValue = "rating") String sort,
            @RequestParam(defaultValue = "asc") String direction,
            @RequestParam(defaultValue = "100") int limit
    ) {
        authService.requireUser(token);
        return statsService.findContractors(query, minInspections, maxRating, sort, direction, limit);
    }

    @GetMapping("/contractors/{name}")
    public ContractorDetailDto getContractor(@RequestHeader("X-Auth-Token") String token, @PathVariable String name) {
        authService.requireUser(token);
        return statsService.getContractor(name);
    }

    @GetMapping("/contractor")
    public ContractorDetailDto getContractorByQuery(
            @RequestHeader("X-Auth-Token") String token,
            @RequestParam String name
    ) {
        authService.requireUser(token);
        return statsService.getContractor(name);
    }

    @GetMapping("/contractors/{name}/inspections")
    public List<InspectionHistoryDto> getContractorHistory(
            @RequestHeader("X-Auth-Token") String token,
            @PathVariable String name,
            @RequestParam(defaultValue = "100") int limit
    ) {
        authService.requireUser(token);
        return statsService.getInspectionHistory(name, limit);
    }

    @GetMapping("/contractor/inspections")
    public List<InspectionHistoryDto> getContractorHistoryByQuery(
            @RequestHeader("X-Auth-Token") String token,
            @RequestParam String name,
            @RequestParam(defaultValue = "100") int limit
    ) {
        authService.requireUser(token);
        return statsService.getInspectionHistory(name, limit);
    }

    @GetMapping("/admin/users")
    public List<AuthDtos.UserDto> users(@RequestHeader("X-Auth-Token") String token) {
        authService.requireAdmin(token);
        return authService.listUsers();
    }

    @PostMapping("/admin/updateAll")
    public ResponseEntity<Void> adminUpdateAll(
            @RequestHeader("X-Auth-Token") String token,
            @RequestBody(required = false) AuthDtos.UpdateRequest request
    ) {
        authService.requireAdmin(token);
        updatesService.updateAllData(requireRequestApiKey(request));
        return ResponseEntity.ok().build();
    }

    @PostMapping("/admin/update")
    public ResponseEntity<Void> adminUpdate(
            @RequestHeader("X-Auth-Token") String token,
            @RequestBody(required = false) AuthDtos.UpdateRequest request
    ) {
        authService.requireAdmin(token);
        updatesService.updateToDate(requireRequestApiKey(request));
        return ResponseEntity.ok().build();
    }

    private String requireRequestApiKey(AuthDtos.UpdateRequest request) {
        if (request == null || request.apiKey() == null || request.apiKey().isBlank()) {
            throw new IllegalArgumentException("API key is required");
        }

        return request.apiKey().trim();
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, String>> handleRuntimeException(RuntimeException exception) {
        return ResponseEntity.badRequest().body(Map.of(
                "error", exception.getClass().getSimpleName(),
                "message", exception.getMessage() == null ? "Unexpected error" : exception.getMessage()
        ));
    }
}
