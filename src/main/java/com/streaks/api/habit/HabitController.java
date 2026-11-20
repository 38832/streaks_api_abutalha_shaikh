package com.streaks.api.habit;

import com.streaks.api.security.UserPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/habits")
public class HabitController {

    private final HabitService habitService;

    public HabitController(HabitService habitService) {
        this.habitService = habitService;
    }

    @PostMapping
    public ResponseEntity<HabitResponse> createHabit(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestBody(required = false) CreateHabitRequest req
    ) {
        HabitResponse response = habitService.createHabit(principal.getId(), req);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<List<HabitResponse>> getHabits(@AuthenticationPrincipal UserPrincipal principal) {
        List<HabitResponse> response = habitService.getHabits(principal.getId());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/check-ins")
    public ResponseEntity<CheckInResponse> checkIn(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable("id") Long habitId
    ) {
        CheckInResponse response = habitService.checkIn(principal.getId(), habitId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
