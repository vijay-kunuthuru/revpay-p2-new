package com.revpay.service;

import com.revpay.model.dto.BusinessSummaryDTO;
import com.revpay.model.dto.RevenueReportDTO;

import java.time.LocalDateTime;
import java.util.List;

public interface BusinessAnalyticsService {

    BusinessSummaryDTO getTransactionSummary(Long businessId);

    List<RevenueReportDTO> getDailyRevenue(Long businessId);

    // NEW: Get revenue for a specific date range
    List<RevenueReportDTO> getRevenueInDateRange(Long businessId, LocalDateTime start, LocalDateTime end);
}