package com.travel.modules.expert.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import com.travel.common.BizException;
import com.travel.common.ResultCode;
import com.travel.common.UserContext;
import com.travel.common.UserContext.LoginUser;
import com.travel.common.UserLevelPolicy;

@ExtendWith(MockitoExtension.class)
class ExpertInterceptorTest {

    @Mock
    private UserLevelPolicy userLevelPolicy;

    private ExpertInterceptor interceptor;

    @BeforeEach
    void setUp() {
        interceptor = new ExpertInterceptor(userLevelPolicy);
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void nonExpertIsRejectedWith40300() {
        UserContext.set(new LoginUser(9L, "normal"));
        doThrow(new BizException(ResultCode.FORBIDDEN, "需要达人权限（L3）"))
                .when(userLevelPolicy).requireExpert(9L);

        assertThatThrownBy(() -> interceptor.preHandle(new MockHttpServletRequest(),
                new MockHttpServletResponse(), new Object()))
                .isInstanceOfSatisfying(BizException.class,
                        e -> assertThat(e.getResultCode()).isEqualTo(ResultCode.FORBIDDEN));
    }

    @Test
    void expertPassesThrough() throws Exception {
        UserContext.set(new LoginUser(7L, "expert"));

        assertThat(interceptor.preHandle(new MockHttpServletRequest(),
                new MockHttpServletResponse(), new Object())).isTrue();
    }
}
