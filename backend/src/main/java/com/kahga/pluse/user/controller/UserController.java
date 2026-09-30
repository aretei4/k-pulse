package com.kahga.pluse.user.controller;

import com.kahga.pluse.common.response.ApiResponse;
import com.kahga.pluse.user.dto.CreateAdminRequest;
import com.kahga.pluse.user.dto.CreateUserRequest;
import com.kahga.pluse.user.dto.UpdateAdminScopeRequest;
import com.kahga.pluse.user.dto.UpdateUserStatusRequest;
import com.kahga.pluse.user.dto.UserDto;
import com.kahga.pluse.user.entity.Role;
import com.kahga.pluse.user.service.UserService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping
    public ApiResponse<List<UserDto>> list(@RequestParam(required = false) String role) {
        Role filter = role == null || role.isBlank() || "ALL".equalsIgnoreCase(role) ? null : Role.valueOf(role);
        return ApiResponse.ok(userService.list(filter).stream().map(UserDto::from).toList());
    }

    @PostMapping
    public ApiResponse<UserDto> create(@Valid @RequestBody CreateUserRequest request) {
        return ApiResponse.ok(UserDto.from(userService.createFieldAgent(request)));
    }

    /** FR-A13: creating admin accounts — SecurityConfig restricts this path to SUPER_ADMIN. */
    @PostMapping("/admins")
    public ApiResponse<UserDto> createAdmin(@Valid @RequestBody CreateAdminRequest request) {
        return ApiResponse.ok(UserDto.from(userService.createAdmin(request)));
    }

    @PatchMapping("/admins/{id}/scope")
    public ApiResponse<UserDto> updateScope(
            @PathVariable UUID id, @Valid @RequestBody UpdateAdminScopeRequest request) {
        return ApiResponse.ok(UserDto.from(userService.updateAdminScope(id, request)));
    }

    @PatchMapping("/{id}/status")
    public ApiResponse<UserDto> setStatus(@PathVariable UUID id, @RequestBody UpdateUserStatusRequest request) {
        return ApiResponse.ok(UserDto.from(userService.setActive(id, request.active())));
    }
}
