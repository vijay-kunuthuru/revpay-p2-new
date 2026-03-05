package com.revpay.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdatePinRequest {

    @NotBlank(message = "Current PIN is required")
    @ToString.Exclude
    private String oldPin;

    @NotBlank(message = "New PIN is required")
    @Pattern(regexp = "^[0-9]{4,6}$", message = "New PIN must be between 4 and 6 digits")
    @ToString.Exclude
    private String newPin;
}
