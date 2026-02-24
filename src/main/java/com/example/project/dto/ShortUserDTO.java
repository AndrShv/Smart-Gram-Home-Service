package com.example.project.dto;


import kotlin.text.UStringsKt;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShortUserDTO {
    private String id;
    private String username;
    private String avatarUrl;


}
