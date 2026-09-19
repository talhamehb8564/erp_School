package com.erpschool.student.dto;

import com.erpschool.student.entity.Student;
import com.erpschool.student.entity.StudentStatus;
import com.erpschool.user.dto.CreateUserResponse;
import com.erpschool.user.dto.UserResponse;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.UUID;

public final class StudentDtos {

    private StudentDtos() {
    }

    @Getter
    @Setter
    public static class EnrollRequest {
        @NotBlank
        private String firstName;
        @NotBlank
        private String lastName;
        private String email;
        private String phone;
        private UUID campusId;
        @NotNull
        private UUID classId;
        @NotNull
        private UUID sectionId;
        private String rollNumber;
        private String photoUrl;
        private String gender;
        private LocalDate dateOfBirth;
        private LocalDate admissionDate;
        private String address;
        private String guardianName;
        private String guardianPhone;
        private UUID parentUserId;
        private String parentFirstName;
        private String parentLastName;
        private String parentEmail;
        private String parentPhone;
        private String relationship;
    }

    @Getter
    @Setter
    public static class UpdateRequest {
        private UUID campusId;
        private UUID classId;
        private UUID sectionId;
        private String rollNumber;
        private String photoUrl;
        private String gender;
        private LocalDate dateOfBirth;
        private String address;
        private String guardianName;
        private String guardianPhone;
        private StudentStatus status;
    }

    @Getter
    @Builder
    public static class Response {
        private UUID id;
        private UUID userId;
        private UUID campusId;
        private UUID classId;
        private UUID sectionId;
        private String admissionNumber;
        private String registrationNumber;
        private String rollNumber;
        private String photoUrl;
        private String gender;
        private LocalDate dateOfBirth;
        private LocalDate admissionDate;
        private StudentStatus status;
        private String address;
        private String guardianName;
        private String guardianPhone;
        private UserResponse user;
    }

    @Getter
    @Builder
    public static class EnrollResponse {
        private Response student;
        private CreateUserResponse studentAccount;
        private CreateUserResponse parentAccount;
        private String message;
    }

    public static Response from(Student s, UserResponse user) {
        return Response.builder()
                .id(s.getId())
                .userId(s.getUserId())
                .campusId(s.getCampusId())
                .classId(s.getClassId())
                .sectionId(s.getSectionId())
                .admissionNumber(s.getAdmissionNumber())
                .registrationNumber(s.getRegistrationNumber())
                .rollNumber(s.getRollNumber())
                .photoUrl(s.getPhotoUrl())
                .gender(s.getGender())
                .dateOfBirth(s.getDateOfBirth())
                .admissionDate(s.getAdmissionDate())
                .status(s.getStatus())
                .address(s.getAddress())
                .guardianName(s.getGuardianName())
                .guardianPhone(s.getGuardianPhone())
                .user(user)
                .build();
    }
}
