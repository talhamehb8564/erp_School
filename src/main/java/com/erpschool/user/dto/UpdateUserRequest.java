package com.erpschool.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateUserRequest {

    @Size(max = 100)
    private String firstName;

    @Size(max = 100)
    private String lastName;

    @Email
    @Size(max = 150)
    private String email;

    @Size(max = 30)
    private String phone;

    @Size(min = 3, max = 80)
    private String username;

    private java.util.UUID campusId;
}
