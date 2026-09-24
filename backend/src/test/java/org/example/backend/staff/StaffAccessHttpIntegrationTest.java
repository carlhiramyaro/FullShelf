package org.example.backend.staff;

import org.example.backend.device.DevicePairingService;
import org.example.backend.user.User;
import org.example.backend.user.UserRepository;
import org.example.backend.user.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Exercises StaffAccessFilter end to end over real HTTP, since it's custom
 * security code (not Spring's usual authorizeHttpRequests role checks) —
 * unlike the owner chain, there's no framework plumbing already proven
 * elsewhere to lean on. Pairs a real device through /api/devices/pair rather
 * than inserting one directly, so the whole chain from pairing code to
 * staff session is covered in one place.
 *
 * No Jackson ObjectMapper on this project's classpath (a thin
 * spring-boot-starter-webmvc, not -web), so request bodies are built as
 * plain JSON strings and response fields pulled out with a small regex
 * rather than pulling in a JSON library just for two-field test bodies.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class StaffAccessHttpIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DevicePairingService devicePairingService;

    private static String extractJsonField(String json, String field) {
        Matcher matcher = Pattern.compile("\"" + field + "\"\\s*:\\s*\"([^\"]*)\"").matcher(json);
        if (!matcher.find()) {
            throw new AssertionError("Field " + field + " not found in " + json);
        }
        return matcher.group(1);
    }

    private String pairDevice() throws Exception {
        User owner = userRepository.save(new User("Aunt Amerley", UserRole.OWNER));
        String code = devicePairingService.generateCode(owner);

        String response = mockMvc.perform(post("/api/devices/pair")
                        .contentType("application/json")
                        .content("{\"code\":\"" + code + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        return extractJsonField(response, "deviceToken");
    }

    @Test
    void rosterIsRejectedWithoutADeviceToken() throws Exception {
        mockMvc.perform(get("/api/staff/roster"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rosterIsRejectedWithAnUnknownDeviceToken() throws Exception {
        mockMvc.perform(get("/api/staff/roster").header("X-Device-Token", "not-a-real-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void pairedDeviceCanListRoster() throws Exception {
        String deviceToken = pairDevice();
        User staff = new User("Kofi", UserRole.STAFF);
        staff.setPinHash(new BCryptPasswordEncoder().encode("1234"));
        userRepository.save(staff);

        mockMvc.perform(get("/api/staff/roster").header("X-Device-Token", deviceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Kofi"));
    }

    @Test
    void loginWithWrongPinIsUnauthorized() throws Exception {
        String deviceToken = pairDevice();
        User staff = new User("Kofi", UserRole.STAFF);
        staff.setPinHash(new BCryptPasswordEncoder().encode("1234"));
        User saved = userRepository.save(staff);

        mockMvc.perform(post("/api/staff/login")
                        .header("X-Device-Token", deviceToken)
                        .contentType("application/json")
                        .content("{\"staffId\":" + saved.getId() + ",\"pin\":\"0000\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void loginThenLogoutRoundTrips() throws Exception {
        String deviceToken = pairDevice();
        User staff = new User("Kofi", UserRole.STAFF);
        staff.setPinHash(new BCryptPasswordEncoder().encode("1234"));
        User saved = userRepository.save(staff);

        String loginResponse = mockMvc.perform(post("/api/staff/login")
                        .header("X-Device-Token", deviceToken)
                        .contentType("application/json")
                        .content("{\"staffId\":" + saved.getId() + ",\"pin\":\"1234\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String sessionToken = extractJsonField(loginResponse, "sessionToken");

        mockMvc.perform(post("/api/staff/logout").header("X-Staff-Session-Token", sessionToken))
                .andExpect(status().isOk());

        // The now-revoked session can't be reused for another staff-only route.
        mockMvc.perform(post("/api/staff/logout").header("X-Staff-Session-Token", sessionToken))
                .andExpect(status().isUnauthorized());
    }
}
