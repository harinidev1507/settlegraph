package com.settlegraph.Artifacts.service;

import com.settlegraph.Artifacts.entity.Notification;
import com.settlegraph.Artifacts.repository.NotificationRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;

    public NotificationService(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    public Notification create(Long userId, String message) {
        return notificationRepository.save(new Notification(userId, message));
    }

    public List<Notification> getForUser(Long userId) {
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    public void markRead(Long notificationId) {
        notificationRepository.findById(notificationId).ifPresent(n -> {
            n.markRead();
            notificationRepository.save(n);
        });
    }
}
