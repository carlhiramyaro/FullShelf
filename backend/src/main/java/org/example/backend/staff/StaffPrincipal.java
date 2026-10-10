package org.example.backend.staff;

/**
 * The authenticated principal StaffAccessFilter sets for requests carrying a
 * valid X-Staff-Session-Token — proves which staff member is currently
 * serving on the device, which session to revoke on logout, and which shop
 * both the staff member and the device belong to.
 */
public record StaffPrincipal(Long userId, Long sessionId, Long shopId) {
}
