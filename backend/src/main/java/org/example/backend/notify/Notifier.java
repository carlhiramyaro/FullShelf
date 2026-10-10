package org.example.backend.notify;

import org.example.backend.shop.Shop;

/**
 * The one delivery-agnostic seam for stock alerts, per mvp.md's "alerts are
 * sent through a small notifier layer, so the delivery channel can be swapped
 * without touching the rest of the app." The shop says whose owner gets the
 * message: alerts go to the owner of the shop whose stock crossed, and only
 * that one. How that owner's phone number is resolved is left to whichever
 * implementation actually sends something.
 */
public interface Notifier {

    void send(Shop shop, String message);
}
