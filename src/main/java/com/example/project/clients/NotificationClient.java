package com.example.project.clients;

import com.example.project.configs.FeignClientInterceptor;
import com.example.project.dto.NotificationDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;
import java.util.UUID;

@FeignClient(name = "notification-service", url = "${NOTIFICATION_SERVICE_URL}", configuration = FeignClientInterceptor.class)
public interface NotificationClient {

    @GetMapping("/api/notifications/my")
    List<NotificationDTO> myNotifications();


    @GetMapping("/api/notifications/my/unread")
    List<NotificationDTO> unread();

    @GetMapping("/api/notifications/my/unread/count")
    Long unreadCount();

    @GetMapping("/api/notifications/{id}")
    NotificationDTO getById(@PathVariable UUID id);

    @PatchMapping("/api/notifications/{id}/read")
    NotificationDTO markAsRead(@PathVariable UUID id);

    @PatchMapping("/api/notifications/my/read-all")
    void markAllAsRead();

    @DeleteMapping("/api/notifications/{id}")
    void delete(@PathVariable UUID id);




}




