package com.erpschool.bootstrap;

import com.erpschool.campus.entity.Campus;
import com.erpschool.campus.repository.CampusRepository;
import com.erpschool.common.exception.BusinessException;
import com.erpschool.tenant.entity.Tenant;
import com.erpschool.tenant.entity.TenantStatus;
import com.erpschool.tenant.repository.TenantRepository;
import com.erpschool.user.dto.CreateUserResponse;
import com.erpschool.user.entity.UserRole;
import com.erpschool.user.repository.UserRepository;
import com.erpschool.user.service.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class SetupService {

    private final TenantRepository tenantRepository;
    private final CampusRepository campusRepository;
    private final UserRepository userRepository;
    private final UserService userService;

    public SetupService(TenantRepository tenantRepository,
                        CampusRepository campusRepository,
                        UserRepository userRepository,
                        UserService userService) {
        this.tenantRepository = tenantRepository;
        this.campusRepository = campusRepository;
        this.userRepository = userRepository;
        this.userService = userService;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> status() {
        Map<String, Object> m = new LinkedHashMap<>();
        long schools = tenantRepository.count();
        m.put("needsSetup", schools == 0);
        m.put("schoolCount", schools);
        m.put("hasOwner", userRepository.findByUsernameIgnoreCase("erp.owner").isPresent());
        return m;
    }

    @Transactional
    public Map<String, Object> complete(String schoolName, List<String> branchNames) {
        if (tenantRepository.count() > 0) {
            throw new BusinessException("School already exists. Sign in instead of running first-time setup.");
        }
        if (schoolName == null || schoolName.isBlank()) {
            throw new BusinessException("School name is required");
        }
        List<String> branches = branchNames == null ? List.of() : branchNames.stream()
                .filter(n -> n != null && !n.isBlank())
                .map(String::trim)
                .toList();
        if (branches.size() < 3) {
            branches = List.of("Main Campus", "Canal Campus", "Cantt Campus");
        }
        String code = schoolName.replaceAll("[^A-Za-z0-9]", "").toUpperCase(Locale.ROOT);
        if (code.length() < 3) {
            code = (code + "SCH").substring(0, 3);
        } else {
            code = code.substring(0, Math.min(8, code.length()));
        }
        Tenant tenant = new Tenant();
        tenant.setCode(code);
        tenant.setName(schoolName.trim());
        tenant.setStatus(TenantStatus.ACTIVE);
        tenant.setCity("Lahore");
        tenant.setCountry("Pakistan");
        tenant.setTimezone("Asia/Karachi");
        tenant.setAcademicYear("2026-2027");
        tenant.setAcademicSession("2026-2027");
        tenant = tenantRepository.save(tenant);

        List<Map<String, Object>> campuses = new ArrayList<>();
        String[] codes = {"MAIN", "CANAL", "CANTT"};
        for (int i = 0; i < 3; i++) {
            Campus campus = new Campus();
            campus.setTenantId(tenant.getId());
            campus.setName(branches.get(i));
            campus.setCode(codes[i]);
            campus.setCity("Lahore");
            campus = campusRepository.save(campus);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", campus.getId());
            row.put("name", campus.getName());
            row.put("code", campus.getCode());
            campuses.add(row);
        }

        CreateUserResponse admin = userService.createInternal(
                tenant.getId(), tenant.getCode(), UserRole.SCHOOL_ADMIN,
                "School", "Admin", "admin@" + code.toLowerCase(Locale.ROOT) + ".school",
                null, "ChangeMe@123", false);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("tenant", Map.of("id", tenant.getId(), "name", tenant.getName(), "code", tenant.getCode()));
        out.put("branches", campuses);
        out.put("admin", Map.of(
                "username", admin.getUser().getUsername(),
                "temporaryPassword", admin.getTemporaryPassword()));
        out.put("message", "School created with 3 branches. Sign in as the school admin and change the password.");
        return out;
    }
}
