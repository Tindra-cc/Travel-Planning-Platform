package com.travel.modules.guidebook.data;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.travel.modules.chat.dto.IntentDto;
import com.travel.modules.guidebook.entity.VisaPolicy;
import com.travel.modules.guidebook.mapper.VisaPolicyMapper;
import com.travel.support.TestFixtures;

@ExtendWith(MockitoExtension.class)
class VisaPolicyProviderTest {

    @Mock
    private VisaPolicyMapper visaPolicyMapper;

    @Test
    void matchesDestinationByContains() {
        VisaPolicy policy = policy("东京", "需签证", "在留资格外活动许可");
        when(visaPolicyMapper.selectList(any())).thenReturn(List.of(policy));
        IntentDto intent = TestFixtures.intent();
        intent.setDestination("东京都");

        Optional<DataBlock> block = new VisaPolicyProvider(visaPolicyMapper).fetch(intent);

        assertThat(block).isPresent();
        assertThat(block.get().available()).isTrue();
        assertThat(block.get().summary()).contains("需签证", "公开演示数据");
        assertThat(block.get().data()).containsEntry("destination", "东京")
                .containsEntry("entryRequirements", "在留资格外活动许可");
    }

    @Test
    void returnsEmptyWhenNoPolicyMatches() {
        when(visaPolicyMapper.selectList(any())).thenReturn(List.of(policy("东京", "需签证", "")));

        assertThat(new VisaPolicyProvider(visaPolicyMapper).fetch(TestFixtures.intent())).isEmpty();
    }

    @Test
    void mapperFailureReturnsEmpty() {
        when(visaPolicyMapper.selectList(any())).thenThrow(new RuntimeException("db down"));

        assertThat(new VisaPolicyProvider(visaPolicyMapper).fetch(TestFixtures.intent())).isEmpty();
    }

    private VisaPolicy policy(String destination, String summary, String requirements) {
        VisaPolicy policy = new VisaPolicy();
        policy.setDestination(destination);
        policy.setNationality("CN");
        policy.setPolicySummary(summary);
        policy.setEntryRequirements(requirements);
        policy.setSourceName("公开演示数据");
        policy.setLastVerifiedAt(LocalDateTime.of(2026, 10, 1, 0, 0));
        return policy;
    }
}
