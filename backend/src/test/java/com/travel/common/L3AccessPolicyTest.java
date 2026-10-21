package com.travel.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.travel.modules.auth.entity.User;
import com.travel.modules.auth.mapper.UserMapper;
import com.travel.support.TestFixtures;

@ExtendWith(MockitoExtension.class)
class L3AccessPolicyTest {

    @Mock
    private UserMapper userMapper;

    private L3AccessPolicy policy;
    private UserLevelPolicy userLevelPolicy;

    @BeforeEach
    void setUp() {
        policy = new L3AccessPolicy(userMapper);
        ReflectionTestUtils.setField(policy, "trustedUserIds", "5, 9");
        userLevelPolicy = new UserLevelPolicy(policy);
    }

    @Test
    void grantsL3User() {
        when(userMapper.selectById(1L)).thenReturn(level(1L, "L3"));

        assertThat(policy.hasAccess(1L)).isTrue();
    }

    @Test
    void grantsWhitelistedUserWithoutLevel() {
        assertThat(policy.hasAccess(5L)).isTrue();
    }

    @Test
    void deniesRegularUser() {
        when(userMapper.selectById(2L)).thenReturn(level(2L, "L1"));

        assertThat(policy.hasAccess(2L)).isFalse();
    }

    @Test
    void deniesMissingUserAndNullId() {
        assertThat(policy.hasAccess(null)).isFalse();
        assertThat(policy.hasAccess(3L)).isFalse();
    }

    @Test
    void requireExpertThrowsForbiddenForRegularUser() {
        when(userMapper.selectById(2L)).thenReturn(level(2L, "L1"));

        assertThatThrownBy(() -> userLevelPolicy.requireExpert(2L))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getResultCode())
                .isEqualTo(ResultCode.FORBIDDEN);
    }

    @Test
    void requireExpertPassesForWhitelistedUser() {
        userLevelPolicy.requireExpert(9L);
    }

    private User level(long id, String level) {
        User user = TestFixtures.user(id, "1");
        user.setUserLevel(level);
        return user;
    }
}
