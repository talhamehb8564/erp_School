package com.erpschool.fee.service;

import com.erpschool.common.exception.BusinessException;
import com.erpschool.common.exception.ResourceNotFoundException;
import com.erpschool.common.service.DocumentSequenceService;
import com.erpschool.common.util.TenantGuard;
import com.erpschool.fee.entity.ChallanCharge;
import com.erpschool.fee.entity.ChallanStatus;
import com.erpschool.fee.entity.FeeChallan;
import com.erpschool.fee.entity.FeePaymentProof;
import com.erpschool.fee.entity.FeeStructure;
import com.erpschool.fee.repository.ChallanChargeRepository;
import com.erpschool.fee.repository.FeeChallanRepository;
import com.erpschool.fee.repository.FeePaymentProofRepository;
import com.erpschool.fee.repository.FeeStructureRepository;
import com.erpschool.campus.entity.Campus;
import com.erpschool.campus.repository.CampusRepository;
import com.erpschool.notification.service.NotificationService;
import com.erpschool.student.entity.ParentStudent;
import com.erpschool.student.entity.Student;
import com.erpschool.student.entity.StudentStatus;
import com.erpschool.student.repository.ParentStudentRepository;
import com.erpschool.student.repository.StudentRepository;
import com.erpschool.student.service.StudentAccessService;
import com.erpschool.tenant.context.TenantContext;
import com.erpschool.tenant.entity.Tenant;
import com.erpschool.tenant.repository.TenantRepository;
import com.erpschool.user.entity.User;
import com.erpschool.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

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
    private final UserRepository userRepository;
    private final CampusRepository campusRepository;

    public FeeService(FeeStructureRepository structureRepository,
                      FeeChallanRepository challanRepository,
                      ChallanChargeRepository chargeRepository,
                      FeePaymentProofRepository proofRepository,
                      StudentRepository studentRepository,
                      StudentAccessService studentAccessService,
                      ParentStudentRepository parentStudentRepository,
                      DocumentSequenceService documentSequenceService,
                      TenantRepository tenantRepository,
                      NotificationService notificationService,
                      UserRepository userRepository,
                      CampusRepository campusRepository) {
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
        this.userRepository = userRepository;
        this.campusRepository = campusRepository;
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
                                                     BigDecimal extraCharges, BigDecimal discount, UUID studentId) {
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
        if (studentId != null) {
            students = students.stream().filter(s -> studentId.equals(s.getId())).toList();
            if (students.isEmpty()) {
                throw new BusinessException("STUDENT_NOT_IN_CLASS", "No active student in this class matches the selection");
            }
        }
        Set<UUID> alreadyIssued = challanRepository.findByTenantIdAndMonth(tenantId, monthStart).stream()
                .map(FeeChallan::getStudentId)
                .collect(Collectors.toSet());
        Map<UUID, BigDecimal> outstandingByStudent = new HashMap<>();
        for (FeeChallan existing : challanRepository.findByTenantIdAndStatusIn(tenantId, List.copyOf(OUTSTANDING))) {
            if (existing.getMonth() != null && existing.getMonth().isBefore(monthStart) && existing.getTotalPayable() != null) {
                outstandingByStudent.merge(existing.getStudentId(), existing.getTotalPayable(), BigDecimal::add);
            }
        }
        Map<UUID, List<ParentStudent>> parentsByStudent = students.isEmpty()
                ? Map.of()
                : parentStudentRepository.findByTenantIdAndStudentIdIn(
                        tenantId, students.stream().map(Student::getId).toList()).stream()
                .collect(Collectors.groupingBy(ParentStudent::getStudentId));
        List<FeeChallan> createdRows = new ArrayList<>();
        for (Student student : students) {
            if (alreadyIssued.contains(student.getId())) {
                continue;
            }
            FeeChallan challan = new FeeChallan();
            challan.setTenantId(tenantId);
            challan.setStudentId(student.getId());
            challan.setChallanNumber(documentSequenceService.nextFormatted(
                    tenantId, "CHL", tenant.getCode().toUpperCase() + "-CHL-"));
            challan.setMonth(monthStart);
            challan.setIssueDate(issue);
            challan.setDueDate(due);
            challan.setTuitionFee(structure.getTuitionAmount());
            challan.setPreviousOutstanding(outstandingByStudent.getOrDefault(student.getId(), BigDecimal.ZERO));
            challan.setDiscountAmount(discount == null ? BigDecimal.ZERO : discount);
            challan.setAdditionalCharges(extraCharges == null ? BigDecimal.ZERO : extraCharges);
            challan.recomputeTotal();
            challan.setStatus(ChallanStatus.UNPAID);
            challan.setCreatedBy(TenantContext.getUserId());
            challan = challanRepository.save(challan);
            notifyChallan(student, challan, parentsByStudent.getOrDefault(student.getId(), List.of()));
            createdRows.add(challan);
        }
        return toMaps(createdRows);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> challansForStudents(List<UUID> studentIds) {
        if (studentIds == null || studentIds.isEmpty()) {
            return List.of();
        }
        UUID tenantId = TenantGuard.requireTenantId(null);
        return toMaps(challanRepository.findByTenantIdAndStudentIdInOrderByMonthDesc(tenantId, studentIds));
    }

    @Transactional
    public List<Map<String, Object>> studentChallans(UUID studentId) {
        Student student = studentAccessService.requireStudent(studentId);
        LocalDate today = LocalDate.now();
        List<FeeChallan> rows = challanRepository.findByTenantIdAndStudentIdOrderByMonthDesc(student.getTenantId(), studentId);
        for (FeeChallan c : rows) {
            if (c.isPastDue(today)) {
                c.setStatus(ChallanStatus.OVERDUE);
                c.setUpdatedBy(TenantContext.getUserId());
                challanRepository.save(c);
            }
        }
        return toMaps(rows);
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
    public List<Map<String, Object>> pendingProofs() {
        List<FeePaymentProof> proofs = proofRepository.findByTenantIdAndStatusOrderByCreatedAtDesc(
                TenantGuard.requireTenantId(null), ChallanStatus.PAYMENT_UNDER_VERIFICATION);
        if (proofs.isEmpty()) {
            return List.of();
        }
        Set<UUID> challanIds = proofs.stream().map(FeePaymentProof::getChallanId).collect(Collectors.toSet());
        Map<UUID, FeeChallan> challans = challanRepository.findAllById(challanIds).stream()
                .collect(Collectors.toMap(FeeChallan::getId, c -> c));
        Map<UUID, StudentContext> students = loadStudents(challans.values().stream()
                .map(FeeChallan::getStudentId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet()));
        List<Map<String, Object>> rows = new ArrayList<>();
        for (FeePaymentProof proof : proofs) {
            Map<String, Object> m = new HashMap<>();
            m.put("id", proof.getId());
            m.put("challanId", proof.getChallanId());
            m.put("slipUrl", proof.getSlipUrl());
            m.put("transactionRef", proof.getTransactionRef());
            m.put("status", proof.getStatus());
            m.put("remarks", proof.getRemarks());
            m.put("reviewedAt", proof.getReviewedAt());
            FeeChallan challan = challans.get(proof.getChallanId());
            if (challan != null) {
                m.put("challanNumber", challan.getChallanNumber());
                m.put("month", challan.getMonth());
                m.put("dueDate", challan.getDueDate());
                m.put("totalPayable", challan.getTotalPayable());
                m.put("studentId", challan.getStudentId());
                attachStudent(m, students.get(challan.getStudentId()));
            }
            rows.add(m);
        }
        return rows;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> byStatus(ChallanStatus status) {
        return toMaps(challanRepository.findByTenantIdAndStatus(TenantGuard.requireTenantId(null), status));
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
        notifyChallan(student, challan,
                parentStudentRepository.findByTenantIdAndStudentId(challan.getTenantId(), student.getId()));
    }

    private void notifyChallan(Student student, FeeChallan challan, List<ParentStudent> parents) {
        notificationService.notifyUser(challan.getTenantId(), student.getUserId(), "FEE",
                "Fee challan " + challan.getChallanNumber(),
                "Amount payable: " + challan.getTotalPayable(),
                "FeeChallan", challan.getId().toString());
        for (ParentStudent link : parents) {
            notificationService.notifyUser(challan.getTenantId(), link.getParentUserId(),
                    "FEE", "Fee challan " + challan.getChallanNumber(),
                    "Amount payable: " + challan.getTotalPayable(),
                    "FeeChallan", challan.getId().toString());
        }
    }

    private Map<String, Object> toSummary(FeeChallan c) {
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
        return m;
    }

    private List<Map<String, Object>> toMaps(List<FeeChallan> rows) {
        if (rows.isEmpty()) {
            return List.of();
        }
        List<UUID> ids = rows.stream().map(FeeChallan::getId).toList();
        Map<UUID, List<ChallanCharge>> charges = chargeRepository.findByChallanIdIn(ids).stream()
                .collect(Collectors.groupingBy(ChallanCharge::getChallanId));
        Map<UUID, List<FeePaymentProof>> proofs = proofRepository.findByChallanIdInOrderByCreatedAtDesc(ids).stream()
                .collect(Collectors.groupingBy(FeePaymentProof::getChallanId));
        Map<UUID, StudentContext> students = loadStudents(rows.stream()
                .map(FeeChallan::getStudentId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet()));
        return rows.stream()
                .map(c -> {
                    Map<String, Object> m = toMap(c,
                            charges.getOrDefault(c.getId(), List.of()),
                            proofs.getOrDefault(c.getId(), List.of()));
                    attachStudent(m, students.get(c.getStudentId()));
                    return m;
                })
                .toList();
    }

    private Map<String, Object> toMap(FeeChallan c) {
        Map<String, Object> m = toMap(c, chargeRepository.findByChallanId(c.getId()),
                proofRepository.findByChallanIdOrderByCreatedAtDesc(c.getId()));
        Student student = studentRepository.findById(c.getStudentId()).orElse(null);
        if (student != null) {
            attachStudent(m, loadStudents(Set.of(student.getId())).get(student.getId()));
        }
        return m;
    }

    private Map<String, Object> toMap(FeeChallan c, List<ChallanCharge> charges, List<FeePaymentProof> proofs) {
        Map<String, Object> m = toSummary(c);
        m.put("charges", charges);
        m.put("proofs", proofs);
        return m;
    }

    private Map<UUID, StudentContext> loadStudents(Set<UUID> studentIds) {
        if (studentIds == null || studentIds.isEmpty()) {
            return Map.of();
        }
        List<Student> students = studentRepository.findAllById(studentIds);
        Set<UUID> userIds = new HashSet<>();
        Set<UUID> campusIds = new HashSet<>();
        for (Student student : students) {
            if (student.getUserId() != null) {
                userIds.add(student.getUserId());
            }
            if (student.getCampusId() != null) {
                campusIds.add(student.getCampusId());
            }
        }
        Map<UUID, User> users = userIds.isEmpty()
                ? Map.of()
                : userRepository.findAllById(userIds).stream().collect(Collectors.toMap(User::getId, u -> u));
        Map<UUID, Campus> campuses = campusIds.isEmpty()
                ? Map.of()
                : campusRepository.findAllById(campusIds).stream().collect(Collectors.toMap(Campus::getId, c -> c));
        Map<UUID, StudentContext> out = new HashMap<>();
        for (Student student : students) {
            out.put(student.getId(), new StudentContext(
                    student,
                    student.getUserId() == null ? null : users.get(student.getUserId()),
                    student.getCampusId() == null ? null : campuses.get(student.getCampusId())));
        }
        return out;
    }

    private static void attachStudent(Map<String, Object> m, StudentContext ctx) {
        if (ctx == null || ctx.student == null) {
            return;
        }
        Student student = ctx.student;
        m.put("rollNumber", student.getRollNumber());
        m.put("admissionNumber", student.getAdmissionNumber());
        m.put("classId", student.getClassId());
        m.put("sectionId", student.getSectionId());
        m.put("campusId", student.getCampusId());
        m.put("guardianName", student.getGuardianName());
        if (ctx.user != null) {
            m.put("studentName", ctx.user.getFullName());
        }
        if (ctx.campus != null) {
            m.put("campusName", ctx.campus.getName());
        }
    }

    private record StudentContext(Student student, User user, Campus campus) {
    }
}
