package com.example.project.clients;


import com.example.project.configs.FeignConfig;
import com.example.project.dto.profile.SubscriberDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;
import java.util.UUID;

@FeignClient(
        name = "profile-service",
        url = "${SUBSCRIPTION_SERVICE_URL}",
        configuration = FeignConfig.class
)
public interface SubscriptionClient {

    @GetMapping("/api/subscriptions/{userId}/followers/count")
    long getFollowersCount(@PathVariable("userId") UUID userId);

    @GetMapping("/api/subscriptions/{userId}/followings/count")
    long getFollowingsCount(@PathVariable("userId") UUID userId);

    @GetMapping("/api/subscriptions/{userId}/followers")
    List<SubscriberDTO> getFollowers(@PathVariable UUID userId);


}
