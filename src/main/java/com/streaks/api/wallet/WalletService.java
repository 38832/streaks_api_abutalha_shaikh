package com.streaks.api.wallet;

import com.streaks.api.common.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WalletService {

    private final WalletRepository walletRepository;

    public WalletService(WalletRepository walletRepository) {
        this.walletRepository = walletRepository;
    }

    @Transactional(readOnly = true)
    public WalletResponse getWallet(Long userId) {
        Wallet wallet = walletRepository.findByUserId(userId)
                .orElseGet(() -> new Wallet(userId, 0));
        return new WalletResponse(wallet.getBalance());
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void creditCoins(Long userId, int amount) {
        Wallet wallet = walletRepository.findByUserIdForUpdate(userId)
                .orElseGet(() -> walletRepository.save(new Wallet(userId, 0)));

        wallet.setBalance(wallet.getBalance() + amount);
        walletRepository.save(wallet);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public RedeemResponse redeem(Long userId, Integer amount) {
        if (amount == null || amount <= 0) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_AMOUNT",
                    "Amount must be a positive number"
            );
        }

        Wallet wallet = walletRepository.findByUserIdForUpdate(userId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "WALLET_NOT_FOUND",
                        "Wallet not found"
                ));

        if (wallet.getBalance() < amount) {
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "INSUFFICIENT_BALANCE",
                    "Not enough coins"
            );
        }

        int newBalance = wallet.getBalance() - amount;
        wallet.setBalance(newBalance);
        walletRepository.save(wallet);

        return new RedeemResponse(newBalance, amount, "Coins redeemed successfully");
    }
}
