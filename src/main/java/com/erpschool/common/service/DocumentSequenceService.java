package com.erpschool.common.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class DocumentSequenceService {

    @PersistenceContext
    private EntityManager entityManager;

    @Transactional
    public int next(UUID tenantId, String docType) {
        Number n = (Number) entityManager.createNativeQuery("""
                        INSERT INTO document_sequences (tenant_id, doc_type, next_value, updated_at)
                        VALUES (:tenantId, :docType, 2, now())
                        ON CONFLICT (tenant_id, doc_type)
                        DO UPDATE SET next_value = document_sequences.next_value + 1, updated_at = now()
                        RETURNING next_value - 1
                        """)
                .setParameter("tenantId", tenantId)
                .setParameter("docType", docType)
                .getSingleResult();
        return n.intValue();
    }

    @Transactional
    public String nextFormatted(UUID tenantId, String docType, String prefix) {
        return prefix + String.format("%05d", next(tenantId, docType));
    }
}
