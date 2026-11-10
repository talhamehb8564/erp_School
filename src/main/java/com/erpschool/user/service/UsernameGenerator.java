package com.erpschool.user.service;

import com.erpschool.user.entity.UserRole;
import com.erpschool.user.entity.UsernameSequence;
import com.erpschool.user.entity.UsernameSequenceId;
import com.erpschool.user.repository.UsernameSequenceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class UsernameGenerator {

    private final UsernameSequenceRepository sequenceRepository;

    public UsernameGenerator(UsernameSequenceRepository sequenceRepository) {
        this.sequenceRepository = sequenceRepository;
    }

    @Transactional
    public String next(UUID tenantId, String tenantCode, UserRole role) {
        if (role.isPlatformRole()) {
            throw new IllegalArgumentException("ERP owner username is not generated");
        }
        UsernameSequenceId id = new UsernameSequenceId(tenantId, role);
        UsernameSequence sequence = sequenceRepository.lockById(id).orElseGet(() -> {
            UsernameSequence created = new UsernameSequence();
            created.setId(id);
            created.setNextValue(1);
            return sequenceRepository.saveAndFlush(created);
        });
        int n = sequence.consume();
        sequenceRepository.save(sequence);
        return tenantCode.toUpperCase() + "-" + role.getUsernameCode() + "-" + String.format("%04d", n);
    }
}
