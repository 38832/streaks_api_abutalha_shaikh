package com.streaks.api.wallet;

import com.streaks.api.security.UserPrincipal;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/wallet")
public class WalletController {

    private final WalletService walletService;

    public WalletController(WalletService walletService) {
        this.walletService = walletService;
    }

    @GetMapping
    public ResponseEntity<WalletResponse> getWallet(@AuthenticationPrincipal UserPrincipal principal) {
        WalletResponse response = walletService.getWallet(principal.getId());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/redeem")
    public ResponseEntity<RedeemResponse> redeem(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestBody(required = false) RedeemRequest req
    ) {
        Integer amount = req != null ? req.getAmount() : null;
        RedeemResponse response = walletService.redeem(principal.getId(), amount);
        return ResponseEntity.ok(response);
    }
}
