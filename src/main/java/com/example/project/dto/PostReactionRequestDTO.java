package com.example.project.dto;

import com.example.project.enums.Reactions;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;


@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PostReactionRequestDTO {
    private Reactions reaction;

}
