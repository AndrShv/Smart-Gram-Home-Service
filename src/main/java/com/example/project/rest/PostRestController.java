package com.example.project.rest;

import com.example.project.dto.post.PostDTO;
import com.example.project.dto.reaction.PostReactionRequestDTO;
import com.example.project.entity.Post;
import com.example.project.exceptions.UnauthorizedException;
import com.example.project.interfaces.PostCrudService;
import com.example.project.interfaces.PostReactionService;
import com.example.project.mappers.PostMapper;
import com.example.project.metrics.PostApiMetricsService;
import com.example.project.service.MinioService;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/posts")
@AllArgsConstructor
public class PostRestController {

    private final PostCrudService postService;
    private final PostReactionService postReactionService;
    private final PostMapper postMapper;
    private final MinioService minioService;
    private final PostApiMetricsService apiMetrics;

    @GetMapping("/feed")
    public ResponseEntity<List<PostDTO>> getFeed() {
        apiMetrics.feedRequest();
        UUID userId = getCurrentUserId();
        List<PostDTO> postDTOs = postService.getAllPosts().stream()
                .filter(p -> !p.getUserId().equals(userId))
                .filter(Post::isPublic)
                .map(postMapper::toDto)
                .collect(Collectors.toList());
        return ResponseEntity.ok(postDTOs);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<PostDTO> createPost(
            @RequestParam(value = "description", required = false) String description,
            @RequestParam(value = "location", required = false) String location,
            @RequestParam(value = "isPublic", defaultValue = "true") boolean isPublic,
            @RequestPart("photo") MultipartFile photo) throws Exception {
        log.info("REST: создание нового поста");
        apiMetrics.createRequest();

        UUID userId = getCurrentUserId();
        byte[] photoBytes;
        try { photoBytes = photo.getBytes(); }
        catch (IOException e) { throw new RuntimeException("Не удалось прочитать файл", e); }

        String photoUrl = minioService.uploadPostPhotoBytes(photoBytes, photo.getOriginalFilename(), photo.getContentType());

        PostDTO postDTO = new PostDTO();
        postDTO.setDescription(description);
        postDTO.setPhotoUrl(photoUrl);
        postDTO.setUserId(userId);
        postDTO.setLocation(location);
        postDTO.setPublic(isPublic);
        postDTO.setPhotoBytes(photoBytes);
        postDTO.setPhotoFileName(photo.getOriginalFilename());

        Post createdPost = postService.createPost(postDTO);
        return ResponseEntity.status(HttpStatus.CREATED).body(postMapper.toDto(createdPost));
    }

    @GetMapping("/{postId}")
    public ResponseEntity<PostDTO> getPost(@PathVariable UUID postId) throws Exception {
        apiMetrics.getRequest();
        return ResponseEntity.ok(postMapper.toDto(postService.getPostById(postId)));
    }

    @PutMapping("/{postId}")
    public ResponseEntity<Void> updatePost(@PathVariable UUID postId, @RequestBody @Valid PostDTO postDTO) {
        apiMetrics.updateRequest();
        postService.updatePost(postId, postDTO);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{postId}")
    public ResponseEntity<Void> deletePost(@PathVariable UUID postId) {
        apiMetrics.deleteRequest();
        postService.deletePost(postId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{postId}/reaction")
    public ResponseEntity<Void> reactToPost(@PathVariable UUID postId, @RequestBody @Valid PostReactionRequestDTO dto) {
        postReactionService.reactToPost(postId, dto.getReaction());
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{postId}/reaction")
    public ResponseEntity<Void> deleteReaction(@PathVariable UUID postId) {
        postReactionService.deleteReaction(postId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{postId}/reactions/count")
    public ResponseEntity<Long> getReactionsCount(@PathVariable UUID postId) {
        return ResponseEntity.ok(postReactionService.countReactions(postId));
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<List<PostDTO>> getUserPosts(@PathVariable UUID userId) {
        apiMetrics.getRequest();
        return ResponseEntity.ok(postService.getPostsByUserId(userId).stream().map(postMapper::toDto).collect(Collectors.toList()));
    }

    @PutMapping("/privacy/all")
    public ResponseEntity<Void> setAllPostsPrivacy(@RequestParam boolean isPublic) {
        postService.setAllPostsPrivacy(getCurrentUserId(), isPublic);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/privacy/status")
    public ResponseEntity<Map<String, Boolean>> getUserPrivacy() {
        UUID userId = getCurrentUserId();
        List<Post> posts = postService.getPostsByUserId(userId);
        boolean isPrivate = !posts.isEmpty() && posts.stream().noneMatch(Post::isPublic);
        return ResponseEntity.ok(Map.of("isPrivate", isPrivate));
    }

    @GetMapping("/search")
    public ResponseEntity<List<PostDTO>> searchPosts(@RequestParam String query) {
        apiMetrics.searchRequest();
        String lower = query.toLowerCase();
        List<PostDTO> result = postService.getAllPosts().stream()
                .filter(p -> p.isPublic() && (
                        (p.getDescription() != null && p.getDescription().toLowerCase().contains(lower)) ||
                                (p.getTags() != null && p.getTags().stream().anyMatch(t -> t.toLowerCase().contains(lower))) ||
                                (p.getLocation() != null && p.getLocation().toLowerCase().contains(lower))
                ))
                .map(postMapper::toDto)
                .collect(Collectors.toList());
        return ResponseEntity.ok(result);
    }

    @GetMapping("/search/tags")
    public ResponseEntity<List<PostDTO>> searchByTag(@RequestParam String tag) {
        apiMetrics.searchRequest();
        return ResponseEntity.ok(postService.searchByTag(tag).stream().map(postMapper::toDto).collect(Collectors.toList()));
    }

    private UUID getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new UnauthorizedException("Пользователь не авторизован");
        }
        try { return UUID.fromString(authentication.getName()); }
        catch (Exception e) { throw new UnauthorizedException("Пользователь не авторизован"); }
    }
}