package org.example.backend.device;

/**
 * The authenticated principal StaffAccessFilter sets for requests carrying a
 * valid X-Device-Token — proves "this is the paired shop device," nothing
 * about which staff member (if any) is currently serving on it.
 */
public record DevicePrincipal(Long deviceId) {
}
