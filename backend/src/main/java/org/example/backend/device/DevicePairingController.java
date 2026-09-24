package org.example.backend.device;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Deliberately public: the code itself is the credential, and this is the
 * only way an unpaired browser ever becomes the shop device. Falls through
 * to SecurityConfig's open default chain rather than needing one of its own.
 */
@RestController
@RequestMapping("/api/devices")
public class DevicePairingController {

    private final DevicePairingService devicePairingService;

    public DevicePairingController(DevicePairingService devicePairingService) {
        this.devicePairingService = devicePairingService;
    }

    @PostMapping("/pair")
    public PairResponse pair(@RequestBody PairRequest request) {
        return new PairResponse(devicePairingService.redeemCode(request.code()));
    }

    @ExceptionHandler(InvalidPairingCodeException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleInvalidCode(InvalidPairingCodeException ex) {
        return new ErrorResponse(ex.getMessage());
    }

    public record PairRequest(String code) {
    }

    public record PairResponse(String deviceToken) {
    }

    public record ErrorResponse(String message) {
    }
}
