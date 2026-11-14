package org.example.backend.report;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.LocalDate;

/**
 * Owner-only, under /api/owner/** so the Clerk-JWT chain already gates it. One GET returning
 * everything the screen needs. The shop comes from CurrentShop; from/to are the owner's chosen
 * dates (data, not identity).
 */
@RestController
@RequestMapping("/api/owner/reports")
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @GetMapping
    public ReportService.Report report(@RequestParam(required = false) LocalDate from,
                                       @RequestParam(required = false) LocalDate to) {
        return reportService.report(from, to);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleBadInput(IllegalArgumentException ex) {
        return new ErrorResponse(ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleBadDate(MethodArgumentTypeMismatchException ex) {
        return new ErrorResponse("Invalid date: " + ex.getValue());
    }

    public record ErrorResponse(String message) {
    }
}
