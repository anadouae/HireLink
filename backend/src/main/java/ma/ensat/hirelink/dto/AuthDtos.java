package ma.ensat.hirelink.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import ma.ensat.hirelink.model.Role;

public class AuthDtos {

    public record RequestOtp(
            @NotBlank @Email @Size(max = 160) String email,
            @NotBlank @Size(max = 16) String purpose
    ) {}

    public record VerifyRegisterOtp(
            @NotBlank @Email @Size(max = 160) String email,
            @NotBlank @Size(max = 12) String code,
            Role role,
            @Size(max = 160) String fullName,
            @Size(max = 120) String company,
            @Size(max = 40) String companyIce,
            @Size(max = 40) String phone,
            @Size(max = 80) String city,
            @Size(max = 160) String school,
            @Size(max = 40) String educationLevel,
            @Size(max = 40) String workMode,
            @Size(max = 120) String contracts,
            @Size(max = 400) String metier,
            @Size(max = 120) String jobTitle
    ) {}

    public record CompleteProfile(
            Role role,
            @Size(max = 160) String fullName,
            @Size(max = 80) String firstName,
            @Size(max = 80) String lastName,
            @Size(max = 40) String phone,
            @Size(max = 80) String phoneCountry,
            @Size(max = 80) String city,
            @Size(max = 80) String nationality,
            @Size(max = 12) String birthDate,
            @Size(max = 160) String school,
            @Size(max = 40) String educationLevel,
            @Size(max = 40) String workMode,
            @Size(max = 120) String contracts,
            @Size(max = 400) String metier,
            @Size(max = 120) String jobTitle,
            @Size(max = 120) String company,
            @Size(max = 40) String companyIce,
            @Size(max = 200) String password
    ) {}

    public record VerifyLoginOtp(
            @NotBlank @Email @Size(max = 160) String email,
            @NotBlank @Size(max = 12) String code
    ) {}

    public record PasswordLogin(
            @NotBlank @Email @Size(max = 160) String email,
            @NotBlank @Size(max = 200) String password
    ) {}

    public record SetPassword(
            @NotBlank @Size(max = 200) String password
    ) {}

    public record GoogleLogin(
            @Size(max = 4096) String idToken,
            @Size(max = 160) String devEmail,
            @Size(max = 160) String devName,
            Role role,
            @Size(max = 40) String companyIce,
            @Size(max = 120) String company
    ) {}

    public record ForgotPassword(@NotBlank @Email @Size(max = 160) String email) {}

    public record ResetPassword(
            @NotBlank @Size(max = 128) String token,
            @NotBlank @Size(max = 200) String newPassword
    ) {}

    public record MfaVerify(@NotBlank @Size(max = 2048) String mfaToken, @NotBlank @Size(max = 32) String code) {}

    public record MfaEnrollStart(@NotBlank @Size(max = 2048) String enrollToken) {}

    public record MfaEnrollFinish(@NotBlank @Size(max = 2048) String enrollToken, @NotBlank @Size(max = 12) String code) {}

    /** Confirmation d'identité avant une action sensible : mot de passe (ou code e-mail) et, si activé, code TOTP. */
    public record Reauth(
            @Size(max = 200) String currentPassword,
            @Size(max = 32) String mfaCode,
            @Size(max = 12) String otp
    ) {}

    public record ChangePassword(
            @Size(max = 200) String currentPassword,
            @Size(max = 32) String mfaCode,
            @Size(max = 12) String otp,
            @NotBlank @Size(max = 200) String newPassword
    ) {}

    public record MfaCode(
            @Size(max = 200) String currentPassword,
            @NotBlank @Size(max = 32) String code,
            @Size(max = 12) String otp
    ) {}

    public record PasskeyRegisterStart(
            @Size(max = 200) String currentPassword,
            @Size(max = 32) String mfaCode,
            @Size(max = 12) String otp
    ) {}

    public record PasskeyFinish(@NotBlank @Size(max = 64) String requestId, JsonNode credential, @Size(max = 80) String name) {}

    public record Message(String message) {}
}
