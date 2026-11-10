package com.erpschool.homework;

import com.erpschool.academic.service.AcademicService;
import com.erpschool.homework.repository.HomeworkAttachmentRepository;
import com.erpschool.homework.repository.HomeworkRepository;
import com.erpschool.homework.repository.HomeworkSubmissionRepository;
import com.erpschool.homework.service.HomeworkService;
import com.erpschool.notification.service.NotificationService;
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

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HomeworkMineGuardTest {

    @Mock private HomeworkRepository homeworkRepository;
    @Mock private HomeworkAttachmentRepository attachmentRepository;
    @Mock private HomeworkSubmissionRepository submissionRepository;
    @Mock private AcademicService academicService;
    @Mock private StudentRepository studentRepository;
    @Mock private StudentAccessService studentAccessService;
    @Mock private ParentStudentRepository parentStudentRepository;
    @Mock private NotificationService notificationService;

    @InjectMocks
    private HomeworkService homeworkService;

    private final UUID tenantId = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private final UUID userId = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @AfterEach
    void clear() {
        TenantContext.clear();
    }

    @Test
    void studentWithoutClassGetsEmptyHomeworkNotNpe() {
        TenantContext.set(tenantId, userId, "student1", UserRole.STUDENT);
        Student me = new Student();
        me.setId(UUID.fromString("33333333-3333-3333-3333-333333333333"));
        me.setUserId(userId);
        me.setTenantId(tenantId);
        when(studentRepository.findByTenantIdAndUserId(tenantId, userId)).thenReturn(Optional.of(me));

        assertThat(homeworkService.mine(8)).isEmpty();
        verify(homeworkRepository, never()).findByTenantIdAndClassIdAndSectionIdOrderByDueDateDesc(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }
}
