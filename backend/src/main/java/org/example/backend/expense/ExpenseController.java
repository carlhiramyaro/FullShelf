package org.example.backend.expense;

import org.example.backend.user.User;
import org.example.backend.user.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Owner-only, under /api/owner/** so the Clerk-JWT chain already gates it. The shop
 * comes from CurrentShop, never from the request. The date is the owner's choice
 * (it is data, not identity), so it is the one thing taken from the body.
 */
@RestController
@RequestMapping("/api/owner/expenses")
public class ExpenseController {

    private final ExpenseService expenseService;
    private final UserRepository userRepository;

    public ExpenseController(ExpenseService expenseService, UserRepository userRepository) {
        this.expenseService = expenseService;
        this.userRepository = userRepository;
    }

    @GetMapping
    public ExpenseService.ExpenseList list(@RequestParam(required = false) LocalDate from,
                                           @RequestParam(required = false) LocalDate to) {
        return expenseService.list(from, to);
    }

    @PostMapping
    public ExpenseService.ExpenseView add(@RequestBody ExpenseRequest request, @AuthenticationPrincipal Jwt jwt) {
        User owner = userRepository.findByClerkUserId(jwt.getSubject()).orElseThrow();
        return expenseService.add(request.expenseDate(), request.amount(), request.category(), request.paidFrom(),
                request.note(), owner);
    }

    @PostMapping("/{id}/void")
    public ExpenseService.ExpenseView voidExpense(@PathVariable Long id,
                                                  @RequestBody(required = false) VoidExpenseRequest request,
                                                  @AuthenticationPrincipal Jwt jwt) {
        User owner = userRepository.findByClerkUserId(jwt.getSubject()).orElseThrow();
        return expenseService.voidExpense(id, request == null ? null : request.note(), owner);
    }

    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleBadInput(RuntimeException ex) {
        return new ErrorResponse(ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleBadDate(MethodArgumentTypeMismatchException ex) {
        return new ErrorResponse("Invalid value: " + ex.getValue());
    }

    public record ExpenseRequest(LocalDate expenseDate, BigDecimal amount, String category, String paidFrom,
                                 String note) {
    }

    public record VoidExpenseRequest(String note) {
    }

    public record ErrorResponse(String message) {
    }
}
