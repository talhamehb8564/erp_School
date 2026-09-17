package com.erpschool.subscription.service;

import com.erpschool.audit.service.AuditService;
import com.erpschool.common.dto.PageResponse;
import com.erpschool.common.exception.BusinessException;
import com.erpschool.common.exception.ResourceNotFoundException;
import com.erpschool.common.util.TenantGuard;
import com.erpschool.subscription.dto.SubscriptionDtos;
import com.erpschool.subscription.entity.Subscription;
import com.erpschool.subscription.entity.SubscriptionPayment;
import com.erpschool.subscription.entity.SubscriptionStatus;
import com.erpschool.subscription.repository.SubscriptionPaymentRepository;
import com.erpschool.subscription.repository.SubscriptionRepository;
import com.erpschool.tenant.context.TenantContext;
import com.erpschool.tenant.entity.Tenant;
import com.erpschool.tenant.entity.TenantStatus;
import com.erpschool.tenant.repository.TenantRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class SubscriptionService {

    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionPaymentRepository paymentRepository;
    private final TenantRepository tenantRepository;
    private final AuditService auditService;

    public SubscriptionService(SubscriptionRepository subscriptionRepository,
                               SubscriptionPaymentRepository paymentRepository,
                               TenantRepository tenantRepository,
                               AuditService auditService) {
        this.subscriptionRepository = subscriptionRepository;
        this.paymentRepository = paymentRepository;
        this.tenantRepository = tenantRepository;
        this.auditService = auditService;
    }

    @Transactional
    public SubscriptionDtos.Response create(SubscriptionDtos.CreateRequest request) {
        UUID tenantId = request.getTenantId();
        tenantRepository.findById(tenantId).orElseThrow(() -> new ResourceNotFoundException("Tenant", tenantId));
        Subscription s = new Subscription();
        s.setTenantId(tenantId);
        s.setStatus(SubscriptionStatus.PENDING);
        s.setAmount(request.getAmount());
        s.setCurrency(request.getCurrency() == null ? "PKR" : request.getCurrency());
        s.setNotes(request.getNotes());
        s.setCreatedBy(TenantContext.getUserId());
        s = subscriptionRepository.save(s);
        auditService.record("SUBSCRIPTION_CREATED", "Subscription", s.getId().toString(),
                Map.of("tenantId", tenantId.toString()));
        return toResponse(s);
    }

    @Transactional(readOnly = true)
    public PageResponse<SubscriptionDtos.Response> listAll(Pageable pageable) {
        return PageResponse.from(subscriptionRepository.findAllByOrderByCreatedAtDesc(pageable).map(this::toResponse));
    }

    @Transactional(readOnly = true)
    public SubscriptionDtos.Response current(UUID requestedTenantId) {
        UUID tenantId = TenantGuard.requireTenantId(requestedTenantId);
        Subscription s = subscriptionRepository.findTopByTenantIdOrderByCreatedAtDesc(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("No subscription for this school"));
        return toResponse(s);
    }

    @Transactional
    public SubscriptionDtos.Response submitPayment(UUID subscriptionId, SubscriptionDtos.SubmitPaymentRequest request) {
        Subscription s = load(subscriptionId);
        TenantGuard.assertSameTenant(s.getTenantId());
        if (s.getStatus() != SubscriptionStatus.PENDING && s.getStatus() != SubscriptionStatus.REJECTED) {
            throw new BusinessException("Cannot submit payment for status " + s.getStatus());
        }
        SubscriptionPayment p = new SubscriptionPayment();
        p.setTenantId(s.getTenantId());
        p.setSubscriptionId(s.getId());
        p.setSlipUrl(request.getSlipUrl());
        p.setTransactionRef(request.getTransactionRef());
        p.setStatus(SubscriptionStatus.PAYMENT_SUBMITTED);
        p.setCreatedBy(TenantContext.getUserId());
        paymentRepository.save(p);
        s.setStatus(SubscriptionStatus.PAYMENT_SUBMITTED);
        s.setUpdatedBy(TenantContext.getUserId());
        s = subscriptionRepository.save(s);
        auditService.record("SUBSCRIPTION_PAYMENT_SUBMITTED", "Subscription", s.getId().toString(), null);
        return toResponse(s);
    }

    @Transactional
    public SubscriptionDtos.Response review(UUID subscriptionId, SubscriptionDtos.ReviewRequest request) {
        Subscription s = load(subscriptionId);
        List<SubscriptionPayment> payments = paymentRepository.findBySubscriptionIdOrderBySubmittedAtDesc(s.getId());
        if (payments.isEmpty()) {
            throw new BusinessException("No payment slip has been submitted");
        }
        SubscriptionPayment latest = payments.get(0);
        latest.setReviewedBy(TenantContext.getUserId());
        latest.setReviewedAt(Instant.now());
        if (Boolean.TRUE.equals(request.getApprove())) {
            if (request.getPeriodStart() == null || request.getPeriodEnd() == null) {
                throw new BusinessException("PERIOD_REQUIRED", "Subscription start and expiry dates are required");
            }
            latest.setStatus(SubscriptionStatus.PAID);
            s.setStatus(SubscriptionStatus.PAID);
            s.setPeriodStart(request.getPeriodStart());
            s.setPeriodEnd(request.getPeriodEnd());
            Tenant tenant = tenantRepository.findById(s.getTenantId())
                    .orElseThrow(() -> new ResourceNotFoundException("Tenant", s.getTenantId()));
            tenant.setStatus(TenantStatus.ACTIVE);
            tenantRepository.save(tenant);
            auditService.record("SCHOOL_SUBSCRIPTION_APPROVED", "Subscription", s.getId().toString(),
                    Map.of("tenantId", s.getTenantId().toString()));
        } else {
            latest.setStatus(SubscriptionStatus.REJECTED);
            latest.setRejectionReason(request.getRejectionReason());
            s.setStatus(SubscriptionStatus.REJECTED);
            auditService.record("SUBSCRIPTION_REJECTED", "Subscription", s.getId().toString(),
                    Map.of("reason", request.getRejectionReason() == null ? "" : request.getRejectionReason()));
        }
        paymentRepository.save(latest);
        s.setUpdatedBy(TenantContext.getUserId());
        return toResponse(subscriptionRepository.save(s));
    }

    private Subscription load(UUID id) {
        return subscriptionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Subscription", id));
    }

    private SubscriptionDtos.Response toResponse(Subscription s) {
        return SubscriptionDtos.from(s, paymentRepository.findBySubscriptionIdOrderBySubmittedAtDesc(s.getId()));
    }
}
