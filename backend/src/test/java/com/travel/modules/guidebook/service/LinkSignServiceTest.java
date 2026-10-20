package com.travel.modules.guidebook.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class LinkSignServiceTest {

    private final LinkSignService signService = new LinkSignService("test-secret");

    @Test
    void verifiesItsOwnSignature() {
        String signature = signService.sign("r70001");

        assertThat(signature).hasSize(64);
        assertThat(signService.verify("r70001", signature)).isTrue();
    }

    @Test
    void rejectsTamperedSignatureOrResourceId() {
        String signature = signService.sign("r70001");

        assertThat(signService.verify("r70002", signature)).isFalse();
        assertThat(signService.verify("r70001", signature.substring(1) + "0")).isFalse();
        assertThat(signService.verify("r70001", null)).isFalse();
    }

    @Test
    void differentSecretsProduceDifferentSignatures() {
        String other = new LinkSignService("other-secret").sign("r70001");

        assertThat(other).isNotEqualTo(signService.sign("r70001"));
    }
}
