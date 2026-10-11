package org.example.backend.admin;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Platform admin (Carl only; see AdminJwtAuthoritiesConverter). The shop id in
 * the re-link URL is fine here: the admin has no shop of their own and acts on
 * any shop by design. The "shop comes from the identity" rule is for callers
 * who belong to a shop.
 */
@RestController
@RequestMapping("/api/admin")
public class AdminShopController {

    private final AdminShopService service;

    public AdminShopController(AdminShopService service) {
        this.service = service;
    }

    @GetMapping("/me")
    public MeResponse me() {
        return new MeResponse(true);
    }

    @GetMapping("/shops")
    public List<AdminShopService.ShopSummary> shops() {
        return service.listShops();
    }

    @PostMapping("/shops")
    public AdminShopService.ShopSummary createShop(@RequestBody CreateShopRequest request) {
        return service.createShop(request.shopName(), request.ownerName(), request.phone(), request.email());
    }

    @PostMapping("/shops/{shopId}/owner")
    public AdminShopService.ShopSummary relinkOwner(@PathVariable Long shopId, @RequestBody OwnerRequest request) {
        return service.relinkOwner(shopId, request.ownerName(), request.phone(), request.email());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleBadInput(IllegalArgumentException ex) {
        return new ErrorResponse(ex.getMessage());
    }

    public record MeResponse(boolean admin) {
    }

    public record CreateShopRequest(String shopName, String ownerName, String phone, String email) {
    }

    public record OwnerRequest(String ownerName, String phone, String email) {
    }

    public record ErrorResponse(String message) {
    }
}
