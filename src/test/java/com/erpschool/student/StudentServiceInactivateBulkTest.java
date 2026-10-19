package com.erpschool.student;

import com.erpschool.academic.entity.SchoolClass;
import com.erpschool.academic.entity.Section;
import com.erpschool.academic.service.AcademicService;
import com.erpschool.audit.service.AuditService;
import com.erpschool.common.exception.BusinessException;
import com.erpschool.common.exception.ForbiddenException;
import com.erpschool.common.service.DocumentSequenceService;
import com.erpschool.student.entity.StudentStatus;
import com.erpschool.student.repository.ParentStudentRepository;
import com.erpschool.student.repository.StudentRepository;
import com.erpschool.student.repository.StudentStatusEventRepository;
import com.erpschool.student.service.StudentAccessService;
import com.erpschool.student.service.StudentService;
import com.erpschool.tenant.context.TenantContext;
import com.erpschool.tenant.repository.TenantRepository;
import com.erpschool.user.entity.UserRole;
import com.erpschool.user.entity.UserStatus;
import com.erpschool.user.repository.UserRepository;
import com.erpschool.user.service.UserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StudentServiceInactivateBulkTest {

    @Mock private StudentRepository studentRepository;
    @Mock private ParentStudentRepository parentStudentRepository;
    @Mock private StudentAccessService studentAccessService;
    @Mock private UserService userService;
    @Mock private UserRepository userRepository;
    @Mock private TenantRepository tenantRepository;
    @Mock private AcademicService academicService;
    @Mock private DocumentSequenceService documentSequenceService;
    @Mock private AuditService auditService;
    @Mock private StudentStatusEventRepository statusEventRepository;

    @InjectMocks
    private StudentService studentService;

    private final UUID tenantId = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private final UUID actor = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private final UUID campusA = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private final UUID campusB = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private final UUID classId = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private final UUID sectionId = UUID.fromString("44444444-4444-4444-4444-444444444444");

    @AfterEach
    void clear() {
        TenantContext.clear();
    }

    @Test
    void bulkInactivateUpdatesOnceAndDoesNotDelete() {
        TenantContext.set(tenantId, actor, "admin", UserRole.SCHOOL_ADMIN, campusA);
        SchoolClass clazz = new SchoolClass();
        clazz.setId(classId);
        clazz.setName("Class 10");
        clazz.setCampusId(campusA);
        clazz.setTenantId(tenantId);
        when(academicService.requireClass(classId)).thenReturn(clazz);
        List<UUID> userIds = List.of(
                UUID.fromString("55555555-5555-5555-5555-555555555555"),
                UUID.fromString("66666666-6666-6666-6666-666666666666"));
        when(studentRepository.countInScope(tenantId, classId, null, campusA, StudentStatus.ACTIVE)).thenReturn(42L);
        when(studentRepository.findUserIdsInScope(tenantId, classId, null, campusA, StudentStatus.ACTIVE)).thenReturn(userIds);
        when(studentRepository.bulkUpdateStatusInScope(
                eq(tenantId), eq(classId), eq(null), eq(campusA),
                eq(StudentStatus.ACTIVE), eq(StudentStatus.INACTIVE),
                eq("UNPAID_FEES"), eq(null), eq(actor), any(Instant.class))).thenReturn(42);
        when(userRepository.updateStatusForIds(eq(tenantId), eq(userIds), eq(UserStatus.INACTIVE), any(Instant.class), eq(actor)))
                .thenReturn(2);

        Map<String, Object> result = studentService.inactivateBulk(classId, null, "UNPAID_FEES", null);

        assertThat(result.get("inactivated")).isEqualTo(42);
        assertThat(result.get("className")).isEqualTo("Class 10");
        assertThat(result.get("reason")).isEqualTo("UNPAID_FEES");
        verify(statusEventRepository).insertBulkEvents(
                eq(tenantId), eq(classId), eq(false), any(), eq(true), eq(campusA),
                eq("ACTIVE"), eq("INACTIVE"), eq("UNPAID_FEES"), eq(null), eq(actor), eq("admin"), any(Instant.class));
        verify(studentRepository).bulkUpdateStatusInScope(
                eq(tenantId), eq(classId), eq(null), eq(campusA),
                eq(StudentStatus.ACTIVE), eq(StudentStatus.INACTIVE),
                eq("UNPAID_FEES"), eq(null), eq(actor), any(Instant.class));
        verify(studentRepository, never()).save(any());
        verify(studentRepository, never()).saveAll(any());
        verify(studentRepository, never()).delete(any());
        verify(studentRepository, never()).deleteAll(any());
        verify(userRepository).updateStatusForIds(eq(tenantId), eq(userIds), eq(UserStatus.INACTIVE), any(Instant.class), eq(actor));
    }

    @Test
    void bulkInactivateRejectsOtherCampus() {
        TenantContext.set(tenantId, actor, "admin", UserRole.SCHOOL_ADMIN, campusA);
        SchoolClass clazz = new SchoolClass();
        clazz.setId(classId);
        clazz.setName("Class 10");
        clazz.setCampusId(campusB);
        clazz.setTenantId(tenantId);
        when(academicService.requireClass(classId)).thenReturn(clazz);

        assertThatThrownBy(() -> studentService.inactivateBulk(classId, null, "UNPAID_FEES", null))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("campus");
        verify(studentRepository, never()).bulkUpdateStatusInScope(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void bulkInactivateRejectsSectionFromAnotherClass() {
        TenantContext.set(tenantId, actor, "admin", UserRole.SCHOOL_ADMIN, campusA);
        SchoolClass clazz = new SchoolClass();
        clazz.setId(classId);
        clazz.setName("Class 10");
        clazz.setCampusId(campusA);
        clazz.setTenantId(tenantId);
        Section other = new Section();
        other.setId(sectionId);
        other.setClassId(UUID.fromString("77777777-7777-7777-7777-777777777777"));
        when(academicService.requireClass(classId)).thenReturn(clazz);
        when(academicService.requireSection(sectionId)).thenReturn(other);

        assertThatThrownBy(() -> studentService.inactivateBulk(classId, sectionId, "GRADUATED", null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Section");
        verify(studentRepository, never()).bulkUpdateStatusInScope(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void bulkInactivateFailsWhenNoActiveStudents() {
        TenantContext.set(tenantId, actor, "admin", UserRole.SCHOOL_ADMIN, campusA);
        SchoolClass clazz = new SchoolClass();
        clazz.setId(classId);
        clazz.setName("Class 10");
        clazz.setCampusId(campusA);
        clazz.setTenantId(tenantId);
        when(academicService.requireClass(classId)).thenReturn(clazz);
        when(studentRepository.countInScope(tenantId, classId, null, campusA, StudentStatus.ACTIVE)).thenReturn(0L);

        assertThatThrownBy(() -> studentService.inactivateBulk(classId, null, "UNPAID_FEES", null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("No active students");
        verify(studentRepository, never()).bulkUpdateStatusInScope(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }
}
