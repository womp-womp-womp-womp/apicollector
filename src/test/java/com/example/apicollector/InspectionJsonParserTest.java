package com.example.apicollector;

import com.example.exception.ApiParsingException;
import com.example.jsonparser.dto.InspectionDto;
import com.example.jsonparser.parser.InspectionJsonParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InspectionJsonParserTest {

    private InspectionJsonParser parser;

    @BeforeEach
    void setUp() {
        parser = new InspectionJsonParser(new ObjectMapper().registerModule(new JavaTimeModule()));
    }

    @Test
    void parseMapsApiResponseToDtosAndNormalizesContractors() {
        String json = """
                {
                  "data": [
                    {
                      "id": 10,
                      "public_id": 20,
                      "date": "2026-01-02T03:04:05",
                      "stat_fails": null,
                      "stat_critical_fails": 2,
                      "place": {
                        "name": "Cafe",
                        "address": "Street 1",
                        "groups": [
                          {"name": " Alpha "},
                          {"name": ""},
                          {"name": "Alpha"},
                          {"name": "Beta"}
                        ]
                      }
                    },
                    null,
                    {
                      "id": 11,
                      "public_id": 21,
                      "date": "2026-01-03T03:04:05",
                      "place": null
                    }
                  ]
                }
                """;

        List<InspectionDto> result = parser.parse(json);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).publicId()).isEqualTo(20L);
        assertThat(result.get(0).violationsCount()).isZero();
        assertThat(result.get(0).criticalViolationsCount()).isEqualTo(2);
        assertThat(result.get(0).contractors()).containsExactly("Alpha", "Beta");
        assertThat(result.get(1).locationName()).isNull();
        assertThat(result.get(1).contractors()).isEmpty();
    }

    @Test
    void parseTotalValidatesTotalField() {
        assertThat(parser.parseTotal("{\"total\": 42}")).isEqualTo(42);

        assertThatThrownBy(() -> parser.parseTotal("{\"data\": []}"))
                .isInstanceOf(ApiParsingException.class)
                .hasMessage("External API response does not contain required 'total' field");
        assertThatThrownBy(() -> parser.parseTotal("{\"total\": -1}"))
                .isInstanceOf(ApiParsingException.class)
                .hasMessage("External API field 'total' must not be negative");
        assertThatThrownBy(() -> parser.parseTotal("{\"total\": \"abc\"}"))
                .isInstanceOf(ApiParsingException.class)
                .hasMessage("External API field 'total' must be a number");
    }

    @Test
    void parseRejectsInvalidResponseShapes() {
        assertThatThrownBy(() -> parser.parse(" "))
                .isInstanceOf(ApiParsingException.class)
                .hasMessage("External API response body is empty");
        assertThatThrownBy(() -> parser.parse("{}"))
                .isInstanceOf(ApiParsingException.class)
                .hasMessage("External API response does not contain required 'data' field");
        assertThatThrownBy(() -> parser.parse("{\"data\": {}}"))
                .isInstanceOf(ApiParsingException.class)
                .hasMessage("External API field 'data' must be an array");
        assertThatThrownBy(() -> parser.parse("{broken"))
                .isInstanceOf(ApiParsingException.class)
                .hasMessage("Failed to parse inspections JSON");
    }
}
