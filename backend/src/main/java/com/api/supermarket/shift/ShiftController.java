package com.api.supermarket.shift;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.api.supermarket.common.AppTime;
import com.api.supermarket.security.JwtPrincipal;
import com.api.supermarket.shift.ShiftService.CloseRequest;
import com.api.supermarket.shift.ShiftService.ShiftSummary;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/shifts")
public class ShiftController {
    private final ShiftService shifts;
    private final AppTime time;

    public ShiftController(ShiftService shifts, AppTime time) {
        this.shifts = shifts;
        this.time = time;
    }

    @GetMapping("/me/today")
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
    public ShiftSummary myToday(@AuthenticationPrincipal JwtPrincipal me) {
        return shifts.today(me);
    }

    @PostMapping("/me/close")
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
    public ShiftClosing close(@AuthenticationPrincipal JwtPrincipal me, @Valid @RequestBody CloseRequest req) {
        return shifts.close(me, req);
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public List<ShiftClosing> list(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate day) {
        return shifts.list(day == null ? time.today() : day);
    }

    @GetMapping("/cashier/{cashierId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ShiftSummary cashierSummary(@PathVariable String cashierId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate day) {
        return shifts.summary(cashierId, day == null ? time.today() : day);
    }

    @PostMapping("/{id}/reopen")
    @PreAuthorize("hasRole('ADMIN')")
    public ShiftClosing reopen(@PathVariable String id, @AuthenticationPrincipal JwtPrincipal admin) {
        return shifts.reopen(id, admin);
    }
}
