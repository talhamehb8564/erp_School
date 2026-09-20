package com.erpschool.fee.service;

import com.erpschool.common.exception.BusinessException;
import com.erpschool.common.exception.ResourceNotFoundException;
import com.erpschool.common.service.DocumentSequenceService;
import com.erpschool.common.util.TenantGuard;
import com.erpschool.fee.entity.ChallanStatus;
import com.erpschool.fee.entity.FeeChallan;
import com.erpschool.fee.entity.FeePaymentProof;
import com.erpschool.fee.entity.FeeStructure;
import com.erpschool.fee.repository.ChallanChargeRepository;
import com.erpschool.fee.repository.FeeChallanRepository;
import com.erpschool.fee.repository.FeePaymentProofRepository;
import com.erpschool.fee.repository.FeeStructureRepository;
import com.erpschool.notification.service.NotificationService;
import com.erpschool.student.entity.Student;
import com.erpschool.student.entity.StudentStatus;
import com.erpschool.student.repository.ParentStudentRepository;
import com.erpschool.student.repository.StudentRepository;
import com.erpschool.student.service.StudentAccessService;
import com.erpschool.tenant.context.TenantContext;
import com.erpschool.tenant.entity.Tenant;
import com.erpschool.tenant.repository.TenantRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class FeeService {

    private static final EnumSet<ChallanStatus> OUTSTANDING = EnumSet.of(
            ChallanStatus.UNPAID, ChallanStatus.PAYMENT_UNDER_VERIFICATION,
            ChallanStatus.PAYMENT_REJECTED, ChallanStatus.OVERDUE);

    private final FeeStructureRepository structureRepository;
    private final FeeChallanRepository challanRepository;
    private final ChallanChargeRepository chargeRepository;
    private final FeePaymentProofRepository proofRepository;
    private final StudentRepository studentRepository;
    private final StudentAccessService studentAccessService;
    private final ParentStudentRepository parentStudentRepository;
    private final DocumentSequenceService documentSequenceService;
    private final TenantRepository tenantRepository;
    private final NotificationService notificationService;

    public FeeService(FeeStructureRepository structureRepository,
                      FeeChallanRepository challanRepository,
                      ChallanChargeRepository chargeRepository,
                      FeePaymentProofRepository proofRepository,
                      StudentRepository studentRepository,
                      StudentAccessService studentAccessService,
                      ParentStudentRepository parentStudentRepository,
                      DocumentSequenceService documentSequenceService,
                      TenantRepository tenantRepository,
                      NotificationService notificationService) {
        this.structureRepository = structureRepository;
        this.challanRepository = challanRepository;
        this.chargeRepository = chargeRepository;
        this.proofRepository = proofRepository;
        this.studentRepository = studentRepository;
        this.studentAccessService = studentAccessService;
        this.parentStudentRepository = parentStudentRepository;
        this.documentSequenceService = documentSequenceService;
        this.tenantRepository = tenantRepository;
        this.notificationService = notificationService;
    }

    @Transactional
    public FeeStructure saveStructure(FeeStructure body) {
        UUID tenantId = TenantGuard.requireTenantId(null);
        if (body.getId() == null) {
            body.setTenantId(tenantId);
            body.setCreatedBy(TenantContext.getUserId());
        } else {
            FeeStructure existing = structureRepository.findById(body.getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Fee structure", body.getId()));
            TenantGuard.assertSameTenant(existing.getTenantId());
            existing.setName(body.getName());
            existing.setClassId(body.getClassId());
            existing.setAcademicYear(body.getAcademicYear());
            existing.setTuitionAmount(body.getTuitionAmount());
            existing.setUpdatedBy(TenantContext.getUserId());
            return structureRepository.save(existing);
        }
        return structureRepository.save(body);
    }

    @Transactional(readOnly = true)
    public List<FeeStructure> structures() {
        return structureRepository.findByTenantIdOrderByNameAsc(TenantGuard.requireTenantId(null));
    }

    @Transactional
    public List<Map<String, Object>> generateMonthly(UUID classId, LocalDate month, LocalDate dueDate,
                                                     BigDecimal extraCharges, BigDecimal discount) {
        UUID tenantId = TenantGuard.requireTenantId(null);
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new BusinessException("School not found"));
        LocalDate monthStart = month.withDayOfMonth(1);
        LocalDate issue = LocalDate.now();
        LocalDate due = dueDate == null ? monthStart.plusDays(10) : dueDate;
        FeeStructure structure = structureRepository.findFirstByTenantIdAndClassId(tenantId, classId)
                .orElseThrow(() -> new BusinessException("No fee structure for this class"));
        List<Student> students = studentRepository.findByTenantIdAndClassIdAndStatus(
                tenantId, classId, StudentStatus.ACTIVE);
        List<Map<String, Object>> created = new ArrayList<>();
        for (Student student : students) {
            if (challanRepository.findByTenantIdAndStudentIdAndMonth(tenantId, student.getId(), monthStart).isPresent()) {
                continue;
            }
            BigDecimal outstanding = challanRepository
                    .findByTenantIdAndStudentIdAndStatusIn(tenantId, student.getId(), List.copyOf(OUTSTANDING))
                    .stream()
                    .filter(c -> c.getMonth().isBefore(monthStart))
                    .map(FeeChallan::getTotalPayable)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            FeeChallan challan = new FeeChallan();
            challan.setTenantId(tenantId);
            challan.setStudentId(student.getId());
            challan.setChallanNumber(documentSequenceService.nextFormatted(
                    tenantId, "CHL", tenant.getCode().toUpperCase() + "-CHL-"));
            challan.setMonth(monthStart);
            challan.setIssueDate(issue);
            challan.setDueDate(due);
            challan.setTuitionFee(structure.getTuitionAmount());
            challan.setPreviousOutstanding(outstanding);
            challan.setDiscountAmount(discount == null ? BigDecimal.ZERO : discount);
            challan.setAdditionalCharges(extraCharges == null ? BigDecimal.ZERO : extraCharges);
            challan.recomputeTotal();
            challan.setStatus(ChallanStatus.UNPAID);
            challan.setCreatedBy(TenantContext.getUserId());
            challan = challanRepository.save(challan);
            notifyChallan(student, challan);
            created.add(toMap(challan));
        }
        return created;
    }

    @Transactional
    public List<Map<String, Object>> studentChallans(UUID studentId) {
        Student student = studentAccessService.requireStudent(studentId);
        LocalDate today = LocalDate.now();
        return challanRepository.findByTenantIdAndStudentIdOrderByMonthDesc(student.getTenantId(), studentId)
                .stream()
                .map(c -> {
                    if (c.isPastDue(today)) {
                        c.setStatus(ChallanStatus.OVERDUE);
                        c.setUpdatedBy(TenantContext.getUserId());
                        challanRepository.save(c);
                    }
                    return toMap(c);
                })
                .toList();
    }

    @Transactional
    public Map<String, Object> submitProof(UUID challanId, String slipUrl, String transactionRef) {
        FeeChallan challan = requireChallan(challanId);
        studentAccessService.requireStudent(challan.getStudentId());
        if (challan.getStatus() == ChallanStatus.PAID) {
            throw new BusinessException("Challan is already paid");
        }
        FeePaymentProof proof = new FeePaymentProof();
        proof.setTenantId(challan.getTenantId());
        proof.setChallanId(challan.getId());
        proof.setSlipUrl(slipUrl);
        proof.setTransactionRef(transactionRef);
        proof.setStatus(ChallanStatus.PAYMENT_UNDER_VERIFICATION);
        proof.setCreatedBy(TenantContext.getUserId());
        proofRepository.save(proof);
        challan.setStatus(ChallanStatus.PAYMENT_UNDER_VERIFICATION);
        challan.setUpdatedBy(TenantContext.getUserId());
        challanRepository.save(challan);
        return toMap(challan);
    }

    @Transactional
    public Map<String, Object> reviewProof(UUID proofId, boolean approve, String remarks) {
        FeePaymentProof proof = proofRepository.findById(proofId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment proof", proofId));
        TenantGuard.assertSameTenant(proof.getTenantId());
        FeeChallan challan = requireChallan(proof.getChallanId());
        proof.setReviewedBy(TenantContext.getUserId());
        proof.setReviewedAt(Instant.now());
        proof.setRemarks(remarks);
        if (approve) {
            proof.setStatus(ChallanStatus.PAID);
            challan.setStatus(ChallanStatus.PAID);
        } else {
            proof.setStatus(ChallanStatus.PAYMENT_REJECTED);
            challan.setStatus(ChallanStatus.PAYMENT_REJECTED);
        }
        proofRepository.save(proof);
        challan.setUpdatedBy(TenantContext.getUserId());
        challanRepository.save(challan);
        Student student = studentRepository.findById(challan.getStudentId()).orElse(null);
        if (student != null) {
            String title = approve ? "Fee payment approved" : "Fee payment rejected";
            notificationService.notifyUser(challan.getTenantId(), student.getUserId(), "FEE", title, remarks,
                    "FeeChallan", challan.getId().toString());
            parentStudentRepository.findByTenantIdAndStudentId(challan.getTenantId(), student.getId())
                    .forEach(link -> notificationService.notifyUser(challan.getTenantId(), link.getParentUserId(),
                            "FEE", title, remarks, "FeeChallan", challan.getId().toString()));
        }
        return toMap(challan);
    }

    @Transactional(readOnly = true)
    public List<FeePaymentProof> pendingProofs() {
        return proofRepository.findByTenantIdAndStatusOrderByCreatedAtDesc(
                TenantGuard.requireTenantId(null), ChallanStatus.PAYMENT_UNDER_VERIFICATION);
    }

    @Transactional(readOnly = true)
    public List<FeeChallan> byStatus(ChallanStatus status) {
        return challanRepository.findByTenantIdAndStatus(TenantGuard.requireTenantId(null), status);
    }

    @Transactional
    public List<FeeChallan> markOverdue() {
        UUID tenantId = TenantGuard.requireTenantId(null);
        LocalDate today = LocalDate.now();
        List<FeeChallan> unpaid = challanRepository.findByTenantIdAndStatusAndDueDateBefore(
                tenantId, ChallanStatus.UNPAID, today);
        List<FeeChallan> updated = new ArrayList<>();
        for (FeeChallan challan : unpaid) {
            if (!challan.isPastDue(today)) {
                continue;
            }
            challan.setStatus(ChallanStatus.OVERDUE);
            challan.setUpdatedBy(TenantContext.getUserId());
            updated.add(challanRepository.save(challan));
        }
        return updated;
    }

    private FeeChallan requireChallan(UUID id) {
        FeeChallan c = challanRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Challan", id));
        TenantGuard.assertSameTenant(c.getTenantId());
        return c;
    }

    private void notifyChallan(Student student, FeeChallan challan) {
        notificationService.notifyUser(challan.getTenantId(), student.getUserId(), "FEE",
                "Fee challan " + challan.getChallanNumber(),
                "Amount payable: " + challan.getTotalPayable(),
                "FeeChallan", challan.getId().toString());
        parentStudentRepository.findByTenantIdAndStudentId(challan.getTenantId(), student.getId())
                .forEach(link -> notificationService.notifyUser(challan.getTenantId(), link.getParentUserId(),
                        "FEE", "Fee challan " + challan.getChallanNumber(),
                        "Amount payable: " + challan.getTotalPayable(),
                        "FeeChallan", challan.getId().toString()));
    }

    private Map<String, Object> toMap(FeeChallan c) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", c.getId());
        m.put("studentId", c.getStudentId());
        m.put("challanNumber", c.getChallanNumber());
        m.put("month", c.getMonth());
        m.put("issueDate", c.getIssueDate());
        m.put("dueDate", c.getDueDate());
        m.put("tuitionFee", c.getTuitionFee());
        m.put("previousOutstanding", c.getPreviousOutstanding());
        m.put("discountAmount", c.getDiscountAmount());
        m.put("additionalCharges", c.getAdditionalCharges());
        m.put("totalPayable", c.getTotalPayable());
        m.put("status", c.getStatus());
        m.put("charges", chargeRepository.findByChallanId(c.getId()));
        m.put("proofs", proofRepository.findByChallanIdOrderByCreatedAtDesc(c.getId()));
        return m;
    }
}
