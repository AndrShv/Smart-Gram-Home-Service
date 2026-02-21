package com.example.project.mappers;

import com.example.project.dto.StoryDTO;
import com.example.project.entity.Story;
import org.mapstruct.*;

import java.time.LocalDateTime;
import java.util.UUID;
@Mapper(componentModel = "spring")
public interface StoryMapper {

    @Mapping(target = "id",        expression = "java(uuidToString(story.getId()))")
    @Mapping(target = "userId",    expression = "java(uuidToString(story.getUserId()))")
    @Mapping(target = "createdAt", expression = "java(dateToString(story.getCreatedAt()))")
    @Mapping(target = "tags",         source = "tags")
    @Mapping(target = "category",     source = "category")
    @Mapping(target = "location",     source = "location")
    @Mapping(target = "mood",         source = "mood")
    @Mapping(target = "overlayText",  source = "overlayText")
    @Mapping(target = "musicCaption", source = "musicCaption")
        // убираем isPublic, MapStruct сам его маппит
    StoryDTO toDto(Story story);

    @Mapping(target = "id",        expression = "java(stringToUuid(dto.getId()))")
    @Mapping(target = "userId",    expression = "java(stringToUuid(dto.getUserId()))")
    @Mapping(target = "createdAt", expression = "java(stringToDate(dto.getCreatedAt()))")
    @Mapping(target = "tags",         source = "tags")
    @Mapping(target = "category",     source = "category")
    @Mapping(target = "location",     source = "location")
    @Mapping(target = "mood",         source = "mood")
    @Mapping(target = "overlayText",  source = "overlayText")
    @Mapping(target = "musicCaption", source = "musicCaption")
    @Mapping(target = "expireAt",     ignore = true)
    @Mapping(target = "viewers",      ignore = true)
    @Mapping(target = "reactions",    ignore = true)
        // isPublic тоже убираем
    Story toEntity(StoryDTO dto);

    // ---------- helpers ----------
    default String uuidToString(UUID uuid) {
        return uuid == null ? null : uuid.toString();
    }

    default UUID stringToUuid(String value) {
        return value == null ? null : UUID.fromString(value);
    }

    default String dateToString(LocalDateTime time) {
        return time == null ? null : time.toString();
    }

    default LocalDateTime stringToDate(String value) {
        return value == null ? null : LocalDateTime.parse(value);
    }
}