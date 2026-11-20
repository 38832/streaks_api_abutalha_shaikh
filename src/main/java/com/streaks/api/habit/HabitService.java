package com.streaks.api.habit;

import com.streaks.api.common.ApiException;
import com.streaks.api.wallet.WalletService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

@Service
public class HabitService {

    private final HabitRepository habitRepository;
    private final CheckInRepository checkInRepository;
    private final WalletService walletService;
    private final Clock clock;

    @Autowired
    public HabitService(
            HabitRepository habitRepository,
            CheckInRepository checkInRepository,
            WalletService walletService
    ) {
        this(habitRepository, checkInRepository, walletService, Clock.systemUTC());
    }

    public HabitService(
            HabitRepository habitRepository,
            CheckInRepository checkInRepository,
            WalletService walletService,
            Clock clock
    ) {
        this.habitRepository = habitRepository;
        this.checkInRepository = checkInRepository;
        this.walletService = walletService;
        this.clock = clock;
    }

    @Transactional
    public HabitResponse createHabit(Long userId, CreateHabitRequest req) {
        if (req == null || req.getTitle() == null || req.getTitle().trim().isEmpty()) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_TITLE",
                    "Habit title must not be blank"
            );
        }

        Habit habit = new Habit(userId, req.getTitle().trim());
        Habit saved = habitRepository.save(habit);
        return new HabitResponse(saved.getId(), saved.getTitle(), saved.getCreatedAt());
    }

    @Transactional(readOnly = true)
    public List<HabitResponse> getHabits(Long userId) {
        return habitRepository.findAllByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(h -> new HabitResponse(h.getId(), h.getTitle(), h.getCreatedAt()))
                .toList();
    }

    @Transactional
    public CheckInResponse checkIn(Long userId, Long habitId) {
        // Enforce tenant isolation: return 404 if habit does not belong to user
        Habit habit = habitRepository.findByIdAndUserId(habitId, userId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "HABIT_NOT_FOUND",
                        "Habit not found"
                ));

        LocalDate today = LocalDate.now(clock);

        // Pre-check for duplicate check-in today
        if (checkInRepository.existsByHabitIdAndCheckInDate(habit.getId(), today)) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "ALREADY_CHECKED_IN",
                    "Habit already checked in today"
            );
        }

        try {
            CheckIn checkIn = new CheckIn(habit.getId(), today);
            CheckIn savedCheckIn = checkInRepository.save(checkIn);

            // Award 10 coins
            walletService.creditCoins(userId, 10);

            return new CheckInResponse(
                    savedCheckIn.getId(),
                    habit.getId(),
                    today,
                    10,
                    "Check-in successful! 10 coins added to your wallet"
            );
        } catch (DataIntegrityViolationException ex) {
            // Guard against race conditions where concurrent calls bypass the pre-check
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "ALREADY_CHECKED_IN",
                    "Habit already checked in today"
            );
        }
    }
}
