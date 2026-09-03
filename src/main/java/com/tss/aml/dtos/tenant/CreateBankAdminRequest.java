package com.tss.aml.dtos.tenant;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateBankAdminRequest {

    @NotBlank(message = "userCode is required")
    private String userCode;

    private String employeeId;

    @NotBlank(message = "firstName is required")
    private String firstName;

    @NotBlank(message = "lastName is required")
    private String lastName;

    private String phoneNumber;

    @NotBlank(message = "email is required")
    @Email(message = "Invalid email format")
    private String email;
}
