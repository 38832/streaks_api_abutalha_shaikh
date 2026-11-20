package com.streaks.api.wallet;

public record RedeemResponse(int balance, int redeemedAmount, String message) {
}
