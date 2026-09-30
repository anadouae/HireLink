package ma.ensat.hirelink.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import ma.ensat.hirelink.model.Role;

public class AuthDtos {

    public record RequestOtp(
            @NotBlank @Email String email,
            @NotBlank String purpose
    ) {}

    public record VerifyRegisterOtp(
            @NotBlank @Email String email,
            @NotBlank String code,
            Role role,
            String fullName,
            String company,
            String companyIce
    ) {}

    public record VerifyLoginOtp(
            @NotBlank @Email String email,
            @NotBlank String code
    ) {}

    public record PasswordLogin(
            @NotBlank @Email String email,
            @NotBlank String password
    ) {}

    public record SetPassword(
            @NotBlank String password
    ) {}

    public record GoogleLogin(
            String idToken,
            String devEmail,
            String devName,
            Role role,
            String companyIce,
            String company
    ) {}

    public record Message(String message) {}
}
