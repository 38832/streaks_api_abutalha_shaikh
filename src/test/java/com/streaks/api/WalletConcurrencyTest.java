package com.streaks.api;

import com.streaks.api.common.ApiException;
import com.streaks.api.user.User;
import com.streaks.api.user.UserRepository;
import com.streaks.api.wallet.Wallet;
import com.streaks.api.wallet.WalletRepository;
import com.streaks.api.wallet.WalletService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
public class WalletConcurrencyTest {

    @Autowired
    private WalletService walletService;

    @Autowired
    private WalletRepository walletRepository;

    @Autowired
    private UserRepository userRepository;

    private Long userId;

    @BeforeEach
    void setUp() {
        walletRepository.deleteAll();
        userRepository.deleteAll();

        User user = new User("Concurrency User", "concurrent@example.com", "hash");
        user = userRepository.save(user);
        userId = user.getId();

        // Initial balance: 50 coins
        Wallet wallet = new Wallet(userId, 50);
        walletRepository.save(wallet);
    }

    @Test
    @DisplayName("Concurrent redemptions: two simultaneous requests for 50 coins will only allow one to succeed")
    void concurrentRedeem_PreventsDoubleSpendingAndNegativeBalance() throws InterruptedException {
        int numberOfThreads = 2;
        int redeemAmount = 50;

        ExecutorService executor = Executors.newFixedThreadPool(numberOfThreads);
        CountDownLatch readyLatch = new CountDownLatch(numberOfThreads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(numberOfThreads);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);
        List<Throwable> exceptions = Collections.synchronizedList(new ArrayList<>());

        for (int i = 0; i < numberOfThreads; i++) {
            executor.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await(); // ensure simultaneous execution
                    walletService.redeem(userId, redeemAmount);
                    successCount.incrementAndGet();
                } catch (ApiException e) {
                    failureCount.incrementAndGet();
                    exceptions.add(e);
                } catch (Throwable t) {
                    exceptions.add(t);
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        readyLatch.await(5, TimeUnit.SECONDS);
        startLatch.countDown(); // fire all threads at once
        boolean finished = doneLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertTrue(finished, "All concurrent tasks should complete within timeout");
        assertEquals(1, successCount.get(), "Exactly one redeem request should succeed");
        assertEquals(1, failureCount.get(), "The second redeem request should fail with insufficient balance");

        // Verify final wallet balance in DB is exactly 0, never negative
        Wallet finalWallet = walletRepository.findByUserId(userId).orElseThrow();
        assertEquals(0, finalWallet.getBalance(), "Final balance must be 0 and cannot be negative");
    }
}
