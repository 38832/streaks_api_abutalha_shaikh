package com.streaks.api.auth;

import com.streaks.api.common.ApiException;
import com.streaks.api.security.JwtService;
import com.streaks.api.user.User;
import com.streaks.api.user.UserRepository;
import com.streaks.api.wallet.Wallet;
import com.streaks.api.wallet.WalletRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final WalletRepository walletRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(
            UserRepository userRepository,
            WalletRepository walletRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService
    ) {
        this.userRepository = userRepository;
        this.walletRepository = walletRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional
    public UserResponse signup(SignupRequest req) {
        if (userRepository.existsByEmail(req.getEmail())) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "EMAIL_ALREADY_EXISTS",
                    "A user with this email already exists"
            );
        }

        String hashedPassword = passwordEncoder.encode(req.getPassword());
        User user = new User(req.getName(), req.getEmail(), hashedPassword);
        User savedUser = userRepository.save(user);

        // Auto-provision wallet with 0 coin balance
        Wallet wallet = new Wallet(savedUser.getId(), 0);
        walletRepository.save(wallet);

        return new UserResponse(
                savedUser.getId(),
                savedUser.getName(),
                savedUser.getEmail(),
                savedUser.getCreatedAt()
        );
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest req) {
        User user = userRepository.findByEmail(req.getEmail())
                .orElseThrow(() -> new ApiException(
                        HttpStatus.UNAUTHORIZED,
                        "INVALID_CREDENTIALS",
                        "Invalid email or password"
                ));

        if (!passwordEncoder.matches(req.getPassword(), user.getPasswordHash())) {
            throw new ApiException(
                    HttpStatus.UNAUTHORIZED,
                    "INVALID_CREDENTIALS",
                    "Invalid email or password"
            );
        }

        String token = jwtService.generateToken(user.getId(), user.getEmail(), user.getName());
        return new AuthResponse(token);
    }
}
