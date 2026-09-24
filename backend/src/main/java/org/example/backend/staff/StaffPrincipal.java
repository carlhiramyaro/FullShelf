package org.example.backend.staff;

/**
 * The authenticated principal StaffAccessFilter sets for requests carrying a
 * valid X-Staff-Session-Token — proves which staff member is currently
 * serving on the device, and which session to revoke on logout.
 */
public record StaffPrincipal(Long userId, Long sessionId) {
}
