package com.erpschool.student;

import com.erpschool.academic.repository.TeacherAssignmentRepository;
import com.erpschool.common.exception.ForbiddenException;
import com.erpschool.student.entity.Student;
import com.erpschool.student.repository.ParentStudentRepository;
import com.erpschool.student.repository.StudentRepository;
import com.erpschool.student.service.StudentAccessService;
import com.erpschool.tenant.context.TenantContext;
import com.erpschool.user.entity.UserRole;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StudentAccessServiceTest {

    @Mock
    private StudentRepository studentRepository;
    @Mock
    private ParentStudentRepository parentStudentRepository;
    @Mock
    private TeacherAssignmentRepository assignmentRepository;
    @InjectMocks
    private StudentAccessService studentAccessService;

    private final UUID schoolA = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @AfterEach
    void clear() {
        TenantContext.clear();
    }

    @Test
    void parentCannotViewUnlinkedChild() {
        UUID parentId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        TenantContext.set(schoolA, parentId, "GVS-PAR-0001", UserRole.PARENT);
        Student student = new Student();
        student.setId(studentId);
        student.setTenantId(schoolA);
        when(parentStudentRepository.existsByTenantIdAndParentUserIdAndStudentId(schoolA, parentId, studentId))
                .thenReturn(false);
        assertThatThrownBy(() -> studentAccessService.assertCanView(student))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void studentCannotViewAnotherStudent() {
        UUID me = UUID.randomUUID();
        TenantContext.set(schoolA, me, "GVS-STU-0001", UserRole.STUDENT);
        Student other = new Student();
        other.setId(UUID.randomUUID());
        other.setTenantId(schoolA);
        other.setUserId(UUID.randomUUID());
        assertThatThrownBy(() -> studentAccessService.assertCanView(other))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void teacherCannotViewUnassignedClass() {
        UUID teacherId = UUID.randomUUID();
        TenantContext.set(schoolA, teacherId, "GVS-TCH-0001", UserRole.TEACHER);
        Student student = new Student();
        student.setId(UUID.randomUUID());
        student.setTenantId(schoolA);
        student.setClassId(UUID.randomUUID());
        student.setSectionId(UUID.randomUUID());
        when(assignmentRepository.existsByTenantIdAndTeacherUserIdAndClassIdAndSectionId(
                schoolA, teacherId, student.getClassId(), student.getSectionId())).thenReturn(false);
        assertThatThrownBy(() -> studentAccessService.assertCanView(student))
                .isInstanceOf(ForbiddenException.class);
    }
}
