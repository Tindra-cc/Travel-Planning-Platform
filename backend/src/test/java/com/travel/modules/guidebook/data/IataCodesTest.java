package com.travel.modules.guidebook.data;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class IataCodesTest {

    @Test
    void resolvesChineseCityNamesWithSuffix() {
        assertThat(IataCodes.resolve("北京")).contains("PEK");
        assertThat(IataCodes.resolve("东京都")).contains("TYO");
        assertThat(IataCodes.resolve("成都市")).contains("CTU");
    }

    @Test
    void passesThroughExplicitIataCode() {
        assertThat(IataCodes.resolve("pek")).contains("PEK");
    }

    @Test
    void unknownCityIsEmpty() {
        assertThat(IataCodes.resolve("不存在的地方")).isEmpty();
    }
}
