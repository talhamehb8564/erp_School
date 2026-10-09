package com.erpschool.calendar.controller;

import com.erpschool.calendar.entity.SchoolEvent;
import com.erpschool.calendar.service.CalendarService;
import com.erpschool.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/calendar")
@Tag(name = "Calendar")
public class CalendarController {

    private final CalendarService calendarService;

    public CalendarController(CalendarService calendarService) {
        this.calendarService = calendarService;
    }

    @PostMapping("/events")
    @PreAuthorize("hasAnyRole('ERP_OWNER','SCHOOL_ADMIN','PRINCIPAL')")
    public ResponseEntity<ApiResponse<SchoolEvent>> create(@RequestBody SchoolEvent body) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Event created", calendarService.create(body)));
    }

    @GetMapping("/events")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<SchoolEvent>>> list(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(ApiResponse.ok(calendarService.range(from, to)));
    }

    @PutMapping("/events/{id}")
    @PreAuthorize("hasAnyRole('ERP_OWNER','SCHOOL_ADMIN','PRINCIPAL')")
    public ResponseEntity<ApiResponse<SchoolEvent>> update(@PathVariable UUID id, @RequestBody SchoolEvent body) {
        return ResponseEntity.ok(ApiResponse.ok("Event updated", calendarService.update(id, body)));
    }
}
