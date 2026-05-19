package com.example.jsonparser.parser;

import com.example.exception.ApiParsingException;
import com.example.jsonparser.dto.InspectionDto;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

@Component
public class InspectionJsonParser {

    private final ObjectMapper objectMapper;

    public InspectionJsonParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public List<InspectionDto> parse(String json) {
        validateJsonIsNotBlank(json);

        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode dataNode = root.get("data");

            if (dataNode == null) {
                throw new ApiParsingException("External API response does not contain required 'data' field");
            }

            if (!dataNode.isArray()) {
                throw new ApiParsingException("External API field 'data' must be an array");
            }

            InspectionApiResponse response = objectMapper.treeToValue(root, InspectionApiResponse.class);

            if (response.getData() == null) {
                return Collections.emptyList();
            }

            return response.getData()
                    .stream()
                    .filter(Objects::nonNull)
                    .map(this::toDto)
                    .toList();

        } catch (ApiParsingException e) {
            throw e;
        } catch (JsonProcessingException e) {
            throw new ApiParsingException("Failed to parse inspections JSON", e);
        } catch (Exception e) {
            throw new ApiParsingException("Unexpected error while parsing inspections JSON", e);
        }
    }

    public long parseTotal(String json) {
        validateJsonIsNotBlank(json);

        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode totalNode = root.get("total");

            if (totalNode == null) {
                throw new ApiParsingException("External API response does not contain required 'total' field");
            }

            if (!totalNode.canConvertToLong()) {
                throw new ApiParsingException("External API field 'total' must be a number");
            }

            long total = totalNode.asLong();

            if (total < 0) {
                throw new ApiParsingException("External API field 'total' must not be negative");
            }

            return total;
        } catch (ApiParsingException e) {
            throw e;
        } catch (JsonProcessingException e) {
            throw new ApiParsingException("Failed to parse total from inspections JSON", e);
        } catch (Exception e) {
            throw new ApiParsingException("Unexpected error while parsing total from inspections JSON", e);
        }
    }

    private void validateJsonIsNotBlank(String json) {
        if (json == null || json.isBlank()) {
            throw new ApiParsingException("External API response body is empty");
        }
    }

    private InspectionDto toDto(InspectionApiResponse.InspectionRaw raw) {
        var place = raw.getPlace();

        List<String> contractors = Collections.emptyList();

        if (place != null && place.getGroups() != null) {
            contractors = place.getGroups()
                    .stream()
                    .filter(Objects::nonNull)
                    .map(InspectionApiResponse.GroupRaw::getName)
                    .filter(Objects::nonNull)
                    .map(String::trim)
                    .filter(name -> !name.isBlank())
                    .distinct()
                    .toList();
        }

        return new InspectionDto(
                raw.getId(),
                raw.getPublicId(),
                raw.getDate(),
                raw.getStatFails() == null ? 0 : raw.getStatFails(),
                raw.getStatCriticalFails() == null ? 0 : raw.getStatCriticalFails(),
                place == null ? null : place.getName(),
                place == null ? null : place.getAddress(),
                contractors
        );
    }
}
