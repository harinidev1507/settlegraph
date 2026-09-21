package com.settlegraph.Artifacts.service;

import com.settlegraph.Artifacts.entity.Notification;
import com.settlegraph.Artifacts.exception.ForbiddenException;
import com.settlegraph.Artifacts.exception.NotFoundException;
import com.settlegraph.Artifacts.repository.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotificationServiceTest {

    private static final Long OWNER = 14L;
    private static final Long SOMEONE_ELSE = 16L;

    private NotificationRepository notificationRepository;
    private NotificationService notificationService;
    private Notification notification;

    @BeforeEach
    void setUp() {
        notificationRepository = mock(NotificationRepository.class);
        notificationService = new NotificationService(notificationRepository);
        notification = new Notification(OWNER, "hello");
        when(notificationRepository.findById(5L)).thenReturn(Optional.of(notification));
    }

    @Test
    void markRead_byOwner_marksItRead() {
        notificationService.markRead(5L, OWNER);

        assertTrue(notification.isRead());
        verify(notificationRepository).save(notification);
    }

    @Test
    void markRead_bySomeoneElse_isRejected_andTheRowIsUntouched() {
        assertThrows(ForbiddenException.class, () -> notificationService.markRead(5L, SOMEONE_ELSE));

        assertFalse(notification.isRead());
        verify(notificationRepository, never()).save(any());
    }

    @Test
    void markRead_ofMissingNotification_is404() {
        when(notificationRepository.findById(6L)).thenReturn(Optional.empty());
        assertThrows(NotFoundException.class, () -> notificationService.markRead(6L, OWNER));
        verify(notificationRepository, never()).save(any());
    }
}
