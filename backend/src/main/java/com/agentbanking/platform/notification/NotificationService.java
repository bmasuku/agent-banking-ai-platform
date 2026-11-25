package com.agentbanking.platform.notification;

import com.agentbanking.platform.agent.Agent;
import com.agentbanking.platform.common.AppClock;
import com.agentbanking.platform.common.PageResponse;
import com.agentbanking.platform.common.RiskLevel;
import com.agentbanking.platform.common.exception.BusinessException;
import com.agentbanking.platform.user.Role;
import com.agentbanking.platform.user.User;
import com.agentbanking.platform.user.UserRepository;
import com.agentbanking.platform.user.UserStatus;
import com.agentbanking.platform.websocket.RealtimeEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository repository;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher events;

    @Transactional
    public void notifyUser(User recipient, NotificationType type, RiskLevel severity, String title, String message,
                           String entityType, Object entityId) {
        Notification n = new Notification();
        n.setRecipientId(recipient.getId());
        n.setType(type);
        n.setSeverity(severity);
        n.setTitle(title);
        n.setMessage(message);
        n.setEntityType(entityType);
        n.setEntityId(entityId == null ? null : entityId.toString());
        repository.save(n);
        events.publishEvent(RealtimeEvent.user(recipient.getUsername(), RealtimeEvent.Topics.USER_NOTIFICATIONS,
                "NOTIFICATION", NotificationResponse.from(n)));
    }

    @Transactional
    public void notifyAgent(Agent agent, NotificationType type, RiskLevel severity, String title, String message,
                            String entityType, Object entityId) {
        notifyUser(agent.getUser(), type, severity, title, message, entityType, entityId);
    }

    @Transactional
    public void notifyRole(Role role, NotificationType type, RiskLevel severity, String title, String message,
                           String entityType, Object entityId) {
        for (User user : userRepository.findByRoleAndStatus(role, UserStatus.ACTIVE)) {
            notifyUser(user, type, severity, title, message, entityType, entityId);
        }
    }

    @Transactional
    public void notifyAreaManagers(UUID areaId, NotificationType type, RiskLevel severity, String title, String message,
                                   String entityType, Object entityId) {
        List<User> managers = userRepository.findByRoleAndArea_IdAndStatus(Role.AREA_MANAGER, areaId, UserStatus.ACTIVE);
        for (User manager : managers) {
            notifyUser(manager, type, severity, title, message, entityType, entityId);
        }
    }

    /** True when the recipient already received this type of alert for this entity since the given instant. */
    @Transactional(readOnly = true)
    public boolean recentlyNotified(UUID recipientId, NotificationType type, Object entityId, Instant since) {
        return repository.countByRecipientIdAndTypeAndEntityIdAndCreatedAtAfter(recipientId, type, entityId.toString(), since) > 0;
    }

    @Transactional(readOnly = true)
    public PageResponse<NotificationResponse> list(UUID recipientId, boolean unreadOnly, Pageable pageable) {
        var page = unreadOnly
                ? repository.findByRecipientIdAndReadAtIsNullOrderByCreatedAtDesc(recipientId, pageable)
                : repository.findByRecipientIdOrderByCreatedAtDesc(recipientId, pageable);
        return PageResponse.of(page, NotificationResponse::from);
    }

    @Transactional(readOnly = true)
    public long unreadCount(UUID recipientId) {
        return repository.countByRecipientIdAndReadAtIsNull(recipientId);
    }

    @Transactional
    public NotificationResponse markRead(UUID recipientId, UUID notificationId) {
        Notification n = repository.findById(notificationId)
                .filter(x -> x.getRecipientId().equals(recipientId))
                .orElseThrow(() -> BusinessException.notFound("Notification", notificationId));
        if (n.getReadAt() == null) {
            n.setReadAt(AppClock.now());
        }
        return NotificationResponse.from(n);
    }

    @Transactional
    public int markAllRead(UUID recipientId) {
        return repository.markAllRead(recipientId, AppClock.now());
    }

    public record NotificationResponse(UUID id, NotificationType type, RiskLevel severity, String title, String message,
                                       String entityType, String entityId, boolean read, Instant createdAt) {
        public static NotificationResponse from(Notification n) {
            return new NotificationResponse(n.getId(), n.getType(), n.getSeverity(), n.getTitle(), n.getMessage(),
                    n.getEntityType(), n.getEntityId(), n.getReadAt() != null, n.getCreatedAt());
        }
    }
}
