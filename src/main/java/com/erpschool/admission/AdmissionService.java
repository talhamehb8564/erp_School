package com.erpschool.admission;

import com.erpschool.campus.entity.Campus;
import com.erpschool.campus.repository.CampusRepository;
import com.erpschool.common.cache.CatalogCache;
import com.erpschool.common.dto.PageResponse;
import com.erpschool.common.exception.BusinessException;
import com.erpschool.common.exception.ResourceNotFoundException;
import com.erpschool.common.util.TenantGuard;
import com.erpschool.feature.FeatureService;
import com.erpschool.feature.SchoolFeature;
import com.erpschool.tenant.context.TenantContext;
import com.erpschool.tenant.entity.Tenant;
import com.erpschool.tenant.entity.TenantStatus;
import com.erpschool.tenant.repository.TenantRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class AdmissionService {

    static final ZoneId ZONE = ZoneId.of("Asia/Karachi");
    private static final Set<Integer> SLOT_MINUTES = Set.of(15, 20, 30, 45, 60);
    private static final String[] CORE_KEYS = {"studentName", "guardianName", "cnic", "phone"};

    private final AdmissionSettingsRepository settingsRepository;
    private final AdmissionFieldRepository fieldRepository;
    private final SchoolOpenPeriodRepository periodRepository;
    private final AdmissionApplicationRepository applicationRepository;
    private final AdmissionAppointmentRepository appointmentRepository;
    private final TenantRepository tenantRepository;
    private final CampusRepository campusRepository;
    private final FeatureService featureService;
    private final CatalogCache catalogCache;
    private final ObjectMapper objectMapper;

    public AdmissionService(AdmissionSettingsRepository settingsRepository,
                            AdmissionFieldRepository fieldRepository,
                            SchoolOpenPeriodRepository periodRepository,
                            AdmissionApplicationRepository applicationRepository,
                            AdmissionAppointmentRepository appointmentRepository,
                            TenantRepository tenantRepository,
                            CampusRepository campusRepository,
                            FeatureService featureService,
                            CatalogCache catalogCache,
                            ObjectMapper objectMapper) {
        this.settingsRepository = settingsRepository;
        this.fieldRepository = fieldRepository;
        this.periodRepository = periodRepository;
        this.applicationRepository = applicationRepository;
        this.appointmentRepository = appointmentRepository;
        this.tenantRepository = tenantRepository;
        this.campusRepository = campusRepository;
        this.featureService = featureService;
        this.catalogCache = catalogCache;
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> publicSummary(UUID tenantId) {
        Map<String, Object> out = new LinkedHashMap<>();
        boolean featureOn = featureService.enabled(tenantId, SchoolFeature.ADMISSIONS);
        AdmissionSettings settings = settingsRepository.findById(tenantId).orElse(null);
        boolean on = featureOn && (settings == null || settings.isEnabled());
        out.put("enabled", on);
        out.put("ctaLabel", settings != null && settings.getCtaLabel() != null
                ? settings.getCtaLabel() : "Apply for Admission");
        out.put("appointmentEnabled", settings != null && settings.isAppointmentEnabled());
        out.put("appointmentRequired", settings != null && settings.isAppointmentRequired());
        return out;
    }

    @Transactional
    public Map<String, Object> publicForm(String code) {
        Tenant tenant = resolveSchool(code);
        Map<String, Object> summary = publicSummary(tenant.getId());
        Map<String, Object> out = new LinkedHashMap<>(summary);
        out.put("schoolName", tenant.getName());
        out.put("schoolCode", tenant.getCode());
        out.put("campuses", campusRepository.findByTenantIdOrderByNameAsc(tenant.getId()).stream()
                .map(c -> Map.of("id", c.getId(), "name", c.getName(), "code", c.getCode()))
                .toList());
        if (!Boolean.TRUE.equals(summary.get("enabled"))) {
            out.put("fields", List.of());
            return out;
        }
        ensureCoreFields(tenant.getId());
        out.put("fields", fieldRepository.findByTenantIdOrderBySortOrderAsc(tenant.getId()).stream()
                .filter(AdmissionField::isEnabled)
                .map(this::toField)
                .toList());
        return out;
    }

    @Transactional
    public Map<String, Object> adminConfig() {
        UUID tenantId = TenantGuard.requireTenantId(null);
        ensureCoreFields(tenantId);
        AdmissionSettings settings = settings(tenantId);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("settings", toSettings(settings));
        out.put("featureEnabled", featureService.enabled(tenantId, SchoolFeature.ADMISSIONS));
        out.put("fields", fieldRepository.findByTenantIdOrderBySortOrderAsc(tenantId).stream()
                .map(this::toField).toList());
        out.put("openPeriods", periodRepository.findByTenantIdOrderByDayOfWeekAscStartTimeAsc(tenantId).stream()
                .map(this::toPeriod).toList());
        return out;
    }

    @Transactional
    public Map<String, Object> saveSettings(Map<String, Object> body) {
        UUID tenantId = TenantGuard.requireTenantId(null);
        AdmissionSettings settings = settings(tenantId);
        if (body.containsKey("enabled")) {
            settings.setEnabled(bool(body.get("enabled")));
        }
        if (body.containsKey("appointmentEnabled")) {
            settings.setAppointmentEnabled(bool(body.get("appointmentEnabled")));
        }
        if (body.containsKey("appointmentRequired")) {
            settings.setAppointmentRequired(bool(body.get("appointmentRequired")));
        }
        if (body.get("ctaLabel") instanceof String label && !label.isBlank()) {
            settings.setCtaLabel(label.trim().substring(0, Math.min(80, label.trim().length())));
        }
        if (settings.isAppointmentRequired()) {
            settings.setAppointmentEnabled(true);
        }
        settings.setUpdatedAt(Instant.now());
        settings.setUpdatedBy(TenantContext.getUserId());
        settingsRepository.save(settings);
        catalogCache.evictTenant(tenantId);
        return toSettings(settings);
    }

    @Transactional
    public Map<String, Object> addField(Map<String, Object> body) {
        UUID tenantId = TenantGuard.requireTenantId(null);
        ensureCoreFields(tenantId);
        String key = slug(str(body.get("fieldKey")));
        if (key.isBlank()) {
            throw new BusinessException("Field key is required");
        }
        if (isCore(key) || fieldRepository.existsByTenantIdAndFieldKey(tenantId, key)) {
            throw new BusinessException("FIELD_EXISTS", "That field already exists");
        }
        AdmissionField field = new AdmissionField();
        field.setTenantId(tenantId);
        field.setFieldKey(key);
        field.setLabel(required(str(body.get("label")), "Label"));
        field.setFieldType(parseType(str(body.get("fieldType"))));
        field.setRequired(bool(body.get("required")));
        field.setLocked(false);
        field.setEnabled(body.get("enabled") == null || bool(body.get("enabled")));
        field.setOptions(str(body.get("options")));
        field.setSortOrder(body.get("sortOrder") instanceof Number n ? n.intValue() : 100);
        field.setCreatedBy(TenantContext.getUserId());
        fieldRepository.save(field);
        return toField(field);
    }

    @Transactional
    public Map<String, Object> updateField(UUID id, Map<String, Object> body) {
        UUID tenantId = TenantGuard.requireTenantId(null);
        AdmissionField field = fieldRepository.findById(id)
                .filter(f -> tenantId.equals(f.getTenantId()))
                .orElseThrow(() -> new ResourceNotFoundException("Admission field", id));
        if (body.get("label") instanceof String label && !label.isBlank()) {
            field.setLabel(label.trim());
        }
        if (!field.isLocked()) {
            if (body.get("fieldType") instanceof String type) {
                field.setFieldType(parseType(type));
            }
            if (body.containsKey("required")) {
                field.setRequired(bool(body.get("required")));
            }
            if (body.containsKey("options")) {
                field.setOptions(str(body.get("options")));
            }
        } else if (body.containsKey("required") && !bool(body.get("required"))) {
            throw new BusinessException("Core admission fields stay compulsory");
        }
        if (body.containsKey("enabled")) {
            if (field.isLocked() && !bool(body.get("enabled"))) {
                throw new BusinessException("Core admission fields cannot be turned off");
            }
            field.setEnabled(bool(body.get("enabled")));
        }
        if (body.get("sortOrder") instanceof Number n) {
            field.setSortOrder(n.intValue());
        }
        field.setUpdatedBy(TenantContext.getUserId());
        fieldRepository.save(field);
        return toField(field);
    }

    @Transactional
    public void deleteField(UUID id) {
        UUID tenantId = TenantGuard.requireTenantId(null);
        AdmissionField field = fieldRepository.findById(id)
                .filter(f -> tenantId.equals(f.getTenantId()))
                .orElseThrow(() -> new ResourceNotFoundException("Admission field", id));
        if (field.isLocked()) {
            throw new BusinessException("Core admission fields cannot be removed");
        }
        fieldRepository.delete(field);
    }

    @Transactional
    public Map<String, Object> addPeriod(Map<String, Object> body) {
        UUID tenantId = TenantGuard.requireTenantId(null);
        SchoolOpenPeriod period = new SchoolOpenPeriod();
        period.setTenantId(tenantId);
        applyPeriod(period, body);
        period.setCreatedBy(TenantContext.getUserId());
        periodRepository.save(period);
        return toPeriod(period);
    }

    @Transactional
    public Map<String, Object> updatePeriod(UUID id, Map<String, Object> body) {
        UUID tenantId = TenantGuard.requireTenantId(null);
        SchoolOpenPeriod period = periodRepository.findById(id)
                .filter(p -> tenantId.equals(p.getTenantId()))
                .orElseThrow(() -> new ResourceNotFoundException("Open period", id));
        applyPeriod(period, body);
        period.setUpdatedBy(TenantContext.getUserId());
        periodRepository.save(period);
        return toPeriod(period);
    }

    @Transactional
    public void deletePeriod(UUID id) {
        UUID tenantId = TenantGuard.requireTenantId(null);
        SchoolOpenPeriod period = periodRepository.findById(id)
                .filter(p -> tenantId.equals(p.getTenantId()))
                .orElseThrow(() -> new ResourceNotFoundException("Open period", id));
        periodRepository.delete(period);
    }

    @Transactional(readOnly = true)
    public PageResponse<Map<String, Object>> applications(Pageable pageable) {
        UUID tenantId = TenantGuard.requireTenantId(null);
        return PageResponse.from(applicationRepository.findByTenantIdOrderByCreatedAtDesc(tenantId, pageable)
                .map(this::toApplication));
    }

    @Transactional
    public Map<String, Object> updateApplicationStatus(UUID id, String statusRaw) {
        UUID tenantId = TenantGuard.requireTenantId(null);
        AdmissionApplication app = applicationRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Application", id));
        try {
            app.setStatus(AdmissionApplicationStatus.valueOf(statusRaw.trim().toUpperCase(Locale.ROOT)));
        } catch (Exception ex) {
            throw new BusinessException("Unknown application status");
        }
        app.setUpdatedBy(TenantContext.getUserId());
        applicationRepository.save(app);
        return toApplication(app);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> publicSlots(String code, LocalDate date, UUID campusId) {
        Tenant tenant = resolveSchool(code);
        Map<String, Object> summary = publicSummary(tenant.getId());
        if (!Boolean.TRUE.equals(summary.get("enabled")) || !Boolean.TRUE.equals(summary.get("appointmentEnabled"))) {
            return List.of();
        }
        return slots(tenant.getId(), date, campusId, true);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> adminSlots(LocalDate date, UUID campusId) {
        return slots(TenantGuard.requireTenantId(null), date, campusId, false);
    }

    @Transactional
    public Map<String, Object> submitPublic(String code, Map<String, Object> body) {
        Tenant tenant = resolveSchool(code);
        Map<String, Object> summary = publicSummary(tenant.getId());
        if (!Boolean.TRUE.equals(summary.get("enabled"))) {
            throw new BusinessException("ADMISSIONS_CLOSED", "Admissions are not open for this school");
        }
        ensureCoreFields(tenant.getId());
        UUID campusId = parseUuid(body.get("campusId"));
        if (campusId != null) {
            requireCampus(tenant.getId(), campusId);
        }
        List<AdmissionField> fields = fieldRepository.findByTenantIdOrderBySortOrderAsc(tenant.getId()).stream()
                .filter(AdmissionField::isEnabled)
                .toList();
        Map<String, Object> answers = new LinkedHashMap<>();
        Object answersRaw = body.get("answers");
        if (answersRaw instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> e : map.entrySet()) {
                answers.put(String.valueOf(e.getKey()), e.getValue());
            }
        }
        for (AdmissionField field : fields) {
            Object value = answers.getOrDefault(field.getFieldKey(), body.get(field.getFieldKey()));
            String text = value == null ? "" : String.valueOf(value).trim();
            if (field.isRequired() && text.isBlank()) {
                throw new BusinessException("MISSING_FIELD", field.getLabel() + " is required");
            }
            answers.put(field.getFieldKey(), text);
        }
        String studentName = required(str(answers.get("studentName")), "Student name");
        String guardianName = required(str(answers.get("guardianName")), "Guardian name");
        String cnic = normalizeCnic(str(answers.get("cnic")));
        String phone = normalizePhone(str(answers.get("phone")));
        answers.put("cnic", cnic);
        answers.put("phone", phone);

        Instant slotStart = parseInstant(body.get("slotStart"));
        boolean apptEnabled = Boolean.TRUE.equals(summary.get("appointmentEnabled"));
        boolean apptRequired = Boolean.TRUE.equals(summary.get("appointmentRequired"));
        if (apptRequired && slotStart == null) {
            throw new BusinessException("APPOINTMENT_REQUIRED", "Please pick an appointment slot");
        }
        if (slotStart != null && !apptEnabled) {
            throw new BusinessException("Appointments are not offered for this school");
        }

        AdmissionApplication app = new AdmissionApplication();
        app.setTenantId(tenant.getId());
        app.setCampusId(campusId);
        app.setStudentName(studentName);
        app.setGuardianName(guardianName);
        app.setCnic(cnic);
        app.setPhone(phone);
        app.setPayload(writeJson(answers));
        app.setStatus(AdmissionApplicationStatus.SUBMITTED);
        applicationRepository.save(app);

        Map<String, Object> out = toApplication(app);
        if (slotStart != null) {
            out.put("appointment", book(tenant.getId(), app, campusId, slotStart));
        }
        return out;
    }

    @Transactional
    public Map<String, Object> bookForApplication(UUID applicationId, Instant slotStart, UUID campusId) {
        UUID tenantId = TenantGuard.requireTenantId(null);
        AdmissionApplication app = applicationRepository.findByIdAndTenantId(applicationId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Application", applicationId));
        UUID campus = campusId != null ? campusId : app.getCampusId();
        return book(tenantId, app, campus, slotStart);
    }

    List<Map<String, Object>> slots(UUID tenantId, LocalDate date, UUID campusId, boolean hideTaken) {
        if (date == null) {
            throw new BusinessException("Date is required");
        }
        int dow = date.getDayOfWeek() == DayOfWeek.SUNDAY ? 7 : date.getDayOfWeek().getValue();
        List<SchoolOpenPeriod> periods = periodRepository.findByTenantIdAndDayOfWeekAndEnabledTrue(tenantId, dow);
        Instant dayStart = date.atStartOfDay(ZONE).toInstant();
        Instant dayEnd = date.plusDays(1).atStartOfDay(ZONE).toInstant();
        Set<Instant> taken = new HashSet<>();
        for (AdmissionAppointment booked : appointmentRepository.findBookedInRange(
                tenantId, campusId, dayStart, dayEnd, AdmissionAppointmentStatus.BOOKED)) {
            taken.add(booked.getSlotStart());
        }
        Instant now = Instant.now();
        List<Map<String, Object>> out = new ArrayList<>();
        for (SchoolOpenPeriod period : periods) {
            if (campusId != null && period.getCampusId() != null && !campusId.equals(period.getCampusId())) {
                continue;
            }
            LocalTime t = period.getStartTime();
            while (!t.plusMinutes(period.getSlotMinutes()).isAfter(period.getEndTime())) {
                Instant start = date.atTime(t).atZone(ZONE).toInstant();
                Instant end = date.atTime(t.plusMinutes(period.getSlotMinutes())).atZone(ZONE).toInstant();
                boolean booked = taken.contains(start);
                if (start.isAfter(now) && (!hideTaken || !booked)) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("start", start.toString());
                    row.put("end", end.toString());
                    row.put("booked", booked);
                    out.add(row);
                }
                t = t.plusMinutes(period.getSlotMinutes());
            }
        }
        return out;
    }

    Map<String, Object> book(UUID tenantId, AdmissionApplication app, UUID campusId, Instant slotStart) {
        if (slotStart == null) {
            throw new BusinessException("Appointment time is required");
        }
        if (appointmentRepository.findByTenantIdAndApplicationIdAndStatus(
                tenantId, app.getId(), AdmissionAppointmentStatus.BOOKED).isPresent()) {
            throw new BusinessException("SLOT_TAKEN", "This application already has an appointment");
        }
        LocalDate date = slotStart.atZone(ZONE).toLocalDate();
        boolean allowed = slots(tenantId, date, campusId, false).stream()
                .anyMatch(s -> slotStart.toString().equals(s.get("start")) && Boolean.FALSE.equals(s.get("booked")));
        if (!allowed) {
            throw new BusinessException("SLOT_TAKEN", "That appointment time is not available");
        }
        Instant slotEnd = slotStart.plusSeconds(30 * 60);
        for (Map<String, Object> s : slots(tenantId, date, campusId, false)) {
            if (slotStart.toString().equals(s.get("start")) && s.get("end") instanceof String end) {
                slotEnd = Instant.parse(end);
                break;
            }
        }
        AdmissionAppointment appt = new AdmissionAppointment();
        appt.setTenantId(tenantId);
        appt.setApplicationId(app.getId());
        appt.setCampusId(campusId);
        appt.setSlotStart(slotStart);
        appt.setSlotEnd(slotEnd);
        appt.setStatus(AdmissionAppointmentStatus.BOOKED);
        try {
            appointmentRepository.saveAndFlush(appt);
        } catch (DataIntegrityViolationException ex) {
            throw new BusinessException("SLOT_TAKEN", "That appointment time is already booked");
        }
        return toAppointment(appt);
    }

    void ensureCoreFields(UUID tenantId) {
        List<AdmissionField> existing = fieldRepository.findByTenantIdOrderBySortOrderAsc(tenantId);
        Set<String> keys = new HashSet<>();
        for (AdmissionField field : existing) {
            keys.add(field.getFieldKey());
        }
        addCore(tenantId, keys, "studentName", "Student name", AdmissionFieldType.TEXT, 10);
        addCore(tenantId, keys, "guardianName", "Guardian name", AdmissionFieldType.TEXT, 20);
        addCore(tenantId, keys, "cnic", "CNIC", AdmissionFieldType.TEXT, 30);
        addCore(tenantId, keys, "phone", "Mobile number", AdmissionFieldType.TEL, 40);
    }

    private void addCore(UUID tenantId, Set<String> keys, String key, String label,
                         AdmissionFieldType type, int order) {
        if (keys.contains(key)) {
            return;
        }
        AdmissionField field = new AdmissionField();
        field.setTenantId(tenantId);
        field.setFieldKey(key);
        field.setLabel(label);
        field.setFieldType(type);
        field.setRequired(true);
        field.setLocked(true);
        field.setEnabled(true);
        field.setSortOrder(order);
        fieldRepository.save(field);
        keys.add(key);
    }

    private AdmissionSettings settings(UUID tenantId) {
        return settingsRepository.findById(tenantId).orElseGet(() -> {
            AdmissionSettings s = new AdmissionSettings();
            s.setTenantId(tenantId);
            s.setEnabled(true);
            s.setCtaLabel("Apply for Admission");
            s.setUpdatedAt(Instant.now());
            return settingsRepository.save(s);
        });
    }

    private Tenant resolveSchool(String code) {
        if (code != null && !code.isBlank()) {
            return tenantRepository.findByCodeIgnoreCase(code.trim())
                    .orElseThrow(() -> new ResourceNotFoundException("School", code));
        }
        List<Tenant> active = tenantRepository.findByStatusInOrderByCreatedAtAsc(
                List.of(TenantStatus.ACTIVE, TenantStatus.PENDING));
        if (active.isEmpty()) {
            throw new ResourceNotFoundException("No school is configured yet");
        }
        return active.get(0);
    }

    private void requireCampus(UUID tenantId, UUID campusId) {
        Campus campus = campusRepository.findById(campusId)
                .orElseThrow(() -> new ResourceNotFoundException("Campus", campusId));
        if (!tenantId.equals(campus.getTenantId())) {
            throw new BusinessException("Campus does not belong to this school");
        }
    }

    private void applyPeriod(SchoolOpenPeriod period, Map<String, Object> body) {
        int day = body.get("dayOfWeek") instanceof Number n ? n.intValue() : period.getDayOfWeek();
        if (day < 1 || day > 7) {
            throw new BusinessException("Day of week must be 1 (Monday) through 7 (Sunday)");
        }
        LocalTime start = parseTime(body.get("startTime"), period.getStartTime());
        LocalTime end = parseTime(body.get("endTime"), period.getEndTime());
        if (start == null || end == null || !end.isAfter(start)) {
            throw new BusinessException("Open period must have an end time after the start");
        }
        int minutes = body.get("slotMinutes") instanceof Number n ? n.intValue() : period.getSlotMinutes();
        if (minutes <= 0) {
            minutes = 30;
        }
        if (!SLOT_MINUTES.contains(minutes)) {
            throw new BusinessException("Slot length must be 15, 20, 30, 45 or 60 minutes");
        }
        UUID campusId = parseUuid(body.get("campusId"));
        if (campusId != null) {
            requireCampus(period.getTenantId(), campusId);
        }
        period.setDayOfWeek(day);
        period.setStartTime(start);
        period.setEndTime(end);
        period.setSlotMinutes(minutes);
        if (body.containsKey("campusId")) {
            period.setCampusId(campusId);
        }
        if (body.containsKey("enabled")) {
            period.setEnabled(bool(body.get("enabled")));
        }
    }

    private Map<String, Object> toSettings(AdmissionSettings settings) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("enabled", settings.isEnabled());
        m.put("appointmentEnabled", settings.isAppointmentEnabled());
        m.put("appointmentRequired", settings.isAppointmentRequired());
        m.put("ctaLabel", settings.getCtaLabel());
        return m;
    }

    private Map<String, Object> toField(AdmissionField field) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", field.getId());
        m.put("fieldKey", field.getFieldKey());
        m.put("label", field.getLabel());
        m.put("fieldType", field.getFieldType().name());
        m.put("required", field.isRequired());
        m.put("locked", field.isLocked());
        m.put("enabled", field.isEnabled());
        m.put("options", field.getOptions());
        m.put("sortOrder", field.getSortOrder());
        return m;
    }

    private Map<String, Object> toPeriod(SchoolOpenPeriod period) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", period.getId());
        m.put("campusId", period.getCampusId());
        m.put("dayOfWeek", period.getDayOfWeek());
        m.put("startTime", period.getStartTime().toString());
        m.put("endTime", period.getEndTime().toString());
        m.put("slotMinutes", period.getSlotMinutes());
        m.put("enabled", period.isEnabled());
        return m;
    }

    private Map<String, Object> toApplication(AdmissionApplication app) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", app.getId());
        m.put("campusId", app.getCampusId());
        m.put("studentName", app.getStudentName());
        m.put("guardianName", app.getGuardianName());
        m.put("cnic", app.getCnic());
        m.put("phone", app.getPhone());
        m.put("status", app.getStatus().name());
        m.put("createdAt", app.getCreatedAt());
        m.put("payload", readJson(app.getPayload()));
        return m;
    }

    private Map<String, Object> toAppointment(AdmissionAppointment appt) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", appt.getId());
        m.put("applicationId", appt.getApplicationId());
        m.put("campusId", appt.getCampusId());
        m.put("slotStart", appt.getSlotStart().toString());
        m.put("slotEnd", appt.getSlotEnd().toString());
        m.put("status", appt.getStatus().name());
        return m;
    }

    private String writeJson(Map<String, Object> answers) {
        try {
            return objectMapper.writeValueAsString(answers);
        } catch (JsonProcessingException e) {
            throw new BusinessException("Could not save application answers");
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readJson(String raw) {
        if (raw == null || raw.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(raw, Map.class);
        } catch (JsonProcessingException e) {
            return Map.of("raw", raw);
        }
    }

    private static boolean isCore(String key) {
        for (String core : CORE_KEYS) {
            if (core.equals(key)) {
                return true;
            }
        }
        return false;
    }

    private static AdmissionFieldType parseType(String raw) {
        if (raw == null || raw.isBlank()) {
            return AdmissionFieldType.TEXT;
        }
        try {
            return AdmissionFieldType.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new BusinessException("Unknown field type");
        }
    }

    private static String slug(String raw) {
        if (raw == null) {
            return "";
        }
        String s = raw.trim();
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (Character.isLetterOrDigit(c)) {
                b.append(i == 0 ? Character.toLowerCase(c) : c);
            } else if (c == '_' || c == '-') {
                b.append(c == '-' ? '_' : c);
            }
        }
        return b.toString();
    }

    static String normalizeCnic(String raw) {
        String digits = digitsOnly(raw);
        if (digits.length() != 13) {
            throw new BusinessException("CNIC must be 13 digits");
        }
        return digits;
    }

    static String normalizePhone(String raw) {
        String digits = digitsOnly(raw);
        if (digits.length() < 10 || digits.length() > 15) {
            throw new BusinessException("Mobile number must be 10 to 15 digits");
        }
        return digits;
    }

    private static String digitsOnly(String raw) {
        if (raw == null) {
            return "";
        }
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (Character.isDigit(c)) {
                b.append(c);
            }
        }
        return b.toString();
    }

    private static String required(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new BusinessException("MISSING_FIELD", label + " is required");
        }
        return value.trim();
    }

    private static String str(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private static boolean bool(Object value) {
        if (value instanceof Boolean b) {
            return b;
        }
        return "true".equalsIgnoreCase(String.valueOf(value));
    }

    private static UUID parseUuid(Object value) {
        if (value == null) {
            return null;
        }
        String s = String.valueOf(value).trim();
        if (s.isBlank() || "null".equalsIgnoreCase(s)) {
            return null;
        }
        try {
            return UUID.fromString(s);
        } catch (IllegalArgumentException ex) {
            throw new BusinessException("Invalid id");
        }
    }

    private static Instant parseInstant(Object value) {
        if (value == null) {
            return null;
        }
        String s = String.valueOf(value).trim();
        if (s.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(s);
        } catch (DateTimeParseException ex) {
            throw new BusinessException("Invalid appointment time");
        }
    }

    private static LocalTime parseTime(Object value, LocalTime fallback) {
        if (value == null) {
            return fallback;
        }
        String s = String.valueOf(value).trim();
        if (s.isBlank()) {
            return fallback;
        }
        try {
            return LocalTime.parse(s);
        } catch (DateTimeParseException ex) {
            throw new BusinessException("Invalid time");
        }
    }
}
