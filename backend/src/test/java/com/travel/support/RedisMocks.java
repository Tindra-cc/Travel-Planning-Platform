package com.travel.support;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import lombok.Getter;

/** StringRedisTemplate 的最小 Mockito 测试桩。 */
@Getter
public final class RedisMocks {

    private final StringRedisTemplate template;
    private final ValueOperations<String, String> values;

    private RedisMocks(StringRedisTemplate template, ValueOperations<String, String> values) {
        this.template = template;
        this.values = values;
    }

    @SuppressWarnings("unchecked")
    public static RedisMocks create() {
        StringRedisTemplate template = mock(StringRedisTemplate.class);
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(template.opsForValue()).thenReturn(values);
        return new RedisMocks(template, values);
    }
}
