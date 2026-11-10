package com.erpschool.fee;

import com.erpschool.fee.entity.FeeChallan;
import org.junit.jupiter.api.Test;

import com.erpschool.fee.entity.ChallanStatus;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class FeeChallanTest {

    @Test
    void totalIsTuitionPlusOutstandingPlusExtraMinusDiscount() {
        FeeChallan c = new FeeChallan();
        c.setTuitionFee(new BigDecimal("5000"));
        c.setPreviousOutstanding(new BigDecimal("1500"));
        c.setAdditionalCharges(new BigDecimal("200"));
        c.setDiscountAmount(new BigDecimal("700"));
        c.recomputeTotal();
        assertThat(c.getTotalPayable()).isEqualByComparingTo("6000");
    }

    @Test
    void totalDoesNotGoNegative() {
        FeeChallan c = new FeeChallan();
        c.setTuitionFee(new BigDecimal("1000"));
        c.setDiscountAmount(new BigDecimal("5000"));
        c.recomputeTotal();
        assertThat(c.getTotalPayable()).isEqualByComparingTo("0");
    }

    @Test
    void unpaidChallanIsPastDueAfterDueDate() {
        FeeChallan c = new FeeChallan();
        c.setStatus(ChallanStatus.UNPAID);
        c.setDueDate(LocalDate.of(2026, 9, 1));
        assertThat(c.isPastDue(LocalDate.of(2026, 9, 2))).isTrue();
        assertThat(c.isPastDue(LocalDate.of(2026, 9, 1))).isFalse();
        c.setStatus(ChallanStatus.PAID);
        assertThat(c.isPastDue(LocalDate.of(2026, 9, 10))).isFalse();
    }
}
