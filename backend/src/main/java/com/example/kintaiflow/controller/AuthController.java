package com.example.kintaiflow.controller;

import com.example.kintaiflow.dto.ChangePasswordRequest;
import com.example.kintaiflow.dto.LoginRequest;
import com.example.kintaiflow.dto.LoginResponse;
import com.example.kintaiflow.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** API-001 ログイン／API-023 パスワード変更。Controller は受け取りと受け渡しだけを行う。 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    /** トークンの subject（ユーザーID）から本人を特定するため、他人のパスワードは変更できない。 */
    @PostMapping("/password")
    public ResponseEntity<Void> changePassword(Authentication authentication,
                                               @Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(Long.valueOf(authentication.getName()), request);
        return ResponseEntity.noContent().build();
    }
}
