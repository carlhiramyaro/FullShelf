package org.example.backend.notify;

/**
 * The one delivery-agnostic seam for alerts to Aunt Amerley, per mvp.md's
 * "alerts are sent through a small notifier layer, so the delivery channel
 * can be swapped without touching the rest of the app." No recipient
 * parameter: there is exactly one recipient in this whole app ("alerts go to
 * her only"), and how that phone number is resolved is left to whichever
 * implementation actually sends something (SMS delivery, next slice).
 */
public interface Notifier {

    void send(String message);
}
