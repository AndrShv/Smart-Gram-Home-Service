package com.example.project.clients;


import com.example.project.configs.FeignConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

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

}
