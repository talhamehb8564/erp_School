package com.erpschool.user.repository;

import com.erpschool.user.entity.UsernameSequence;
import com.erpschool.user.entity.UsernameSequenceId;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UsernameSequenceRepository extends JpaRepository<UsernameSequence, UsernameSequenceId> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM UsernameSequence s WHERE s.id = :id")
    Optional<UsernameSequence> lockById(@Param("id") UsernameSequenceId id);
}
