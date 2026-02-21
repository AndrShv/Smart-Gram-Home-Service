package com.example.project.entity;

import com.example.project.enums.PostCategory;
import com.example.project.enums.PostMood;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.GenericGenerator;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "posts")
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class Post {

    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
    @Column(columnDefinition = "BINARY(16)", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(length = 100)
    private String description;

    @Column(name = "photo_url", length = 512, nullable = false)
    private String photoUrl;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @ElementCollection
    @CollectionTable(name = "post_tags", joinColumns = @JoinColumn(name = "post_id"))
    @Column(name = "tag")
    private List<String> tags;


    @Enumerated(EnumType.STRING)
    @Column(name = "category", length = 64)
    private PostCategory category;

    @Column(name = "location", length = 128)
    private String location;


    @Enumerated(EnumType.STRING)
    @Column(name = "mood", length = 64)
    private PostMood mood;

    @Column(name = "is_public", nullable = false)
    @Builder.Default
    private boolean isPublic = true;

    @ToString.Exclude
    @OneToMany(mappedBy = "post", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PostReaction> reactions;

    @ToString.Exclude
    @OneToMany(mappedBy = "post", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Comment> comments;


    @ElementCollection
    @CollectionTable(name = "post_dominant_colors", joinColumns = @JoinColumn(name = "post_id"))
    @Column(name = "color")
    private List<String> dominantColors;


    @JsonIgnore
    private transient byte[] photoBytes;

    @JsonIgnore
    private transient String photoFileName;
}