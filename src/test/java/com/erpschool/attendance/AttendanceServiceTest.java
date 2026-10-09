package com.erpschool.attendance;

import com.erpschool.attendance.service.AttendanceService;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class AttendanceServiceTest {

    @Test
    void isoDayMondayIs1SundayIs7() {
        assertThat(AttendanceService.isoDay(LocalDate.of(2026, 9, 14))).isEqualTo(1);
        assertThat(AttendanceService.isoDay(LocalDate.of(2026, 9, 20))).isEqualTo(7);
    }
}
