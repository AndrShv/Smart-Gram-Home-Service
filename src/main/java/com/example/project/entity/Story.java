package com.example.project.entity;

import com.example.project.enums.StoryCategory;
import com.example.project.enums.StoryMood;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.GenericGenerator;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "stories")
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class Story {

    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
    @Column(columnDefinition = "BINARY(16)", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "photo_url", length = 512)
    private String photoUrl;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "expire_at", nullable = false)
    private LocalDateTime expireAt;

    @ElementCollection
    @CollectionTable(name = "story_tags", joinColumns = @JoinColumn(name = "story_id"))
    @Column(name = "tag")
    private List<String> tags;

    @Column(name = "category", length = 64)
    private StoryCategory category;

    @Column(name = "location", length = 128)
    private String location;

    @Column(name = "mood", length = 64)
    private StoryMood mood;

    @Column(name = "overlay_text", length = 128)
    private String overlayText;

    @Column(name = "music_caption", length = 128)
    private String musicCaption;

    @Column(name = "is_public", nullable = false)
    @Builder.Default
    private boolean isPublic = true;



    @ToString.Exclude
    @JsonIgnore
    @OneToMany(mappedBy = "story", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<StoryViewer> viewers;

    @ToString.Exclude
    @JsonIgnore
    @OneToMany(mappedBy = "story", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<StoryReaction> reactions;


    @JsonIgnore
    private transient byte[] photoBytes;

}