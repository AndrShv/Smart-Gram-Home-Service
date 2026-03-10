package com.example.project.interfaces;

import com.example.project.event.PostCreatingNotifications;

public interface PostCreatingEventProducer {
    void sendPostCreatingEvent(PostCreatingNotifications event);
}
