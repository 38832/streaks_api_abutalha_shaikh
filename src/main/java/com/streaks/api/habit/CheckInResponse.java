package com.streaks.api.habit;

import java.time.LocalDate;

public record CheckInResponse(
        Long id,
        Long habitId,
        LocalDate checkInDate,
        int coinsEarned,
        String message
) {
}
