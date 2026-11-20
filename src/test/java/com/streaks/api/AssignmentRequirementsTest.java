package com.streaks.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.streaks.api.auth.LoginRequest;
import com.streaks.api.auth.SignupRequest;
import com.streaks.api.habit.CheckInRepository;
import com.streaks.api.habit.CreateHabitRequest;
import com.streaks.api.habit.Habit;
import com.streaks.api.habit.HabitRepository;
import com.streaks.api.user.UserRepository;
import com.streaks.api.wallet.RedeemRequest;
import com.streaks.api.wallet.Wallet;
import com.streaks.api.wallet.WalletRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class AssignmentRequirementsTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private WalletRepository walletRepository;

    @Autowired
    private HabitRepository habitRepository;

    @Autowired
    private CheckInRepository checkInRepository;

    @BeforeEach
    void setUp() {
        checkInRepository.deleteAll();
        habitRepository.deleteAll();
        walletRepository.deleteAll();
        userRepository.deleteAll();
    }

    private String registerAndGetToken(String name, String email, String password) throws Exception {
        SignupRequest signup = new SignupRequest(email, password, name);
        mockMvc.perform(post("/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(signup)))
                .andExpect(status().isCreated());

        LoginRequest login = new LoginRequest(email, password);
        MvcResult loginResult = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(login)))
                .andExpect(status().isOk())
                .andReturn();

        String responseJson = loginResult.getResponse().getContentAsString();
        return objectMapper.readTree(responseJson).get("token").asText();
    }

    private Long createHabitAndGetId(String token, String title) throws Exception {
        CreateHabitRequest req = new CreateHabitRequest(title);
        MvcResult result = mockMvc.perform(post("/habits")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();

        String responseJson = result.getResponse().getContentAsString();
        return objectMapper.readTree(responseJson).get("id").asLong();
    }

    @Test
    @DisplayName("Requirement 1: Checking in twice on the same day returns 409 Conflict")
    void checkingInTwiceOnSameDay_Returns409() throws Exception {
        String token = registerAndGetToken("Alice", "alice@example.com", "password123");
        Long habitId = createHabitAndGetId(token, "Drink 2L Water");

        // First check-in of the day -> 201 Created (earns 10 coins)
        mockMvc.perform(post("/habits/{id}/check-ins", habitId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.coinsEarned", is(10)));

        // Second check-in on the same day -> 409 Conflict
        mockMvc.perform(post("/habits/{id}/check-ins", habitId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error", is("ALREADY_CHECKED_IN")))
                .andExpect(jsonPath("$.message", is("Habit already checked in today")));
    }

    @Test
    @DisplayName("Requirement 2: Redeeming more than balance returns 422 Unprocessable Entity")
    void redeemingMoreThanBalance_Returns422() throws Exception {
        String token = registerAndGetToken("Bob", "bob@example.com", "password123");
        Long habitId = createHabitAndGetId(token, "Daily Walk");

        // Single check-in -> 10 coins in wallet
        mockMvc.perform(post("/habits/{id}/check-ins", habitId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated());

        // Verify balance is 10
        mockMvc.perform(get("/wallet")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance", is(10)));

        // Attempt to redeem 50 coins (balance is only 10) -> 422 Unprocessable Entity
        RedeemRequest redeemReq = new RedeemRequest(50);
        mockMvc.perform(post("/wallet/redeem")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(redeemReq)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error", is("INSUFFICIENT_BALANCE")))
                .andExpect(jsonPath("$.message", is("Not enough coins")));
    }

    @Test
    @DisplayName("Requirement 3: Checking in on another user's habit returns 404 Not Found")
    void checkingInOnAnotherUsersHabit_Returns404() throws Exception {
        String aliceToken = registerAndGetToken("Alice", "alice.private@example.com", "password123");
        String eveToken = registerAndGetToken("Eve", "eve@example.com", "password123");

        // Alice creates a habit
        Long alicesHabitId = createHabitAndGetId(aliceToken, "Read 10 pages");

        // Eve tries to check-in on Alice's habit -> 404 Not Found (tenant isolation)
        mockMvc.perform(post("/habits/{id}/check-ins", alicesHabitId)
                        .header("Authorization", "Bearer " + eveToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error", is("HABIT_NOT_FOUND")))
                .andExpect(jsonPath("$.message", is("Habit not found")));
    }

    @Test
    @DisplayName("Rule: Redeem amount must be positive; non-positive returns 400 Bad Request")
    void redeemingNonPositiveAmount_Returns400() throws Exception {
        String token = registerAndGetToken("Charlie", "charlie@example.com", "password123");

        // Amount <= 0
        RedeemRequest zeroReq = new RedeemRequest(0);
        mockMvc.perform(post("/wallet/redeem")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(zeroReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", is("INVALID_AMOUNT")));

        // Negative amount
        RedeemRequest negativeReq = new RedeemRequest(-10);
        mockMvc.perform(post("/wallet/redeem")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(negativeReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", is("INVALID_AMOUNT")));
    }
}
