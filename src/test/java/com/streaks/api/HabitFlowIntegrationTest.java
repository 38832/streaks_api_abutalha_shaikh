package com.streaks.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.streaks.api.auth.LoginRequest;
import com.streaks.api.auth.SignupRequest;
import com.streaks.api.habit.CheckInRepository;
import com.streaks.api.habit.CreateHabitRequest;
import com.streaks.api.habit.HabitRepository;
import com.streaks.api.user.UserRepository;
import com.streaks.api.wallet.RedeemRequest;
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

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class HabitFlowIntegrationTest {

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

    @Test
    @DisplayName("Unauthenticated request to protected endpoints returns 401 Unauthorized")
    void unauthenticatedAccess_Returns401() throws Exception {
        mockMvc.perform(get("/habits"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error", is("UNAUTHORIZED")));

        mockMvc.perform(get("/wallet"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error", is("UNAUTHORIZED")));
    }

    @Test
    @DisplayName("Duplicate signup email returns 409 Conflict")
    void duplicateSignup_Returns409() throws Exception {
        SignupRequest signup = new SignupRequest("dup@example.com", "password123", "User One");
        mockMvc.perform(post("/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(signup)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(signup)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error", is("EMAIL_ALREADY_EXISTS")));
    }

    @Test
    @DisplayName("Invalid login credentials returns 401 Unauthorized")
    void invalidLogin_Returns401() throws Exception {
        SignupRequest signup = new SignupRequest("login@example.com", "password123", "User");
        mockMvc.perform(post("/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(signup)))
                .andExpect(status().isCreated());

        LoginRequest wrongPassword = new LoginRequest("login@example.com", "wrongpass");
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(wrongPassword)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error", is("INVALID_CREDENTIALS")));
    }

    @Test
    @DisplayName("Full End-to-End flow: signup, login, habit CRUD, check-in rewards, and redemption")
    void completeUserJourney() throws Exception {
        // 1. Signup
        SignupRequest signup = new SignupRequest("journey@example.com", "securePassword123", "Journey User");
        mockMvc.perform(post("/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(signup)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email", is("journey@example.com")));

        // 2. Login
        LoginRequest login = new LoginRequest("journey@example.com", "securePassword123");
        MvcResult loginRes = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(login)))
                .andExpect(status().isOk())
                .andReturn();
        String token = objectMapper.readTree(loginRes.getResponse().getContentAsString()).get("token").asText();

        // 3. Verify initial wallet balance is 0
        mockMvc.perform(get("/wallet")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance", is(0)));

        // 4. Create Habit
        CreateHabitRequest createReq = new CreateHabitRequest("Read 15 Pages");
        MvcResult habitRes = mockMvc.perform(post("/habits")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title", is("Read 15 Pages")))
                .andReturn();
        Long habitId = objectMapper.readTree(habitRes.getResponse().getContentAsString()).get("id").asLong();

        // 5. List habits
        mockMvc.perform(get("/habits")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].title", is("Read 15 Pages")));

        // 6. Check in habit -> adds 10 coins
        mockMvc.perform(post("/habits/{id}/check-ins", habitId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.coinsEarned", is(10)));

        // 7. Check wallet balance -> now 10 coins
        mockMvc.perform(get("/wallet")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance", is(10)));

        // 8. Redeem 10 coins
        RedeemRequest redeemReq = new RedeemRequest(10);
        mockMvc.perform(post("/wallet/redeem")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(redeemReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance", is(0)))
                .andExpect(jsonPath("$.redeemedAmount", is(10)));

        // 9. Wallet balance is back to 0
        mockMvc.perform(get("/wallet")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance", is(0)));
    }
}
