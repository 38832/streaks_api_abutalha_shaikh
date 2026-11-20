package com.streaks.api.habit;

import java.time.Instant;

public record HabitResponse(Long id, String title, Instant createdAt) {
}
