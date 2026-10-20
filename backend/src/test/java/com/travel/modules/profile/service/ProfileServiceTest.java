package com.travel.modules.profile.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.travel.common.BizException;
import com.travel.common.ResultCode;
import com.travel.common.UserContext;
import com.travel.common.UserContext.LoginUser;
import com.travel.modules.auth.entity.User;
import com.travel.modules.auth.mapper.UserMapper;
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

@ExtendWith(MockitoExtension.class)
class ProfileServiceTest {

    private static final long USER_ID = 1L;

    @Mock
    private UserProfileMapper profileMapper;
    @Mock
    private TravelerMapper travelerMapper;
    @Mock
    private UserMapper userMapper;

    private ProfileService service;

    @BeforeEach
    void setUp() {
        UserContext.set(new LoginUser(USER_ID, "user1@test.local"));
        service = new ProfileService(profileMapper, travelerMapper, userMapper);
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void getProfileCreatesMissingProfile() {
        when(profileMapper.selectOne(any(Wrapper.class))).thenReturn(null);
        when(profileMapper.insert(any(UserProfile.class))).thenAnswer(invocation -> {
            UserProfile profile = invocation.getArgument(0);
            profile.setId(7L);
            return 1;
        });

        ProfileResponse response = service.getProfile();

        assertThat(response.getProfileId()).isEqualTo(7L);
        assertThat(response.getUserId()).isEqualTo(USER_ID);
        assertThat(response.getVersion()).isZero();
        verify(profileMapper).insert(any(UserProfile.class));
    }

    @Test
    void getProfileReturnsExistingValues() {
        when(profileMapper.selectOne(any(Wrapper.class))).thenReturn(profile("balanced", "relaxed"));

        ProfileResponse response = service.getProfile();

        assertThat(response.getBasic().getBudgetLevel()).isEqualTo("balanced");
        assertThat(response.getBasic().getPace()).isEqualTo("relaxed");
        assertThat(response.getBasic().getInterestTags()).containsExactly("美食");
        assertThat(response.getBasic().getPartyStructure().getAdults()).isEqualTo(2);
        assertThat(response.getVersion()).isEqualTo(2);
    }

    @Test
    void updateOnlyOverwritesProvidedFieldsAndIncrementsVersion() {
        UserProfile existing = profile("balanced", "relaxed");
        when(profileMapper.selectOne(any(Wrapper.class))).thenReturn(existing);
        when(userMapper.selectById(USER_ID)).thenReturn(user(false));

        ProfileUpdateRequest request = new ProfileUpdateRequest();
        BasicProfile basic = new BasicProfile();
        basic.setPace("tight");
        request.setBasic(basic);

        ProfileResponse response = service.updateProfile(request);

        assertThat(response.getBasic().getPace()).isEqualTo("tight");
        assertThat(response.getBasic().getBudgetLevel()).isEqualTo("balanced");
        assertThat(response.getBasic().getInterestTags()).containsExactly("美食");
        assertThat(response.getVersion()).isEqualTo(3);
        verify(profileMapper).updateById(existing);
    }

    @Test
    void completingAllFieldsMarksUserProfileCompleted() {
        UserProfile existing = new UserProfile();
        existing.setId(7L);
        existing.setUserId(USER_ID);
        existing.setVersion(0);
        when(profileMapper.selectOne(any(Wrapper.class))).thenReturn(existing);
        User user = user(false);
        when(userMapper.selectById(USER_ID)).thenReturn(user);

        ProfileUpdateRequest request = new ProfileUpdateRequest();
        BasicProfile basic = new BasicProfile();
        basic.setBudgetLevel("comfort");
        basic.setPace("balanced");
        basic.setInterestTags(List.of("美食"));
        PartyStructure party = new PartyStructure();
        party.setAdults(2);
        basic.setPartyStructure(party);
        request.setBasic(basic);

        service.updateProfile(request);

        assertThat(user.getProfileCompleted()).isTrue();
        verify(userMapper).updateById(user);
    }

    @Test
    void completedProfileDoesNotRewriteUser() {
        UserProfile existing = profile("balanced", "relaxed");
        when(profileMapper.selectOne(any(Wrapper.class))).thenReturn(existing);
        when(userMapper.selectById(USER_ID)).thenReturn(user(true));

        service.updateProfile(new ProfileUpdateRequest());

        verify(userMapper, never()).updateById(any(User.class));
    }

    @Test
    void checkListsMissingFields() {
        when(profileMapper.selectOne(any(Wrapper.class))).thenReturn(emptyProfile());

        ProfileCheckResponse check = service.check();

        assertThat(check.isCompleted()).isFalse();
        assertThat(check.getMissingFields())
                .containsExactly("budgetLevel", "pace", "interestTags", "partyStructure");
    }

    @Test
    void checkReturnsCompleteWhenAllFieldsPresent() {
        when(profileMapper.selectOne(any(Wrapper.class))).thenReturn(profile("balanced", "relaxed"));

        ProfileCheckResponse check = service.check();

        assertThat(check.isCompleted()).isTrue();
        assertThat(check.getMissingFields()).isEmpty();
    }

    @Test
    void addTravelerAssignsCurrentUser() {
        when(travelerMapper.insert(any(Traveler.class))).thenAnswer(invocation -> {
            Traveler traveler = invocation.getArgument(0);
            traveler.setId(3L);
            return 1;
        });

        TravelerResponse response = service.addTraveler(travelerRequest("小明"));

        assertThat(response.getTravelerId()).isEqualTo(3L);
        assertThat(response.getName()).isEqualTo("小明");
        ArgumentCaptor<Traveler> captor = ArgumentCaptor.forClass(Traveler.class);
        verify(travelerMapper).insert(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(USER_ID);
    }

    @Test
    void listTravelersMapsRows() {
        Traveler traveler = new Traveler();
        traveler.setId(3L);
        traveler.setUserId(USER_ID);
        traveler.setName("小明");
        when(travelerMapper.selectList(any(Wrapper.class))).thenReturn(List.of(traveler));

        List<TravelerResponse> travelers = service.listTravelers();

        assertThat(travelers).hasSize(1);
        assertThat(travelers.get(0).getName()).isEqualTo("小明");
    }

    @Test
    void updateTravelerRejectsMissingRow() {
        when(travelerMapper.selectById(9L)).thenReturn(null);

        assertBizCode(() -> service.updateTraveler(9L, travelerRequest("小明")),
                ResultCode.NOT_FOUND);
    }

    @Test
    void updateTravelerRejectsOtherUsersRow() {
        Traveler traveler = new Traveler();
        traveler.setId(9L);
        traveler.setUserId(2L);
        when(travelerMapper.selectById(9L)).thenReturn(traveler);

        assertBizCode(() -> service.updateTraveler(9L, travelerRequest("小明")),
                ResultCode.NOT_OWNER);
        verify(travelerMapper, never()).updateById(any(Traveler.class));
    }

    @Test
    void updateTravelerAppliesFields() {
        Traveler traveler = new Traveler();
        traveler.setId(9L);
        traveler.setUserId(USER_ID);
        traveler.setName("旧名字");
        when(travelerMapper.selectById(9L)).thenReturn(traveler);

        TravelerResponse response = service.updateTraveler(9L, travelerRequest("新名字"));

        assertThat(response.getName()).isEqualTo("新名字");
        verify(travelerMapper).updateById(traveler);
    }

    @Test
    void deleteTravelerRemovesOwnedRow() {
        Traveler traveler = new Traveler();
        traveler.setId(9L);
        traveler.setUserId(USER_ID);
        when(travelerMapper.selectById(9L)).thenReturn(traveler);

        service.deleteTraveler(9L);

        verify(travelerMapper).deleteById(eq(9L));
    }

    private UserProfile profile(String budgetLevel, String pace) {
        UserProfile profile = new UserProfile();
        profile.setId(7L);
        profile.setUserId(USER_ID);
        profile.setBudgetLevel(budgetLevel);
        profile.setPace(pace);
        profile.setInterestTags(List.of("美食"));
        profile.setPartyAdults(2);
        profile.setVersion(2);
        return profile;
    }

    private UserProfile emptyProfile() {
        UserProfile profile = new UserProfile();
        profile.setId(7L);
        profile.setUserId(USER_ID);
        profile.setVersion(0);
        return profile;
    }

    private User user(boolean completed) {
        User user = new User();
        user.setId(USER_ID);
        user.setProfileCompleted(completed);
        return user;
    }

    private TravelerRequest travelerRequest(String name) {
        TravelerRequest request = new TravelerRequest();
        request.setName(name);
        return request;
    }

    private void assertBizCode(Runnable action, ResultCode expected) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(BizException.class,
                        e -> assertThat(e.getResultCode()).isEqualTo(expected));
    }
}
