package com.travel.modules.guidebook.generation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

class ItineraryJsonParserTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void parsesPlainJson() {
        GeneratedContent content = ItineraryJsonParser.parse(mapper, """
                {"title":"成都三日","days":[{"day":1,"nodes":[
                  {"nodeId":"d1-1","timeSlot":{"start":"09:00","end":"11:00"},
                   "type":"activity","place":{"name":"宽窄巷子"},"source":"[K1]"}]}],
                 "packingList":["雨具"]}
                """);
        assertThat(content.getTitle()).isEqualTo("成都三日");
        assertThat(content.getDays()).hasSize(1);
        assertThat(content.getDays().get(0).getNodes().get(0).getPlace().getName())
                .isEqualTo("宽窄巷子");
    }

    @Test
    void stripsMarkdownFence() {
        GeneratedContent content = ItineraryJsonParser.parse(mapper,
                "```json\n{\"days\":[{\"day\":1,\"nodes\":[]}]}\n```");
        assertThat(content.getDays()).hasSize(1);
    }

    @Test
    void rejectsMissingDays() {
        assertThatThrownBy(() -> ItineraryJsonParser.parse(mapper, "{\"title\":\"x\"}"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNonJson() {
        assertThatThrownBy(() -> ItineraryJsonParser.parse(mapper, "抱歉，我无法生成"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
