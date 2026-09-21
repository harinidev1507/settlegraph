package com.settlegraph.Artifacts.service;

import com.settlegraph.Artifacts.entity.Notification;
import com.settlegraph.Artifacts.exception.ForbiddenException;
import com.settlegraph.Artifacts.exception.NotFoundException;
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

    /** Only the notification's own recipient may mark it read. */
    public void markRead(Long notificationId, Long requestingUserId) {
        Notification n = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new NotFoundException("Notification not found"));
        if (!n.getUserId().equals(requestingUserId)) {
            throw new ForbiddenException("This notification isn't yours");
        }
        n.markRead();
        notificationRepository.save(n);
    }
}
