package com.ecommerce.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "New account registration. Always creates a CUSTOMER -- there is no way to self-assign ADMIN through this endpoint.")
public class RegisterRequest {

    @Schema(description = "Unique email address, used as the login username", example = "jane.doe@example.com")
    @NotBlank(message = "email is required")
    @Email(message = "email must be a well-formed email address")
    private String email;

    @Schema(description = "Plaintext password (hashed with BCrypt before storage, never persisted as-is)", example = "SecurePass123!")
    @NotBlank(message = "password is required")
    @Size(min = 8, message = "password must be at least 8 characters")
    private String password;

    @Schema(description = "Display name", example = "Jane Doe")
    @NotBlank(message = "fullName is required")
    private String fullName;

    public RegisterRequest() {
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }
}
