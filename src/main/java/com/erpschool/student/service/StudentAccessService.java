package com.erpschool.student.service;

import com.erpschool.common.exception.ForbiddenException;
import com.erpschool.common.exception.ResourceNotFoundException;
import com.erpschool.common.util.TenantGuard;
import com.erpschool.student.entity.Student;
import com.erpschool.student.repository.ParentStudentRepository;
import com.erpschool.student.repository.StudentRepository;
import com.erpschool.tenant.context.TenantContext;
import com.erpschool.user.entity.UserRole;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class StudentAccessService {

    private final StudentRepository studentRepository;
    private final ParentStudentRepository parentStudentRepository;

    public StudentAccessService(StudentRepository studentRepository,
                                ParentStudentRepository parentStudentRepository) {
        this.studentRepository = studentRepository;
        this.parentStudentRepository = parentStudentRepository;
    }

    public Student requireStudent(UUID id) {
        Student student = studentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Student", id));
        TenantGuard.assertSameTenant(student.getTenantId());
        assertCanView(student);
        return student;
    }

    public void assertCanView(Student student) {
        UserRole role = TenantContext.getRole();
        if (role == UserRole.STUDENT) {
            if (student.getUserId() == null || !student.getUserId().equals(TenantContext.getUserId())) {
                throw new ForbiddenException("Students can only access their own record");
            }
        } else if (role == UserRole.PARENT) {
            if (!parentStudentRepository.existsByTenantIdAndParentUserIdAndStudentId(
                    student.getTenantId(), TenantContext.getUserId(), student.getId())) {
                throw new ForbiddenException("Parents can only access linked children");
            }
        }
    }

    public boolean isLinkedParent(UUID tenantId, UUID parentUserId, UUID studentId) {
        return parentStudentRepository.existsByTenantIdAndParentUserIdAndStudentId(tenantId, parentUserId, studentId);
    }
}
