package com.erpschool.salary.service;

import com.erpschool.attendance.entity.SalaryDeduction;
import com.erpschool.attendance.repository.SalaryDeductionRepository;
import com.erpschool.common.exception.BusinessException;
import com.erpschool.common.exception.ResourceNotFoundException;
import com.erpschool.common.util.TenantGuard;
import com.erpschool.salary.entity.SalaryStatus;
import com.erpschool.salary.entity.StaffProfile;
import com.erpschool.salary.entity.StaffSalary;
import com.erpschool.salary.repository.StaffProfileRepository;
import com.erpschool.salary.repository.StaffSalaryRepository;
import com.erpschool.tenant.context.TenantContext;
import com.erpschool.user.entity.User;
import com.erpschool.user.entity.UserRole;
import com.erpschool.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class SalaryService {

    private static final Set<UserRole> STAFF = Set.of(
            UserRole.SCHOOL_ADMIN, UserRole.PRINCIPAL, UserRole.TEACHER, UserRole.ACCOUNT_OFFICER);

    private final StaffProfileRepository profileRepository;
    private final StaffSalaryRepository salaryRepository;
    private final SalaryDeductionRepository deductionRepository;
    private final UserRepository userRepository;

    public SalaryService(StaffProfileRepository profileRepository,
                         StaffSalaryRepository salaryRepository,
                         SalaryDeductionRepository deductionRepository,
                         UserRepository userRepository) {
        this.profileRepository = profileRepository;
        this.salaryRepository = salaryRepository;
        this.deductionRepository = deductionRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public StaffProfile upsertProfile(UUID userId, BigDecimal baseSalary) {
        UUID tenantId = TenantGuard.requireTenantId(null);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
        TenantGuard.assertSameTenant(user.getTenantId());
        if (!STAFF.contains(user.getRole())) {
            throw new BusinessException("Salary profiles are only for school staff (not parents/students)");
        }
        StaffProfile p = profileRepository.findByTenantIdAndUserId(tenantId, userId).orElseGet(StaffProfile::new);
        p.setTenantId(tenantId);
        p.setUserId(userId);
        p.setBaseSalary(baseSalary);
        if (p.getId() == null) {
            p.setCreatedBy(TenantContext.getUserId());
        } else {
            p.setUpdatedBy(TenantContext.getUserId());
        }
        return profileRepository.save(p);
    }

    @Transactional(readOnly = true)
    public List<StaffProfile> profiles() {
        return profileRepository.findByTenantId(TenantGuard.requireTenantId(null));
    }

    @Transactional
    public List<StaffSalary> generateMonth(LocalDate month) {
        UUID tenantId = TenantGuard.requireTenantId(null);
        LocalDate monthStart = month.withDayOfMonth(1);
        List<StaffSalary> out = new ArrayList<>();
        for (StaffProfile profile : profileRepository.findByTenantId(tenantId)) {
            StaffSalary row = salaryRepository
                    .findByTenantIdAndStaffUserIdAndMonth(tenantId, profile.getUserId(), monthStart)
                    .orElseGet(StaffSalary::new);
            BigDecimal deductions = deductionRepository
                    .findByTenantIdAndTeacherUserIdAndMonth(tenantId, profile.getUserId(), monthStart)
                    .stream()
                    .filter(SalaryDeduction::isDeduct)
                    .map(SalaryDeduction::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            row.setTenantId(tenantId);
            row.setStaffUserId(profile.getUserId());
            row.setMonth(monthStart);
            row.setBaseSalary(profile.getBaseSalary());
            row.setAttendanceDeductions(deductions);
            if (row.getOtherDeductions() == null) row.setOtherDeductions(BigDecimal.ZERO);
            if (row.getBonuses() == null) row.setBonuses(BigDecimal.ZERO);
            if (row.getStatus() == null) row.setStatus(SalaryStatus.UNPAID);
            row.recompute();
            if (row.getId() == null) {
                row.setCreatedBy(TenantContext.getUserId());
            } else {
                row.setUpdatedBy(TenantContext.getUserId());
            }
            out.add(salaryRepository.save(row));
        }
        return out;
    }

    @Transactional
    public StaffSalary adjust(UUID id, BigDecimal otherDeductions, BigDecimal bonuses, String notes) {
        StaffSalary s = salaryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Salary", id));
        TenantGuard.assertSameTenant(s.getTenantId());
        if (s.getStatus() == SalaryStatus.PAID) {
            throw new BusinessException("Cannot change a paid salary");
        }
        if (otherDeductions != null) {
            s.setOtherDeductions(otherDeductions);
        }
        if (bonuses != null) {
            s.setBonuses(bonuses);
        }
        if (notes != null) {
            s.setNotes(notes);
        }
        s.recompute();
        s.setUpdatedBy(TenantContext.getUserId());
        return salaryRepository.save(s);
    }

    @Transactional
    public StaffSalary markPaid(UUID id, LocalDate paymentDate) {
        StaffSalary s = salaryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Salary", id));
        TenantGuard.assertSameTenant(s.getTenantId());
        s.setStatus(SalaryStatus.PAID);
        s.setPaymentDate(paymentDate == null ? LocalDate.now() : paymentDate);
        s.setUpdatedBy(TenantContext.getUserId());
        return salaryRepository.save(s);
    }

    @Transactional(readOnly = true)
    public List<StaffSalary> month(LocalDate month) {
        return salaryRepository.findByTenantIdAndMonth(TenantGuard.requireTenantId(null), month.withDayOfMonth(1));
    }

    @Transactional(readOnly = true)
    public List<StaffSalary> mine() {
        return salaryRepository.findByTenantIdAndStaffUserIdOrderByMonthDesc(
                TenantGuard.requireTenantId(null), TenantContext.getUserId());
    }
}
