package com.streaks.api.wallet;

public class RedeemRequest {

    private Integer amount;

    public RedeemRequest() {
    }

    public RedeemRequest(Integer amount) {
        this.amount = amount;
    }

    public Integer getAmount() {
        return amount;
    }

    public void setAmount(Integer amount) {
        this.amount = amount;
    }
}
