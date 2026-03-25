package com.example.project.dto.reaction;

import com.example.project.enums.Reactions;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;


@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StoryReactionResponseDTO {
    private String userId;
    private Reactions reaction;
    private LocalDateTime reactedAt;
}
