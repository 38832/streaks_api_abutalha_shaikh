package com.streaks.api.habit;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;

@Repository
public interface CheckInRepository extends JpaRepository<CheckIn, Long> {

    boolean existsByHabitIdAndCheckInDate(Long habitId, LocalDate checkInDate);

    long countByHabitId(Long habitId);
}
