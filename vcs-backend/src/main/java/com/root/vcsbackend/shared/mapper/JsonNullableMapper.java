package com.root.vcsbackend.shared.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Named;
import org.openapitools.jackson.nullable.JsonNullable;

import java.net.URI;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * Reusable MapStruct conversion helpers.
 * Used via: @Mapper(componentModel = "spring", uses = JsonNullableMapper.class)
 */
@Mapper(componentModel = "spring", config = MapStructConfig.class)
public interface JsonNullableMapper {

    /** Wraps any nullable UUID as a present JsonNullable (including null → of(null)). */
    @Named("uuidToJsonNullable")
    default JsonNullable<UUID> uuidToJsonNullable(UUID value) {
        return JsonNullable.of(value);
    }

    /** Extracts the value from JsonNullable<UUID>, returning null if undefined or null. */
    @Named("jsonNullableToUuid")
    default UUID jsonNullableToUuid(JsonNullable<UUID> nullable) {
        return (nullable != null && nullable.isPresent()) ? nullable.get() : null;
    }

    /** Converts a stored URI string to JsonNullable<URI>, undefined when null. */
    @Named("stringToJsonNullableUri")
    default JsonNullable<URI> stringToJsonNullableUri(String url) {
        return url != null ? JsonNullable.of(URI.create(url)) : JsonNullable.undefined();
    }

    /** Extracts a URI from JsonNullable<URI> and converts it to a String. */
    @Named("jsonNullableUriToString")
    default String jsonNullableUriToString(JsonNullable<URI> nullable) {
        if (nullable == null || !nullable.isPresent() || nullable.get() == null) return null;
        return nullable.get().toString();
    }

    /** Converts a DB Instant (UTC) to OffsetDateTime for the API response. */
    @Named("instantToOffsetDateTime")
    default OffsetDateTime instantToOffsetDateTime(Instant instant) {
        return instant != null ? instant.atOffset(ZoneOffset.UTC) : null;
    }
}

