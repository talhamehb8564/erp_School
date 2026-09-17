package com.erpschool.fee;

import com.erpschool.fee.entity.FeeChallan;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

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
}
