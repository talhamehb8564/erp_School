package com.erpschool.student.repository;

import com.erpschool.student.entity.Student;
import com.erpschool.student.entity.StudentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StudentRepository extends JpaRepository<Student, UUID> {

    Page<Student> findByTenantId(UUID tenantId, Pageable pageable);

    Page<Student> findByTenantIdAndCampusId(UUID tenantId, UUID campusId, Pageable pageable);

    Page<Student> findByTenantIdAndCampusIdAndClassId(UUID tenantId, UUID campusId, UUID classId, Pageable pageable);

    long countByTenantIdAndCampusId(UUID tenantId, UUID campusId);

    long countByTenantIdAndCampusIdAndStatus(UUID tenantId, UUID campusId, StudentStatus status);

    java.util.Optional<Student> findByTenantIdAndCnic(UUID tenantId, String cnic);

    Page<Student> findByTenantIdAndClassId(UUID tenantId, UUID classId, Pageable pageable);

    Page<Student> findByTenantIdAndClassIdIn(UUID tenantId, Collection<UUID> classIds, Pageable pageable);

    Page<Student> findByTenantIdAndCampusIdAndClassIdIn(UUID tenantId, UUID campusId, Collection<UUID> classIds, Pageable pageable);

    Page<Student> findByTenantIdAndSectionId(UUID tenantId, UUID sectionId, Pageable pageable);

    Page<Student> findByTenantIdAndClassIdAndSectionId(UUID tenantId, UUID classId, UUID sectionId, Pageable pageable);

    List<Student> findByTenantIdAndClassIdAndSectionIdAndStatus(
            UUID tenantId, UUID classId, UUID sectionId, StudentStatus status);

    List<Student> findByTenantIdAndClassIdAndStatus(UUID tenantId, UUID classId, StudentStatus status);

    List<Student> findByTenantIdAndClassIdInAndStatus(UUID tenantId, Collection<UUID> classIds, StudentStatus status);

    List<Student> findByTenantIdAndSectionIdAndStatus(UUID tenantId, UUID sectionId, StudentStatus status);

    List<Student> findByTenantIdAndSectionIdInAndStatus(UUID tenantId, Collection<UUID> sectionIds, StudentStatus status);

    List<Student> findByTenantIdAndClassId(UUID tenantId, UUID classId);

    Optional<Student> findByTenantIdAndUserId(UUID tenantId, UUID userId);

    Optional<Student> findFirstByTenantIdAndRollNumberIgnoreCase(UUID tenantId, String rollNumber);

    List<Student> findByTenantId(UUID tenantId);

    long countByTenantId(UUID tenantId);

    long countByTenantIdAndStatus(UUID tenantId, StudentStatus status);

    long countByTenantIdAndClassId(UUID tenantId, UUID classId);

    @Query("""
            select s.id from Student s
            where s.tenantId = :tenantId
              and s.classId = :classId
              and s.sectionId = :sectionId
              and s.status = :status
            """)
    List<UUID> findIdsByTenantIdAndClassIdAndSectionIdAndStatus(
            @Param("tenantId") UUID tenantId,
            @Param("classId") UUID classId,
            @Param("sectionId") UUID sectionId,
            @Param("status") StudentStatus status);

    @Query("""
            select s from Student s
            where s.tenantId = :tenantId
              and s.classId = :classId
              and (:sectionId is null or s.sectionId = :sectionId)
              and (:campusId is null or s.campusId = :campusId)
              and (:status is null or s.status = :status)
            order by s.rollNumber, s.admissionNumber
            """)
    List<Student> findRoster(
            @Param("tenantId") UUID tenantId,
            @Param("campusId") UUID campusId,
            @Param("classId") UUID classId,
            @Param("sectionId") UUID sectionId,
            @Param("status") StudentStatus status);

    @Query("""
            select count(s) from Student s
            where s.tenantId = :tenantId
              and s.classId = :classId
              and s.status = :status
              and (:sectionId is null or s.sectionId = :sectionId)
              and (:campusId is null or s.campusId = :campusId)
            """)
    long countInScope(
            @Param("tenantId") UUID tenantId,
            @Param("classId") UUID classId,
            @Param("sectionId") UUID sectionId,
            @Param("campusId") UUID campusId,
            @Param("status") StudentStatus status);

    @Query("""
            select s.userId from Student s
            where s.tenantId = :tenantId
              and s.classId = :classId
              and s.status = :status
              and s.userId is not null
              and (:sectionId is null or s.sectionId = :sectionId)
              and (:campusId is null or s.campusId = :campusId)
            """)
    List<UUID> findUserIdsInScope(
            @Param("tenantId") UUID tenantId,
            @Param("classId") UUID classId,
            @Param("sectionId") UUID sectionId,
            @Param("campusId") UUID campusId,
            @Param("status") StudentStatus status);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Student s
            set s.status = :toStatus,
                s.inactiveReason = :reason,
                s.inactiveNote = :note,
                s.updatedBy = :actorId,
                s.updatedAt = :now
            where s.tenantId = :tenantId
              and s.status = :fromStatus
              and s.classId = :classId
              and (:sectionId is null or s.sectionId = :sectionId)
              and (:campusId is null or s.campusId = :campusId)
            """)
    int bulkUpdateStatusInScope(
            @Param("tenantId") UUID tenantId,
            @Param("classId") UUID classId,
            @Param("sectionId") UUID sectionId,
            @Param("campusId") UUID campusId,
            @Param("fromStatus") StudentStatus fromStatus,
            @Param("toStatus") StudentStatus toStatus,
            @Param("reason") String reason,
            @Param("note") String note,
            @Param("actorId") UUID actorId,
            @Param("now") Instant now);

    @Query("""
            select s from Student s
            where s.tenantId = :tenantId
              and (:campusId is null or s.campusId = :campusId)
              and (:classId is null or s.classId = :classId)
              and (:sectionId is null or s.sectionId = :sectionId)
              and (:status is null or s.status = :status)
              and (:filterClasses = false or s.classId in :classIds)
            """)
    Page<Student> searchFiltered(
            @Param("tenantId") UUID tenantId,
            @Param("campusId") UUID campusId,
            @Param("classId") UUID classId,
            @Param("sectionId") UUID sectionId,
            @Param("status") StudentStatus status,
            @Param("filterClasses") boolean filterClasses,
            @Param("classIds") Collection<UUID> classIds,
            Pageable pageable);

    @Query("""
            select s from Student s
            where s.tenantId = :tenantId
              and (:campusId is null or s.campusId = :campusId)
              and (:classId is null or s.classId = :classId)
              and (:sectionId is null or s.sectionId = :sectionId)
              and (:status is null or s.status = :status)
              and (:filterClasses = false or s.classId in :classIds)
              and (
                    lower(coalesce(s.admissionNumber, '')) like :like
                 or lower(coalesce(s.rollNumber, '')) like :like
                 or lower(coalesce(s.cnic, '')) like :like
                 or exists (
                      select 1 from User u
                      where u.id = s.userId
                        and u.tenantId = :tenantId
                        and (
                              lower(u.firstName) like :like
                           or lower(u.lastName) like :like
                           or lower(concat(u.firstName, ' ', coalesce(u.lastName, ''))) like :like
                        )
                 )
              )
            """)
    Page<Student> search(
            @Param("tenantId") UUID tenantId,
            @Param("campusId") UUID campusId,
            @Param("classId") UUID classId,
            @Param("sectionId") UUID sectionId,
            @Param("status") StudentStatus status,
            @Param("filterClasses") boolean filterClasses,
            @Param("classIds") Collection<UUID> classIds,
            @Param("like") String like,
            Pageable pageable);
}
