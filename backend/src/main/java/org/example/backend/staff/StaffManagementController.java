package org.example.backend.staff;

import org.example.backend.user.User;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Owner-only staff management. Sits under /api/owner/** so it's already
 * gated by the existing Clerk-JWT chain from Phase A.
 */
@RestController
@RequestMapping("/api/owner/staff")
public class StaffManagementController {

    private final StaffManagementService staffManagementService;

    public StaffManagementController(StaffManagementService staffManagementService) {
        this.staffManagementService = staffManagementService;
    }

    @GetMapping
    public List<StaffResponse> list() {
        return staffManagementService.listStaff().stream().map(StaffResponse::from).toList();
    }

    @PostMapping
    public StaffResponse create(@RequestBody CreateStaffRequest request) {
        return StaffResponse.from(staffManagementService.createStaff(request.name(), request.pin()));
    }

    @PostMapping("/{id}/reset-pin")
    public void resetPin(@PathVariable Long id, @RequestBody ResetPinRequest request) {
        staffManagementService.resetPin(id, request.pin());
    }

    @PostMapping("/{id}/deactivate")
    public void deactivate(@PathVariable Long id) {
        staffManagementService.deactivate(id);
    }

    public record CreateStaffRequest(String name, String pin) {
    }

    public record ResetPinRequest(String pin) {
    }

    public record StaffResponse(Long id, String name, boolean active, boolean pinLocked) {
        static StaffResponse from(User user) {
            return new StaffResponse(user.getId(), user.getName(), user.isActive(), user.isPinLocked());
        }
    }
}
