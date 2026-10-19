package com.erpschool.website;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface WebsiteInquiryRepository extends JpaRepository<WebsiteInquiry, UUID> {
}
