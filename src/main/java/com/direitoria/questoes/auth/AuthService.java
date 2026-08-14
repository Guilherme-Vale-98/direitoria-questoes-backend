package com.direitoria.questoes.auth;

import com.direitoria.questoes.auth.dto.LoginRequest;
import com.direitoria.questoes.auth.dto.RegisterRequest;
import com.direitoria.questoes.auth.dto.TokenResponse;
import com.direitoria.questoes.auth.dto.UserProfileResponse;
import com.direitoria.questoes.domain.Role;
import com.direitoria.questoes.domain.User;
import com.direitoria.questoes.repository.RoleRepository;
import com.direitoria.questoes.repository.UserRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthService {

    // A real-looking BCrypt hash used to equalize timing when the email is unknown
    // (so "no such user" and "wrong password" take the same time — no timing oracle).
    private static final String DUMMY_HASH =
            "$2a$12$C6UzMDM.H6dfI/f/IKcEeO3wQzD2x7vJf0fJ9mC2tQ3o0bq8mO1bK";

    private final UserRepository users;
    private final RoleRepository roles;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokens;

    public AuthService(
            UserRepository users,
            RoleRepository roles,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            RefreshTokenService refreshTokens) {
        this.users = users;
        this.roles = roles;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshTokens = refreshTokens;
    }

    @Transactional
    public UserProfileResponse register(RegisterRequest req) {
        String email = normalize(req.email());
        if (users.existsByEmail(email)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already registered");
        }
        Role userRole = roles.findByAuthority("USER")
                .orElseThrow(() -> new IllegalStateException("USER role not seeded"));
        User user = new User(
                req.firstName().trim(), req.lastName().trim(), email,
                passwordEncoder.encode(req.password()));
        user.addRole(userRole);
        users.save(user);
        return UserProfileResponse.from(user);
    }

    @Transactional
    public TokenResponse login(LoginRequest req) {
        String email = normalize(req.email());
        Optional<User> found = users.findByEmail(email);
        if (found.isEmpty()) {
            passwordEncoder.matches(req.password(), DUMMY_HASH); // equalize timing
            throw invalidCredentials();
        }
        User user = found.get();
        if (!passwordEncoder.matches(req.password(), user.getPassword())) {
            throw invalidCredentials();
        }
        List<String> roleNames = user.getRoles().stream().map(Role::getAuthority).toList();
        String access = jwtService.issueAccessToken(user.getId(), roleNames);
        String refresh = refreshTokens.issueNewFamily(user.getId());
        return TokenResponse.bearer(access, refresh, jwtService.getAccessTtlSeconds());
    }

    // noRollbackFor: when rotate() detects reuse it revokes the whole token family and
    // then throws 401 — since this method joins the same physical transaction, it must
    // not roll that revocation back either.
    @Transactional(noRollbackFor = ResponseStatusException.class)
    public TokenResponse refresh(String rawRefreshToken) {
        RotationResult rotated = refreshTokens.rotate(rawRefreshToken);
        List<String> roleNames = users.findById(rotated.userId())
                .map(u -> u.getRoles().stream().map(Role::getAuthority).toList())
                .orElse(List.of());
        String access = jwtService.issueAccessToken(rotated.userId(), roleNames);
        return TokenResponse.bearer(access, rotated.rawRefreshToken(), jwtService.getAccessTtlSeconds());
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        refreshTokens.revokeFamily(rawRefreshToken);
    }

    private static String normalize(String email) {
        return email == null ? null : email.trim().toLowerCase();
    }

    private static ResponseStatusException invalidCredentials() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
    }
}
