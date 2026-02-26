package com.example.project.dto.profile;


import com.example.project.enums.Genders;
import com.example.project.enums.RelationsStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProfileDTO {

    private String id;

    private String userId;

    @NotBlank(message = "Name cannot be blank")
    private String name;

    @NotBlank(message = "Surname cannot be blank")
    private String surname;


    @NotBlank(message = "bio cannot be blank")
    private String bioDescription;

    @NotNull(message = "Country cannot be null")
    @NotBlank(message = "Country cannot be blank")
    private String location;

    private String birthDate;

    @NotNull(message = "Relation status cannot be null")
    private RelationsStatus relationStatus;

    private String avatarUrl;
    private Genders gender;


    private long postsCount;
    private long followersCount;
    private long followingsCount;
}

