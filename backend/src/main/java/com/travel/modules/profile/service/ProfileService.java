package com.travel.modules.profile.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.travel.common.BizException;
import com.travel.common.ResultCode;
import com.travel.common.UserContext;
import com.travel.modules.auth.entity.User;
import com.travel.modules.auth.mapper.UserMapper;
import com.travel.modules.profile.dto.AdvancedProfile;
import com.travel.modules.profile.dto.BasicProfile;
import com.travel.modules.profile.dto.PartyStructure;
import com.travel.modules.profile.dto.ProfileCheckResponse;
import com.travel.modules.profile.dto.ProfileResponse;
import com.travel.modules.profile.dto.ProfileUpdateRequest;
import com.travel.modules.profile.dto.TravelerRequest;
import com.travel.modules.profile.dto.TravelerResponse;
import com.travel.modules.profile.entity.Traveler;
import com.travel.modules.profile.entity.UserProfile;
import com.travel.modules.profile.mapper.TravelerMapper;
import com.travel.modules.profile.mapper.UserProfileMapper;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ProfileService {

    private final UserProfileMapper profileMapper;
    private final TravelerMapper travelerMapper;
    private final UserMapper userMapper;

    public ProfileResponse getProfile() {
        return toResponse(getOrCreate(UserContext.getUserId()));
    }

    public ProfileResponse updateProfile(ProfileUpdateRequest req) {
        Long userId = UserContext.getUserId();
        UserProfile profile = getOrCreate(userId);

        BasicProfile b = req.getBasic();
        if (b != null) {
            if (b.getBudgetLevel() != null) {
                profile.setBudgetLevel(b.getBudgetLevel());
            }
            if (b.getPace() != null) {
                profile.setPace(b.getPace());
            }
            if (b.getInterestTags() != null) {
                profile.setInterestTags(b.getInterestTags());
            }
            if (b.getDietaryRestrictions() != null) {
                profile.setDietaryRestrictions(b.getDietaryRestrictions());
            }
            if (b.getTripDays() != null) {
                profile.setTripDays(b.getTripDays());
            }
            if (b.getBudgetAmount() != null) {
                profile.setBudgetAmount(b.getBudgetAmount());
            }
            PartyStructure p = b.getPartyStructure();
            if (p != null) {
                if (p.getAdults() != null) {
                    profile.setPartyAdults(p.getAdults());
                }
                if (p.getChildren() != null) {
                    profile.setPartyChildren(p.getChildren());
                }
                if (p.getElderly() != null) {
                    profile.setPartyElderly(p.getElderly());
                }
                if (p.getPets() != null) {
                    profile.setPartyPets(p.getPets());
                }
            }
        }

        AdvancedProfile a = req.getAdvanced();
        if (a != null) {
            if (a.getSpecialPreferences() != null) {
                profile.setSpecialPreferences(a.getSpecialPreferences());
            }
            if (a.getAcceptRedEye() != null) {
                profile.setAcceptRedEye(a.getAcceptRedEye());
            }
            if (a.getTransportPreference() != null) {
                profile.setTransportPreference(a.getTransportPreference());
            }
            if (a.getAccommodationPreference() != null) {
                profile.setAccommodationPreference(a.getAccommodationPreference());
            }
        }

        profile.setVersion((profile.getVersion() == null ? 0 : profile.getVersion()) + 1);
        profileMapper.updateById(profile);
        syncCompleted(userId, profile);
        return toResponse(profile);
    }

    /**
     * 复盘建议的唯一写入入口：只允许白名单字段，并用版本条件防止覆盖并发修改。
     * @return 新版本号
     */
    public int applySuggestedFields(Map<String, Object> fields, int expectedVersion) {
        if (fields == null || fields.isEmpty()) {
            throw new BizException(ResultCode.CONFLICT, "画像建议为空");
        }
        Long userId = UserContext.getUserId();
        UserProfile profile = getOrCreate(userId);
        int currentVersion = profile.getVersion() == null ? 0 : profile.getVersion();
        if (currentVersion != expectedVersion) {
            throw new BizException(ResultCode.CONFLICT, "画像已被修改，请刷新后重试");
        }

        for (Map.Entry<String, Object> entry : fields.entrySet()) {
            switch (entry.getKey()) {
                case "pace" -> profile.setPace(requireEnum(entry.getValue(), "pace",
                        List.of("tight", "balanced", "relaxed")));
                case "budgetLevel" -> profile.setBudgetLevel(requireEnum(entry.getValue(), "budgetLevel",
                        List.of("budget", "economy", "balanced", "comfort", "luxury")));
                case "interestTags" -> profile.setInterestTags(requireTags(entry.getValue()));
                case "tripDays" -> profile.setTripDays(requireTripDays(entry.getValue()));
                default -> throw new BizException(ResultCode.CONFLICT,
                        "画像建议包含不支持的字段：" + entry.getKey());
            }
        }

        int nextVersion = expectedVersion + 1;
        profile.setVersion(nextVersion);
        int rows = profileMapper.update(profile, Wrappers.<UserProfile>update()
                .eq("id", profile.getId())
                .eq("version", expectedVersion));
        if (rows == 0) {
            throw new BizException(ResultCode.CONFLICT, "画像已被修改，请刷新后重试");
        }
        syncCompleted(userId, profile);
        return nextVersion;
    }

    public ProfileCheckResponse check() {
        List<String> missing = missingFields(getOrCreate(UserContext.getUserId()));
        return new ProfileCheckResponse(missing.isEmpty(), missing);
    }

    // ---- 出行人档案 ----

    public List<TravelerResponse> listTravelers() {
        return travelerMapper.selectList(
                        Wrappers.<Traveler>lambdaQuery().eq(Traveler::getUserId, UserContext.getUserId()))
                .stream().map(this::toTravelerResponse).toList();
    }

    public TravelerResponse addTraveler(TravelerRequest req) {
        Traveler t = new Traveler();
        t.setUserId(UserContext.getUserId());
        applyTraveler(t, req);
        travelerMapper.insert(t);
        return toTravelerResponse(t);
    }

    public TravelerResponse updateTraveler(Long travelerId, TravelerRequest req) {
        Traveler t = requireOwned(travelerId);
        applyTraveler(t, req);
        travelerMapper.updateById(t);
        return toTravelerResponse(t);
    }

    public void deleteTraveler(Long travelerId) {
        requireOwned(travelerId);
        travelerMapper.deleteById(travelerId);
    }

    // ---- 内部方法 ----

    private String requireEnum(Object value, String field, List<String> allowed) {
        if (value instanceof String text && allowed.contains(text)) {
            return text;
        }
        throw new BizException(ResultCode.CONFLICT, "画像建议中的 " + field + " 不合法");
    }

    private List<String> requireTags(Object value) {
        if (value instanceof List<?> list && list.stream().allMatch(String.class::isInstance)) {
            return list.stream().map(String.class::cast).toList();
        }
        throw new BizException(ResultCode.CONFLICT, "画像建议中的 interestTags 不合法");
    }

    private Integer requireTripDays(Object value) {
        if (value instanceof Number number) {
            int days = number.intValue();
            if (days > 0 && days <= 60) {
                return days;
            }
        }
        throw new BizException(ResultCode.CONFLICT, "画像建议中的 tripDays 不合法");
    }

    private UserProfile getOrCreate(Long userId) {
        UserProfile profile = profileMapper.selectOne(
                Wrappers.<UserProfile>lambdaQuery().eq(UserProfile::getUserId, userId));
        if (profile != null) {
            return profile;
        }
        profile = new UserProfile();
        profile.setUserId(userId);
        profile.setVersion(0);
        profileMapper.insert(profile);
        return profile;
    }

    private List<String> missingFields(UserProfile p) {
        List<String> missing = new ArrayList<>();
        if (!StringUtils.hasText(p.getBudgetLevel())) {
            missing.add("budgetLevel");
        }
        if (!StringUtils.hasText(p.getPace())) {
            missing.add("pace");
        }
        if (p.getInterestTags() == null || p.getInterestTags().isEmpty()) {
            missing.add("interestTags");
        }
        if (p.getPartyAdults() == null && p.getPartyChildren() == null
                && p.getPartyElderly() == null && p.getPartyPets() == null) {
            missing.add("partyStructure");
        }
        return missing;
    }

    private void syncCompleted(Long userId, UserProfile p) {
        boolean completed = missingFields(p).isEmpty();
        User user = userMapper.selectById(userId);
        if (user != null && !Boolean.valueOf(completed).equals(user.getProfileCompleted())) {
            user.setProfileCompleted(completed);
            userMapper.updateById(user);
        }
    }

    private Traveler requireOwned(Long travelerId) {
        Traveler t = travelerMapper.selectById(travelerId);
        if (t == null) {
            throw new BizException(ResultCode.NOT_FOUND, "出行人档案不存在");
        }
        if (!t.getUserId().equals(UserContext.getUserId())) {
            throw new BizException(ResultCode.NOT_OWNER, "无权访问该出行人档案");
        }
        return t;
    }

    private void applyTraveler(Traveler t, TravelerRequest req) {
        t.setName(req.getName());
        t.setIdType(req.getIdType());
        t.setIdNumber(req.getIdNumber());
        t.setBirthDate(req.getBirthDate());
        t.setSpecialNeeds(req.getSpecialNeeds());
    }

    private ProfileResponse toResponse(UserProfile p) {
        ProfileResponse resp = new ProfileResponse();
        resp.setProfileId(p.getId());
        resp.setUserId(p.getUserId());

        BasicProfile basic = new BasicProfile();
        basic.setBudgetLevel(p.getBudgetLevel());
        basic.setPace(p.getPace());
        basic.setInterestTags(p.getInterestTags());
        basic.setDietaryRestrictions(p.getDietaryRestrictions());
        basic.setTripDays(p.getTripDays());
        basic.setBudgetAmount(p.getBudgetAmount());
        PartyStructure party = new PartyStructure();
        party.setAdults(p.getPartyAdults());
        party.setChildren(p.getPartyChildren());
        party.setElderly(p.getPartyElderly());
        party.setPets(p.getPartyPets());
        basic.setPartyStructure(party);
        resp.setBasic(basic);

        AdvancedProfile advanced = new AdvancedProfile();
        advanced.setSpecialPreferences(p.getSpecialPreferences());
        advanced.setAcceptRedEye(p.getAcceptRedEye());
        advanced.setTransportPreference(p.getTransportPreference());
        advanced.setAccommodationPreference(p.getAccommodationPreference());
        resp.setAdvanced(advanced);

        resp.setVersion(p.getVersion());
        resp.setUpdatedAt(p.getUpdateTime());
        return resp;
    }

    private TravelerResponse toTravelerResponse(Traveler t) {
        return new TravelerResponse(t.getId(), t.getName(), t.getIdType(), t.getIdNumber(),
                t.getBirthDate(), t.getSpecialNeeds());
    }
}
