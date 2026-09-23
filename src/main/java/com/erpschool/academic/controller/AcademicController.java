package com.erpschool.academic.controller;

import com.erpschool.academic.entity.ClassSubject;
import com.erpschool.academic.entity.SchoolClass;
import com.erpschool.academic.entity.Section;
import com.erpschool.academic.entity.Subject;
import com.erpschool.academic.entity.TeacherAssignment;
import com.erpschool.academic.entity.TimetableSlot;
import com.erpschool.academic.service.AcademicService;
import com.erpschool.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Academic")
public class AcademicController {

    private final AcademicService academicService;

    public AcademicController(AcademicService academicService) {
        this.academicService = academicService;
    }

    @PostMapping("/classes")
    @PreAuthorize("hasAnyRole('ERP_OWNER','SCHOOL_ADMIN')")
    public ResponseEntity<ApiResponse<SchoolClass>> createClass(@RequestBody SchoolClass body) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Class created", academicService.createClass(body)));
    }

    @GetMapping("/classes")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<SchoolClass>>> classes() {
        return ResponseEntity.ok(ApiResponse.ok(academicService.classes()));
    }

    @PostMapping("/classes/{classId}/sections")
    @PreAuthorize("hasAnyRole('ERP_OWNER','SCHOOL_ADMIN')")
    public ResponseEntity<ApiResponse<Section>> createSection(@PathVariable UUID classId,
                                                              @Valid @RequestBody NameRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Section created", academicService.createSection(classId, request.getName())));
    }

    @GetMapping("/classes/{classId}/sections")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<Section>>> sections(@PathVariable UUID classId) {
        return ResponseEntity.ok(ApiResponse.ok(academicService.sections(classId)));
    }

    @GetMapping("/sections")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<Section>>> allSections() {
        return ResponseEntity.ok(ApiResponse.ok(academicService.allSections()));
    }

    @PostMapping("/subjects")
    @PreAuthorize("hasAnyRole('ERP_OWNER','SCHOOL_ADMIN')")
    public ResponseEntity<ApiResponse<Subject>> createSubject(@RequestBody Subject body) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Subject created", academicService.createSubject(body.getName(), body.getCode())));
    }

    @GetMapping("/subjects")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<Subject>>> subjects() {
        return ResponseEntity.ok(ApiResponse.ok(academicService.subjects()));
    }

    @PostMapping("/classes/{classId}/subjects/{subjectId}")
    @PreAuthorize("hasAnyRole('ERP_OWNER','SCHOOL_ADMIN')")
    public ResponseEntity<ApiResponse<ClassSubject>> classSubject(@PathVariable UUID classId,
                                                                  @PathVariable UUID subjectId) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(academicService.assignSubjectToClass(classId, subjectId)));
    }

    @PostMapping("/teacher-assignments")
    @PreAuthorize("hasAnyRole('ERP_OWNER','SCHOOL_ADMIN')")
    public ResponseEntity<ApiResponse<TeacherAssignment>> assign(@Valid @RequestBody AssignmentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Teacher assigned", academicService.assignTeacher(
                        request.getTeacherUserId(), request.getClassId(), request.getSectionId(), request.getSubjectId())));
    }

    @GetMapping("/teacher-assignments")
    @PreAuthorize("hasAnyRole('ERP_OWNER','SCHOOL_ADMIN','PRINCIPAL','TEACHER')")
    public ResponseEntity<ApiResponse<List<TeacherAssignment>>> assignments(
            @RequestParam(required = false) UUID teacherUserId) {
        return ResponseEntity.ok(ApiResponse.ok(academicService.teacherAssignments(teacherUserId)));
    }

    @GetMapping("/timetable/settings")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Map<String, Object>>> timetableSettings() {
        return ResponseEntity.ok(ApiResponse.ok(academicService.settingsView()));
    }

    @PutMapping("/timetable/settings")
    @PreAuthorize("hasAnyRole('ERP_OWNER','SCHOOL_ADMIN')")
    public ResponseEntity<ApiResponse<com.erpschool.academic.entity.TimetableSettings>> saveTimetableSettings(
            @RequestBody com.erpschool.academic.entity.TimetableSettings body) {
        return ResponseEntity.ok(ApiResponse.ok("Timetable settings saved", academicService.saveSettings(body)));
    }

    @PostMapping("/timetable")
    @PreAuthorize("hasAnyRole('ERP_OWNER','SCHOOL_ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> slot(@Valid @RequestBody TimetableRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Timetable lecture created", academicService.createSlot(toSlot(request))));
    }

    @PutMapping("/timetable/{id}")
    @PreAuthorize("hasAnyRole('ERP_OWNER','SCHOOL_ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> updateSlot(
            @PathVariable UUID id, @Valid @RequestBody TimetableRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Timetable lecture updated", academicService.updateSlot(id, toSlot(request))));
    }

    @DeleteMapping("/timetable/{id}")
    @PreAuthorize("hasAnyRole('ERP_OWNER','SCHOOL_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteSlot(@PathVariable UUID id) {
        academicService.deleteSlot(id);
        return ResponseEntity.ok(ApiResponse.ok("Timetable lecture deleted", null));
    }

    @PostMapping("/timetable/apply")
    @PreAuthorize("hasAnyRole('ERP_OWNER','SCHOOL_ADMIN')")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> apply(@Valid @RequestBody ApplyRequest request) {
        List<TimetableSlot> lectures = request.getLectures() == null ? List.of()
                : request.getLectures().stream().map(this::toLectureSlot).toList();
        return ResponseEntity.ok(ApiResponse.ok("Timetable applied", academicService.applyPattern(
                request.getClassId(), request.getSectionId(), request.getDays(), lectures,
                Boolean.TRUE.equals(request.getReplace()))));
    }

    @PostMapping("/timetable/copy")
    @PreAuthorize("hasAnyRole('ERP_OWNER','SCHOOL_ADMIN')")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> copy(@Valid @RequestBody CopyRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Timetable copied", academicService.copyTimetable(
                request.getFromClassId(), request.getFromSectionId(),
                request.getToClassId(), request.getToSectionId(), request.getDays())));
    }

    @GetMapping("/timetable")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> classTimetable(
            @RequestParam UUID classId, @RequestParam UUID sectionId) {
        return ResponseEntity.ok(ApiResponse.ok(academicService.classTimetable(classId, sectionId)));
    }

    @GetMapping("/teacher/timetable")
    @PreAuthorize("hasRole('TEACHER')")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> teacherTimetable() {
        return ResponseEntity.ok(ApiResponse.ok(academicService.teacherTimetable()));
    }

    @GetMapping("/teacher/timetable/today")
    @PreAuthorize("hasRole('TEACHER')")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> teacherToday() {
        int day = LocalDate.now().getDayOfWeek() == DayOfWeek.SUNDAY ? 7 : LocalDate.now().getDayOfWeek().getValue();
        return ResponseEntity.ok(ApiResponse.ok(academicService.teacherToday(day)));
    }

    private TimetableSlot toSlot(TimetableRequest request) {
        TimetableSlot slot = new TimetableSlot();
        slot.setClassId(request.getClassId());
        slot.setSectionId(request.getSectionId());
        slot.setSubjectId(request.getSubjectId());
        slot.setTeacherUserId(request.getTeacherUserId());
        slot.setDayOfWeek(request.getDayOfWeek());
        slot.setStartTime(request.getStartTime());
        slot.setEndTime(request.getEndTime());
        return slot;
    }

    private TimetableSlot toLectureSlot(LectureLine line) {
        TimetableSlot slot = new TimetableSlot();
        slot.setClassId(line.getClassId());
        slot.setSectionId(line.getSectionId());
        slot.setSubjectId(line.getSubjectId());
        slot.setTeacherUserId(line.getTeacherUserId());
        slot.setDayOfWeek(line.getDayOfWeek() == null ? 1 : line.getDayOfWeek());
        slot.setStartTime(line.getStartTime());
        slot.setEndTime(line.getEndTime());
        return slot;
    }

    @Getter
    @Setter
    public static class NameRequest {
        @NotBlank
        private String name;
    }

    @Getter
    @Setter
    public static class AssignmentRequest {
        @NotNull
        private UUID teacherUserId;
        @NotNull
        private UUID classId;
        @NotNull
        private UUID sectionId;
        @NotNull
        private UUID subjectId;
    }

    @Getter
    @Setter
    public static class TimetableRequest {
        @NotNull
        private UUID classId;
        @NotNull
        private UUID sectionId;
        @NotNull
        private UUID subjectId;
        @NotNull
        private UUID teacherUserId;
        @NotNull
        private Integer dayOfWeek;
        @NotNull
        private LocalTime startTime;
        @NotNull
        private LocalTime endTime;
    }

    @Getter
    @Setter
    public static class ApplyRequest {
        @NotNull
        private UUID classId;
        @NotNull
        private UUID sectionId;
        private List<Integer> days;
        private Boolean replace;
        private List<LectureLine> lectures;
    }

    @Getter
    @Setter
    public static class LectureLine {
        @NotNull
        private UUID subjectId;
        @NotNull
        private UUID teacherUserId;
        @NotNull
        private LocalTime startTime;
        @NotNull
        private LocalTime endTime;
        private Integer dayOfWeek;
        private UUID classId;
        private UUID sectionId;
    }

    @Getter
    @Setter
    public static class CopyRequest {
        @NotNull
        private UUID fromClassId;
        @NotNull
        private UUID fromSectionId;
        @NotNull
        private UUID toClassId;
        @NotNull
        private UUID toSectionId;
        private List<Integer> days;
    }
}
