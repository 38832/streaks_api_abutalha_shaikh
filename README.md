# Streaks API

A secure, concurrent RESTful backend for a habit-tracking app built with **Java 17**, **Spring Boot 3.2**, **Spring Security 6**, **Spring Data JPA**, **Flyway**, and **PostgreSQL** (with **H2** for in-memory testing).

- **Live URL (Deployed on Railway)**: [https://streaks-api-production.up.railway.app](https://streaks-api-production.up.railway.app)
- **GitHub Repository**: [https://github.com/38832/streaks_api_abutalha_shaikh](https://github.com/38832/streaks_api_abutalha_shaikh)

---

## Features

- **Authentication & Security**:
  - Stateless JWT authentication (`io.jsonwebtoken:jjwt`).
  - Passwords hashed using **BCrypt** with salt (never stored in plain text).
  - Strict tenant isolation: users can only view and check in on their own habits (accessing another user's habit returns `404 Not Found`).
- **Habit Tracking & Streaks**:
  - Create and list personal habits.
  - One check-in per habit per day: duplicate daily check-in returns `409 Conflict`.
  - Each check-in automatically awards **10 coins** to the user's wallet.
- **Wallet & Concurrency Protection**:
  - View balance and redeem coins.
  - Validates positive amount (returns `400 Bad Request` if $\le 0$).
  - Overdraft protection: returns `422 Unprocessable Entity` if `amount > balance`.
  - **Thread-safe & Race-condition proof**: Protected with pessimistic write locking (`SELECT ... FOR UPDATE`) and database-level `CHECK (balance >= 0)` constraints.
- **Clean Architecture & Error Handling**:
  - Centralized `@RestControllerAdvice` returning standardized `{ "error": "...", "message": "..." }` responses with **no stack traces** leaked.
  - Zero hardcoded secrets: externalized configuration via environment variables.
  - Automated database migrations using **Flyway**.
  - Multi-stage **Dockerfile** and **docker-compose.yml**.
  - Automated CI pipeline with **GitHub Actions**.

---

## API Specification

| Method | Path | Auth | Request Body | Status Codes | Description |
|---|---|---|---|---|---|
| `POST` | `/auth/signup` | None | `{"email": "...", "password": "...", "name": "..."}` | 201, 400, 409 | Registers a new user & initializes wallet with 0 balance |
| `POST` | `/auth/login` | None | `{"email": "...", "password": "..."}` | 200, 400, 401 | Authenticates credentials and returns a Bearer JWT |
| `POST` | `/habits` | Bearer JWT | `{"title": "..."}` | 201, 400, 401 | Creates a new habit for authenticated user |
| `GET` | `/habits` | Bearer JWT | None | 200, 401 | Lists all habits belonging to authenticated user |
| `POST` | `/habits/{id}/check-ins` | Bearer JWT | None | 201, 401, 404, 409 | Daily check-in (+10 coins). 409 on duplicate today, 404 on foreign habit |
| `GET` | `/wallet` | Bearer JWT | None | 200, 401 | Returns current coin balance |
| `POST` | `/wallet/redeem` | Bearer JWT | `{"amount": 50}` | 200, 400, 401, 422 | Deducts coins. 400 if amount $\le 0$, 422 if amount > balance |

---

## Environment Variables

All secrets and connection parameters are loaded from environment variables with safe development defaults:

| Variable | Default Value | Description |
|---|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5432/streaks_db` | JDBC URL for PostgreSQL database |
| `DB_USERNAME` | `streaks_user` | Database username |
| `DB_PASSWORD` | `streaks_password` | Database password |
| `JWT_SECRET` | `404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970` | 256-bit secret key for signing JWTs |
| `JWT_EXPIRATION_MS` | `86400000` (24 hours) | JWT validity window in milliseconds |
| `PORT` | `8080` | Server HTTP port |

---

## Getting Started Locally

### Prerequisites

- **Java 17** or higher
- **Docker & Docker Compose** (optional, recommended for PostgreSQL)

### 1. Using Docker Compose (Easiest)

To spin up both PostgreSQL and the Streaks API container together:

```bash
docker compose up --build
```

The application will be running at `http://localhost:8080`.

### 2. Running Locally with Maven

Start a PostgreSQL instance (or use the one in docker-compose):

```bash
docker compose up db -d
```

Run the application:

```bash
# Windows
.\mvnw.cmd spring-boot:run

# Linux / macOS
./mvnw spring-boot:run
```

---

## Running the Tests

The test suite runs with an in-memory **H2 database in PostgreSQL compatibility mode** and executes all Flyway migrations:

```bash
# Windows
.\mvnw.cmd test

# Linux / macOS
./mvnw test
```

### Included Tests:

1. **`checkingInTwiceOnSameDay_Returns409`**: Verifies that checking in on the same habit twice in one day returns HTTP 409 Conflict (`ALREADY_CHECKED_IN`).
2. **`redeemingMoreThanBalance_Returns422`**: Verifies that attempting to redeem more coins than currently available returns HTTP 422 Unprocessable Entity (`INSUFFICIENT_BALANCE`).
3. **`checkingInOnAnotherUsersHabit_Returns404`**: Verifies that attempting to check in on a habit belonging to another user returns HTTP 404 Not Found (`HABIT_NOT_FOUND`).
4. **`redeemingNonPositiveAmount_Returns400`**: Verifies that negative or zero redeem amounts return HTTP 400 Bad Request (`INVALID_AMOUNT`).
5. **`WalletConcurrencyTest`**: Simulates multi-threaded concurrent redemption requests against a single wallet to verify row-level pessimistic locking and prevention of double spending / negative balances.
6. **`HabitFlowIntegrationTest`**: Full end-to-end user lifecycle test covering signup, login, habit creation, habit listing, check-in wallet rewards, and redemption.

---

## Example cURL Commands

### 1. User Signup
```bash
curl -X POST http://localhost:8080/auth/signup \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Jane Doe",
    "email": "jane@example.com",
    "password": "securePassword123"
  }'
```

### 2. User Login
```bash
curl -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "email": "jane@example.com",
    "password": "securePassword123"
  }'
```
*Response:*
```json
{
  "token": "eyJhbGciOiJIUzI1NiIsIn..."
}
```

### 3. Create a Habit
```bash
curl -X POST http://localhost:8080/habits \
  -H "Authorization: Bearer <YOUR_TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{
    "title": "Read 10 pages"
  }'
```

### 4. List Habits
```bash
curl -X GET http://localhost:8080/habits \
  -H "Authorization: Bearer <YOUR_TOKEN>"
```

### 5. Check in for Today (+10 coins)
```bash
curl -X POST http://localhost:8080/habits/1/check-ins \
  -H "Authorization: Bearer <YOUR_TOKEN>"
```

### 6. Get Wallet Balance
```bash
curl -X GET http://localhost:8080/wallet \
  -H "Authorization: Bearer <YOUR_TOKEN>"
```

### 7. Redeem Coins
```bash
curl -X POST http://localhost:8080/wallet/redeem \
  -H "Authorization: Bearer <YOUR_TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{
    "amount": 10
  }'
```

---

## Architecture & Design Questions

### Question 1:
**What happens in your code if a user taps "Redeem" twice very quickly? Could they lose coins twice, or end up with a negative balance? How would you prevent that?**

#### Answer:
In our code, **a user can neither lose coins twice nor end up with a negative balance**. We prevent this through two cooperative safeguards:

1. **Pessimistic Row-Level Database Locking (`PESSIMISTIC_WRITE`)**:
   In `WalletService.redeem()`, the wallet is retrieved using `walletRepository.findByUserIdForUpdate(userId)`. This issues a `SELECT ... FOR UPDATE` query in PostgreSQL/SQL, acquiring an exclusive lock on that user's wallet row within the transaction. When two requests arrive concurrently:
   - The first transaction locks the row, verifies `balance >= amount`, subtracts the amount, writes the new balance, and commits.
   - The second transaction waits for the lock. Upon acquiring it, it reads the freshly committed lower balance. If the balance is no longer sufficient, it immediately rejects the request with HTTP `422 Unprocessable Entity` (`INSUFFICIENT_BALANCE`).
2. **Database Integrity Constraint**:
   The table schema enforces `CONSTRAINT chk_wallet_balance_non_negative CHECK (balance >= 0)`. Even in the event of an unforeseen application bug, the database itself refuses any transaction resulting in a negative balance.
3. **Automated Concurrency Test**:
   We prove this behavior in `WalletConcurrencyTest.java`, where multiple threads fire simultaneous redeem requests against a shared balance using `CountDownLatch` and `ExecutorService`.

---

### Question 2:
**If this API were going live for real users tomorrow, what are the top 3–5 things you would add or change?**

#### Answer:
1. **Idempotency Keys on Mutations**:
   Add an `Idempotency-Key` HTTP header for `/wallet/redeem` (persisted temporarily in Redis or a dedicated table). If network drops cause the client to retry an identical request, the server safely returns the original response without re-executing the deduction.
2. **Immutable Financial Ledger (`wallet_transactions`)**:
   Rather than merely mutating a single aggregate `balance` column, store an append-only transaction history (e.g., `id`, `wallet_id`, `amount`, `type: CREDIT/DEBIT`, `reference_id`, `timestamp`). This enables auditing, dispute resolution, financial reconciliation, and ledger replaying.
3. **Timezone-Aware Daily Check-ins**:
   Currently, day boundaries are calculated using the server's UTC date (`LocalDate.now(clock)`). Real users live in diverse timezones, so 11:00 PM local time could be evaluated as "tomorrow" in UTC. Storing the user's IANA timezone (or accepting a client timezone header) ensures check-ins correspond to their actual calendar day.
4. **Rate Limiting & Brute-Force Protection**:
   Implement rate limiting (e.g. using Bucket4j or Redis tokens) across sensitive endpoints: strictly limiting `/auth/login` to prevent credential stuffing and throttling `/habits/{id}/check-ins` and `/wallet/redeem` against automated bot activity.
5. **Observability, Healthchecks & Token Revocation**:
   Integrate **Spring Boot Actuator** with Prometheus metrics and structured JSON logging for monitoring; add a token revocation / refresh token rotation mechanism so compromised JWTs can be immediately invalidated before their expiry.
