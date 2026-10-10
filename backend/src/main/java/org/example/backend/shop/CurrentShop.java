package org.example.backend.shop;

import org.springframework.stereotype.Component;

/**
 * The one place services ask "which shop is this request for?".
 *
 * TEMPORARY (G1): there is only shop #1 so far, so this returns it. G2
 * replaces the body with the shop resolved from the authenticated identity
 * (owner login, or device token plus staff session); callers don't change.
 */
@Component
public class CurrentShop {

    private final ShopRepository shopRepository;

    public CurrentShop(ShopRepository shopRepository) {
        this.shopRepository = shopRepository;
    }

    public Shop get() {
        return shopRepository.findFirstByOrderByIdAsc()
                .orElseThrow(() -> new IllegalStateException("No shop exists"));
    }
}
