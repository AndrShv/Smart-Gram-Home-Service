package com.example.project.mappers;

import com.example.project.dto.post.PostDTO;
import com.example.project.entity.Post;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", uses = { CommentMapper.class })
public interface PostMapper {

    @Mapping(target = "reactionsCount",
            expression = "java(post.getReactions() != null ? post.getReactions().size() : 0)")
    @Mapping(target = "commentsCount",
            expression = "java(post.getComments() != null ? post.getComments().size() : 0)")
    @Mapping(target = "viewsCount", ignore = true)
    @Mapping(target = "tags", source = "tags")
    @Mapping(target = "dominantColors", source = "dominantColors")
    @Mapping(target = "category", expression = "java(post.getCategory() != null ? post.getCategory().name() : null)")
    @Mapping(target = "mood", expression = "java(post.getMood() != null ? post.getMood().name() : null)")
    @Mapping(target = "location", source = "location")
    @Mapping(target = "isPublic", expression = "java(post.isPublic())")
    PostDTO toDto(Post post);

}