package com.erpschool.homework.repository;

import com.erpschool.homework.entity.HomeworkAttachment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface HomeworkAttachmentRepository extends JpaRepository<HomeworkAttachment, UUID> {
    List<HomeworkAttachment> findByHomeworkId(UUID homeworkId);
}
