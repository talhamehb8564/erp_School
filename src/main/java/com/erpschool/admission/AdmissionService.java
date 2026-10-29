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
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class AdmissionService {

    static final ZoneId ZONE = ZoneId.of("Asia/Karachi");
    private static final Set<Integer> SLOT_MINUTES = Set.of(15, 20, 30, 45, 60);
    private static final String[] CORE_KEYS = {"studentName", "guardianName", "cnic", "phone"};
    private static final List<AdmissionAppointmentStatus> HOLDING = List.of(
            AdmissionAppointmentStatus.BOOKED, AdmissionAppointmentStatus.ACCEPTED);
    private static final List<AdmissionApplicationStatus> BLOCKING_CNIC = List.of(
            AdmissionApplicationStatus.SUBMITTED,
            AdmissionApplicationStatus.REVIEWED,
            AdmissionApplicationStatus.ACCEPTED);

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

    @Transactional(readOnly = true)
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
        out.put("fields", fieldRepository.findByTenantIdOrderBySortOrderAsc(tenant.getId()).stream()
                .filter(AdmissionField::isEnabled)
                .map(this::toField)
                .toList());
        return out;
    }

    @Transactional
    public Map<String, Object> adminConfig() {
        UUID tenantId = TenantGuard.requireTenantId(null);
        if (!fieldRepository.existsByTenantId(tenantId)) {
            ensureCoreFields(tenantId);
        }
        AdmissionSettings settings = settingsRepository.findById(tenantId).orElseGet(() -> unsavedSettings(tenantId));
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
        if (body.containsKey("closedDays")) {
            settings.setClosedDays(writeClosedDays(parseClosedDays(body.get("closedDays"))));
        }
        if (body.containsKey("openStart")) {
            LocalTime start = parseTime(body.get("openStart"), settings.getOpenStart());
            if (start != null) {
                settings.setOpenStart(start);
            }
        }
        if (body.containsKey("openEnd")) {
            LocalTime end = parseTime(body.get("openEnd"), settings.getOpenEnd());
            if (end != null) {
                settings.setOpenEnd(end);
            }
        }
        if (body.get("slotMinutes") instanceof Number n) {
            int minutes = n.intValue();
            if (!SLOT_MINUTES.contains(minutes)) {
                throw new BusinessException("Slot length must be 15, 20, 30, 45 or 60 minutes");
            }
            settings.setSlotMinutes(minutes);
        }
        if (settings.getOpenStart() != null && settings.getOpenEnd() != null
                && !settings.getOpenEnd().isAfter(settings.getOpenStart())) {
            throw new BusinessException("Closing time must be after opening time");
        }
        settings.setUpdatedAt(Instant.now());
        settings.setUpdatedBy(TenantContext.getUserId());
        settingsRepository.save(settings);
        if (body.containsKey("closedDays") || body.containsKey("openStart") || body.containsKey("openEnd")
                || body.containsKey("slotMinutes") || body.containsKey("sessions")) {
            regenerateOpenDays(tenantId, settings, body.get("sessions"));
        }
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
        var page = applicationRepository.findByTenantIdOrderByCreatedAtDesc(tenantId, pageable);
        Map<UUID, AdmissionAppointment> appts = appointmentsByApplication(tenantId, page.getContent());
        Map<UUID, String> campuses = campusNames(tenantId);
        return PageResponse.from(page.map(app -> toApplication(app, appts.get(app.getId()), campuses.get(app.getCampusId()))));
    }

    @Transactional(readOnly = true)
    public Map<String, Object> application(UUID id) {
        UUID tenantId = TenantGuard.requireTenantId(null);
        AdmissionApplication app = applicationRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Application", id));
        AdmissionAppointment appt = appointmentRepository
                .findFirstByTenantIdAndApplicationIdOrderByCreatedAtDesc(tenantId, id)
                .orElse(null);
        return toApplication(app, appt, campusNames(tenantId).get(app.getCampusId()));
    }

    @Transactional
    public Map<String, Object> updateApplicationStatus(UUID id, String statusRaw) {
        UUID tenantId = TenantGuard.requireTenantId(null);
        AdmissionApplication app = applicationRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Application", id));
        AdmissionApplicationStatus next;
        try {
            next = AdmissionApplicationStatus.valueOf(statusRaw.trim().toUpperCase(Locale.ROOT));
        } catch (Exception ex) {
            throw new BusinessException("Unknown application status");
        }
        AdmissionApplicationStatus prev = app.getStatus();
        if ((prev == AdmissionApplicationStatus.ACCEPTED
                || prev == AdmissionApplicationStatus.REJECTED
                || prev == AdmissionApplicationStatus.CANCELLED)
                && prev != next) {
            throw new BusinessException("ALREADY_DECIDED", "This application has already been " + prev.name().toLowerCase(Locale.ROOT));
        }
        app.setStatus(next);
        app.setUpdatedBy(TenantContext.getUserId());
        applicationRepository.save(app);
        AdmissionAppointment appt = appointmentRepository
                .findFirstByTenantIdAndApplicationIdOrderByCreatedAtDesc(tenantId, id)
                .orElse(null);
        if (appt != null) {
            if (next == AdmissionApplicationStatus.ACCEPTED) {
                appt.setStatus(AdmissionAppointmentStatus.ACCEPTED);
            } else if (next == AdmissionApplicationStatus.REJECTED) {
                appt.setStatus(AdmissionAppointmentStatus.REJECTED);
            } else if (next == AdmissionApplicationStatus.SUBMITTED) {
                appt.setStatus(AdmissionAppointmentStatus.BOOKED);
            }
            appt.setUpdatedBy(TenantContext.getUserId());
            appointmentRepository.save(appt);
        }
        return toApplication(app, appt, campusNames(tenantId).get(app.getCampusId()));
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> publicSlots(String code, LocalDate date, UUID campusId) {
        Tenant tenant = resolveSchool(code);
        Map<String, Object> summary = publicSummary(tenant.getId());
        if (!Boolean.TRUE.equals(summary.get("enabled")) || !Boolean.TRUE.equals(summary.get("appointmentEnabled"))) {
            return List.of();
        }
        return slots(tenant.getId(), date, campusId, false);
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
        rejectDuplicateCnic(tenant.getId(), cnic, studentName);

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
        try {
            applicationRepository.saveAndFlush(app);
        } catch (DataIntegrityViolationException ex) {
            throw duplicateCnicOrRethrow(tenant.getId(), cnic, studentName, ex);
        }

        String campusName = null;
        if (campusId != null) {
            campusName = campusRepository.findById(campusId).map(Campus::getName).orElse(null);
        }
        Map<String, Object> out = toApplication(app, null, campusName);
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
        AdmissionSettings settings = settingsRepository.findById(tenantId).orElse(null);
        if (parseClosedDays(settings == null ? "7" : settings.getClosedDays()).contains(dow)) {
            return List.of();
        }
        List<SchoolOpenPeriod> periods = usablePeriods(
                periodRepository.findByTenantIdAndDayOfWeekAndEnabledTrue(tenantId, dow), campusId);
        Instant dayStart = date.atStartOfDay(ZONE).toInstant();
        Instant dayEnd = date.plusDays(1).atStartOfDay(ZONE).toInstant();
        Set<Instant> taken = new HashSet<>();
        for (AdmissionAppointment booked : appointmentRepository.findHeldInRange(
                tenantId, campusId, dayStart, dayEnd, HOLDING)) {
            taken.add(booked.getSlotStart());
        }
        Instant now = Instant.now();
        /* One map keyed by start Instant so overlapping periods cannot emit 09:00 twice. */
        LinkedHashMap<Instant, Map<String, Object>> unique = new LinkedHashMap<>();
        if (periods.isEmpty()) {
            LocalTime startAt = settings != null && settings.getOpenStart() != null
                    ? settings.getOpenStart() : LocalTime.of(9, 0);
            LocalTime endAt = settings != null && settings.getOpenEnd() != null
                    ? settings.getOpenEnd() : LocalTime.of(13, 0);
            int minutes = settings != null && settings.getSlotMinutes() > 0 ? settings.getSlotMinutes() : 30;
            appendSlots(unique, date, startAt, endAt, minutes, now, taken, hideTaken);
        } else {
            for (SchoolOpenPeriod period : periods) {
                appendSlots(unique, date, period.getStartTime(), period.getEndTime(), period.getSlotMinutes(),
                        now, taken, hideTaken);
            }
        }
        return new ArrayList<>(unique.values());
    }

    Map<String, Object> book(UUID tenantId, AdmissionApplication app, UUID campusId, Instant slotStart) {
        if (slotStart == null) {
            throw new BusinessException("Appointment time is required");
        }
        AdmissionAppointment existing = appointmentRepository
                .findFirstByTenantIdAndApplicationIdOrderByCreatedAtDesc(tenantId, app.getId())
                .orElse(null);
        if (existing != null && HOLDING.contains(existing.getStatus())) {
            throw new BusinessException("SLOT_TAKEN", "This application already has an appointment");
        }
        LocalDate date = slotStart.atZone(ZONE).toLocalDate();
        List<Map<String, Object>> offered = slots(tenantId, date, campusId, false);
        Map<String, Object> match = null;
        for (Map<String, Object> s : offered) {
            if (slotStart.toString().equals(s.get("start"))) {
                match = s;
                break;
            }
        }
        if (match == null || Boolean.TRUE.equals(match.get("booked"))) {
            throw new BusinessException("SLOT_TAKEN", "That appointment time is not available");
        }
        Instant slotEnd = slotStart.plusSeconds(30 * 60);
        if (match.get("end") instanceof String end) {
            slotEnd = Instant.parse(end);
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
            AdmissionSettings s = unsavedSettings(tenantId);
            s.setUpdatedAt(Instant.now());
            return settingsRepository.save(s);
        });
    }

    private AdmissionSettings unsavedSettings(UUID tenantId) {
        AdmissionSettings s = new AdmissionSettings();
        s.setTenantId(tenantId);
        s.setEnabled(true);
        s.setCtaLabel("Apply for Admission");
        return s;
    }

    private Tenant resolveSchool(String code) {
        if (code != null && !code.isBlank()) {
            return tenantRepository.findByCodeIgnoreCase(code.trim())
                    .orElseThrow(() -> new ResourceNotFoundException("School", code));
        }
        return tenantRepository.findFirstByStatusInOrderByCreatedAtAsc(
                        List.of(TenantStatus.ACTIVE, TenantStatus.PENDING))
                .orElseThrow(() -> new ResourceNotFoundException("No school is configured yet"));
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
        m.put("closedDays", new ArrayList<>(parseClosedDays(settings.getClosedDays())));
        m.put("openStart", settings.getOpenStart() == null ? "09:00" : settings.getOpenStart().toString());
        m.put("openEnd", settings.getOpenEnd() == null ? "13:00" : settings.getOpenEnd().toString());
        m.put("slotMinutes", settings.getSlotMinutes() <= 0 ? 30 : settings.getSlotMinutes());
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
        return toApplication(app, null, null);
    }

    private Map<String, Object> toApplication(AdmissionApplication app, AdmissionAppointment appt, String campusName) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", app.getId());
        m.put("campusId", app.getCampusId());
        m.put("campusName", campusName);
        m.put("studentName", app.getStudentName());
        m.put("guardianName", app.getGuardianName());
        m.put("cnic", app.getCnic());
        m.put("phone", app.getPhone());
        m.put("status", app.getStatus().name());
        m.put("createdAt", app.getCreatedAt());
        m.put("payload", readJson(app.getPayload()));
        if (appt != null) {
            m.put("appointment", toAppointment(appt));
        }
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

    private void appendSlots(LinkedHashMap<Instant, Map<String, Object>> out, LocalDate date, LocalTime startAt,
                             LocalTime endAt, int minutes, Instant now, Set<Instant> taken, boolean hideTaken) {
        for (LocalTime t : slotStarts(startAt, endAt, minutes)) {
            Instant start = date.atTime(t).atZone(ZONE).toInstant();
            if (out.containsKey(start)) {
                continue;
            }
            Instant end = date.atTime(t.plusMinutes(minutes)).atZone(ZONE).toInstant();
            boolean booked = taken.contains(start);
            if (start.isAfter(now) && (!hideTaken || !booked)) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("start", start.toString());
                row.put("end", end.toString());
                row.put("booked", booked);
                out.put(start, row);
            }
        }
    }

    private void regenerateOpenDays(UUID tenantId, AdmissionSettings settings, Object sessionsRaw) {
        LinkedHashMap<String, OpenWindow> windows = new LinkedHashMap<>();
        if (sessionsRaw instanceof List<?> list && !list.isEmpty()) {
            for (Object row : list) {
                if (!(row instanceof Map<?, ?> map)) {
                    continue;
                }
                LocalTime start = parseTime(map.get("startTime"), settings.getOpenStart());
                LocalTime end = parseTime(map.get("endTime"), settings.getOpenEnd());
                int minutes = map.get("slotMinutes") instanceof Number n ? n.intValue() : settings.getSlotMinutes();
                if (start == null || end == null || !end.isAfter(start) || !SLOT_MINUTES.contains(minutes)) {
                    throw new BusinessException("Each opening period needs a start, end, and slot length");
                }
                windows.putIfAbsent(start + "|" + end + "|" + minutes, new OpenWindow(start, end, minutes));
            }
        }
        if (windows.isEmpty()) {
            LocalTime start = settings.getOpenStart() == null ? LocalTime.of(9, 0) : settings.getOpenStart();
            LocalTime end = settings.getOpenEnd() == null ? LocalTime.of(13, 0) : settings.getOpenEnd();
            int minutes = settings.getSlotMinutes() <= 0 ? 30 : settings.getSlotMinutes();
            windows.put(start + "|" + end + "|" + minutes, new OpenWindow(start, end, minutes));
        }
        Set<Integer> closed = parseClosedDays(settings.getClosedDays());
        periodRepository.deleteByTenantId(tenantId);
        UUID actor = TenantContext.getUserId();
        for (int day = 1; day <= 7; day++) {
            if (closed.contains(day)) {
                continue;
            }
            for (OpenWindow session : windows.values()) {
                SchoolOpenPeriod period = new SchoolOpenPeriod();
                period.setTenantId(tenantId);
                period.setDayOfWeek(day);
                period.setStartTime(session.start());
                period.setEndTime(session.end());
                period.setSlotMinutes(session.minutes());
                period.setEnabled(true);
                period.setCreatedBy(actor);
                periodRepository.save(period);
            }
        }
    }

    private record OpenWindow(LocalTime start, LocalTime end, int minutes) {
    }

    private Map<UUID, AdmissionAppointment> appointmentsByApplication(UUID tenantId, List<AdmissionApplication> apps) {
        if (apps == null || apps.isEmpty()) {
            return Map.of();
        }
        List<UUID> ids = apps.stream().map(AdmissionApplication::getId).toList();
        return appointmentRepository.findByTenantIdAndApplicationIdIn(tenantId, ids).stream()
                .collect(Collectors.toMap(AdmissionAppointment::getApplicationId, a -> a, (a, b) ->
                        a.getCreatedAt() != null && b.getCreatedAt() != null && a.getCreatedAt().isAfter(b.getCreatedAt()) ? a : b));
    }

    private Map<UUID, String> campusNames(UUID tenantId) {
        return campusRepository.findByTenantIdOrderByNameAsc(tenantId).stream()
                .collect(Collectors.toMap(Campus::getId, Campus::getName, (a, b) -> a));
    }

    static Set<Integer> parseClosedDays(Object raw) {
        Set<Integer> out = new LinkedHashSet<>();
        if (raw instanceof Collection<?> col) {
            for (Object item : col) {
                addClosedDay(out, item);
            }
        } else if (raw instanceof int[] arr) {
            for (int d : arr) {
                if (d >= 1 && d <= 7) {
                    out.add(d);
                }
            }
        } else if (raw != null) {
            String text = String.valueOf(raw).trim();
            if (!text.isBlank() && !"null".equalsIgnoreCase(text)) {
                for (String part : text.split("[,\\s]+")) {
                    addClosedDay(out, part);
                }
            }
        }
        if (out.isEmpty() && raw == null) {
            out.add(7);
        }
        return out;
    }

    private static void addClosedDay(Set<Integer> out, Object item) {
        if (item instanceof Number n) {
            int d = n.intValue();
            if (d >= 1 && d <= 7) {
                out.add(d);
            }
            return;
        }
        try {
            int d = Integer.parseInt(String.valueOf(item).trim());
            if (d >= 1 && d <= 7) {
                out.add(d);
            }
        } catch (NumberFormatException ignored) {
            /* skip */
        }
    }

    static String writeClosedDays(Set<Integer> days) {
        if (days == null || days.isEmpty()) {
            return "";
        }
        return days.stream().sorted().map(String::valueOf).collect(Collectors.joining(","));
    }

    static List<LocalTime> slotStarts(LocalTime start, LocalTime end, int minutes) {
        List<LocalTime> out = new ArrayList<>();
        if (start == null || end == null || minutes <= 0 || !end.isAfter(start)) {
            return out;
        }
        LocalTime t = start;
        while (!t.plusMinutes(minutes).isAfter(end)) {
            out.add(t);
            t = t.plusMinutes(minutes);
        }
        return out;
    }

    static List<SchoolOpenPeriod> usablePeriods(List<SchoolOpenPeriod> periods, UUID campusId) {
        if (periods == null || periods.isEmpty()) {
            return List.of();
        }
        if (campusId != null) {
            List<SchoolOpenPeriod> forCampus = new ArrayList<>();
            for (SchoolOpenPeriod period : periods) {
                if (campusId.equals(period.getCampusId())) {
                    forCampus.add(period);
                }
            }
            if (!forCampus.isEmpty()) {
                return forCampus;
            }
        }
        List<SchoolOpenPeriod> schoolWide = new ArrayList<>();
        for (SchoolOpenPeriod period : periods) {
            if (period.getCampusId() == null) {
                schoolWide.add(period);
            }
        }
        return schoolWide;
    }

    private void rejectDuplicateCnic(UUID tenantId, String cnic, String studentName) {
        AdmissionApplication existing = findBlockingCnic(tenantId, cnic);
        if (existing != null) {
            throw duplicateCnicException(existing, studentName);
        }
    }

    private BusinessException duplicateCnicOrRethrow(UUID tenantId, String cnic,
                                                     String studentName, DataIntegrityViolationException ex) {
        AdmissionApplication existing = findBlockingCnic(tenantId, cnic);
        if (existing != null) {
            return duplicateCnicException(existing, studentName);
        }
        String detail = ex.getMostSpecificCause() == null ? "" : String.valueOf(ex.getMostSpecificCause().getMessage());
        if (detail.toLowerCase(Locale.ROOT).contains("uq_admission_app_active_cnic")) {
            return duplicateCnicException(null, studentName);
        }
        throw ex;
    }

    private AdmissionApplication findBlockingCnic(UUID tenantId, String cnic) {
        List<AdmissionApplication> rows = applicationRepository.findByTenantIdAndCnicAndStatusIn(
                tenantId, cnic, BLOCKING_CNIC);
        AdmissionApplication accepted = null;
        AdmissionApplication other = null;
        for (AdmissionApplication row : rows) {
            if (row.getStatus() == AdmissionApplicationStatus.ACCEPTED) {
                accepted = row;
            } else if (other == null) {
                other = row;
            }
        }
        return accepted != null ? accepted : other;
    }

    static BusinessException duplicateCnicException(AdmissionApplication existing, String requestedName) {
        String name = existing != null && existing.getStudentName() != null && !existing.getStudentName().isBlank()
                ? existing.getStudentName()
                : (requestedName == null || requestedName.isBlank() ? "this student" : requestedName);
        if (existing != null && existing.getStatus() == AdmissionApplicationStatus.ACCEPTED) {
            return new BusinessException("ADMISSION_EXISTS_ACCEPTED",
                    "An admission request for " + name
                            + " using this CNIC has already been accepted. Please contact the school if you need further assistance.");
        }
        return new BusinessException("ADMISSION_EXISTS",
                "Your request has already been submitted for " + name
                        + " using this CNIC. Please wait — our team will contact you.");
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
