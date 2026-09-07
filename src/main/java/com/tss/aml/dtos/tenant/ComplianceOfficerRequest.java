package com.tss.aml.dtos.tenant;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@RequiredArgsConstructor
public class ComplianceOfficerRequest {

    @NotBlank(message = "userCode is required")
    private String userCode;

    @NotBlank(message = "employeeId is required")
    private String employeeId;

    @NotBlank(message = "firstName is required")
    private String firstName;

    @NotBlank(message = "lastName is required")
    private String lastName;

    @Pattern(regexp = "^$|^\\+?[0-9]{10,15}$", message = "Please enter a valid phone number.")
    private String phoneNumber;

    @NotBlank(message = "Email is required.")
    @Email(message = "Please enter a valid email address.")
    private String email;
}
