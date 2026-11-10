package com.erpschool.announcement.repository;

import com.erpschool.announcement.entity.Announcement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AnnouncementRepository extends JpaRepository<Announcement, UUID> {
    List<Announcement> findByTenantIdOrderByPublishDateDesc(UUID tenantId);
}
