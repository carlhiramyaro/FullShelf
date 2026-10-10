package org.example.backend.shop;

import org.springframework.stereotype.Component;

/**
 * The one place services ask "which shop is this request for?".
 *
 * The security filters (OwnerShopFilter for owner routes, StaffAccessFilter
 * for staff routes) resolve the shop from the authenticated identity and
 * {@link #set} it for the duration of the request, then {@link #clear} it.
 * Nothing else sets it, and it is never read from a request body or URL.
 * If nothing set it (unauthenticated, or a route with no shop), {@link #get}
 * throws rather than guessing a shop.
 *
 * Held in a ThreadLocal on a singleton, not a request-scoped bean: a request
 * is one thread end to end, and this keeps service-level tests (which run
 * with no web request) able to set a shop directly.
 */
@Component
public class CurrentShop {

    private static final ThreadLocal<Shop> CURRENT = new ThreadLocal<>();

    public Shop get() {
        Shop shop = CURRENT.get();
        if (shop == null) {
            throw new IllegalStateException("No shop is set for this request");
        }
        return shop;
    }

    public void set(Shop shop) {
        CURRENT.set(shop);
    }

    public void clear() {
        CURRENT.remove();
    }
}
