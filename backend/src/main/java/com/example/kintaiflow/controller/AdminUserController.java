package com.example.kintaiflow.controller;

import com.example.kintaiflow.dto.*;
import com.example.kintaiflow.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/** ONL-014 ユーザー管理 API（ADMIN 限定）。 */
@RestController
@RequestMapping("/api/admin/users")
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {

    private final UserService userService;

    public AdminUserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public UserListResponse list(@RequestParam(required = false) String keyword,
                                 @RequestParam(required = false) String role,
                                 @RequestParam(required = false) String status,
                                 @RequestParam(required = false) Long departmentId) {
        return userService.search(keyword, role, status, departmentId);
    }

    @GetMapping("/{id}")
    public UserDetailResponse get(@PathVariable Long id) {
        return userService.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserDetailResponse create(@Valid @RequestBody UserCreateRequest request, Authentication auth) {
        return userService.create(request, Long.valueOf(auth.getName()));
    }

    @PutMapping("/{id}")
    public UserDetailResponse update(@PathVariable Long id, @Valid @RequestBody UserUpdateRequest request,
                                     Authentication auth) {
        return userService.update(id, request, Long.valueOf(auth.getName()));
    }

    @PutMapping("/{id}/password")
    public UserDetailResponse resetPassword(@PathVariable Long id, @Valid @RequestBody PasswordResetRequest request,
                                            Authentication auth) {
        return userService.resetPassword(id, request, Long.valueOf(auth.getName()));
    }
}
