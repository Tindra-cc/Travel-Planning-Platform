package com.travel.modules.guidebook.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

class ShareCodeTest {

    private static final Pattern BASE62 = Pattern.compile("^[0-9A-Za-z]{16}$");

    @Test
    void generatesSixteenBase62Chars() {
        String code = ShareCodeGenerator.generate();

        assertThat(code).hasSize(16);
        assertThat(code).matches(BASE62);
    }

    @Test
    void generatesDistinctCodes() {
        Set<String> codes = new HashSet<>();
        for (int i = 0; i < 500; i++) {
            codes.add(ShareCodeGenerator.generate());
        }

        assertThat(codes).hasSize(500);
    }
}
