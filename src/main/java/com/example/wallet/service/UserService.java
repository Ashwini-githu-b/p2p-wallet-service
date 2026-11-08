package com.example.wallet.service;

import com.example.wallet.dto.CreateUserRequest;
import com.example.wallet.dto.CreateUserResponse;
import com.example.wallet.entity.User;
import com.example.wallet.entity.Wallet;
import com.example.wallet.exception.EmailAlreadyExistsException;
import com.example.wallet.repository.UserRepository;
import com.example.wallet.repository.WalletRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final WalletRepository walletRepository;

    public UserService(UserRepository userRepository, WalletRepository walletRepository) {
        this.userRepository = userRepository;
        this.walletRepository = walletRepository;
    }

    @Transactional
    public CreateUserResponse createUser(CreateUserRequest request) {
        String normalizedEmail = request.email().trim().toLowerCase();
        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new EmailAlreadyExistsException(normalizedEmail);
        }

        Instant now = Instant.now();
        User user = userRepository.save(new User(request.name().trim(), normalizedEmail, now));
        Wallet wallet = walletRepository.save(new Wallet(user.getId(), 0L, now, now));

        return new CreateUserResponse(
                user.getId(),
                user.getName(),
                user.getEmail(),
                wallet.getId(),
                wallet.getBalancePaise()
        );
    }
}

