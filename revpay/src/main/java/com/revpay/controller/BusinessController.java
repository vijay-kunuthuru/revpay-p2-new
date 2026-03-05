package com.revpay.controller;

import com.revpay.model.dto.ApiResponse;
import com.revpay.model.entity.BusinessProfile;
import com.revpay.repository.BusinessProfileRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;

@Slf4j
@RestController
@RequestMapping("/api/v1/business")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('BUSINESS', 'ADMIN')")
@Tag(name = "Business Profile", description = "Endpoints for business profile management")
public class BusinessController {

        private final BusinessProfileRepository businessProfileRepository;

        public record BusinessProfileDTO(Long profileId, String businessName, String businessType, String taxId,
                        String address, boolean isVerified) {
        }

        @GetMapping("/profile/me")
        @Operation(summary = "Get my business profile", description = "Retrieves the business profile for the currently logged-in user.")
        public ResponseEntity<ApiResponse<BusinessProfileDTO>> getMyProfile(Principal principal) {
                String email = principal.getName();
                log.debug("Fetching business profile for user: {}", email);

                BusinessProfile profile = businessProfileRepository.findByUserEmail(email)
                                .orElseThrow(() -> new RuntimeException("Business profile not found for user"));

                BusinessProfileDTO dto = new BusinessProfileDTO(
                                profile.getProfileId(),
                                profile.getBusinessName(),
                                profile.getBusinessType(),
                                profile.getTaxId(),
                                profile.getAddress(),
                                profile.isVerified());

                return ResponseEntity.ok(ApiResponse.success(dto, "Business profile retrieved successfully"));
        }
}
