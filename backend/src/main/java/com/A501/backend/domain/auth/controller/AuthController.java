package com.A501.backend.domain.auth.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.A501.backend.domain.auth.dto.request.EmailPatterns;
import com.A501.backend.domain.auth.dto.request.SignupRequest;
import com.A501.backend.domain.auth.dto.response.EmailCheckResponse;
import com.A501.backend.domain.auth.dto.response.SignupResponse;
import com.A501.backend.domain.auth.entity.Parent;
import com.A501.backend.domain.auth.service.AuthService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;

@Tag(name = "auth")
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "이메일 중복 확인")
    @GetMapping("/email/check")
    public ResponseEntity<EmailCheckResponse> checkEmail(
            @RequestParam @Email @NotBlank @Pattern(regexp = EmailPatterns.WITH_TLD) String email) {
        return ResponseEntity.ok(EmailCheckResponse.of(authService.isEmailAvailable(email)));
    }

    @Operation(summary = "이메일 회원가입")
    @PostMapping("/signup")
    public ResponseEntity<SignupResponse> signup(@Valid @RequestBody SignupRequest request) {
        Parent parent = authService.signup(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(SignupResponse.of(parent));
    }
}
