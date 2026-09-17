package com.erpschool.homework.repository;

import com.erpschool.homework.entity.Homework;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface HomeworkRepository extends JpaRepository<Homework, UUID> {
    List<Homework> findByTenantIdAndClassIdAndSectionIdOrderByDueDateDesc(UUID tenantId, UUID classId, UUID sectionId);
    List<Homework> findByTenantIdAndTeacherUserIdOrderByDueDateDesc(UUID tenantId, UUID teacherUserId);
}
