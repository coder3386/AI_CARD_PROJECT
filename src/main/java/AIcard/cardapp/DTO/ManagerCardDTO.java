package AIcard.cardapp.DTO;

import java.time.LocalDateTime;

public class ManagerCardDTO {

    private final Long cardId;
    private final Long userId;
    private final String ownerLoginId;
    private final String ownerName;
    private final String title;
    private final String displayName;
    private final String publicUrl;
    private final Boolean publicCard;
    private final String status;
    private final Long viewCount;
    private final LocalDateTime createdAt;

    public ManagerCardDTO(
            Long cardId,
            Long userId,
            String ownerLoginId,
            String ownerName,
            String title,
            String displayName,
            String publicUrl,
            Boolean publicCard,
            String status,
            Long viewCount,
            LocalDateTime createdAt
    ) {
        this.cardId = cardId;
        this.userId = userId;
        this.ownerLoginId = ownerLoginId;
        this.ownerName = ownerName;
        this.title = title;
        this.displayName = displayName;
        this.publicUrl = publicUrl;
        this.publicCard = publicCard;
        this.status = status;
        this.viewCount = viewCount;
        this.createdAt = createdAt;
    }

    public Long getCardId() {
        return cardId;
    }

    public Long getUserId() {
        return userId;
    }

    public String getOwnerLoginId() {
        return ownerLoginId;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public String getDisplayTitle() {
        if (title != null && !title.isBlank()) {
            return title;
        }
        if (displayName != null && !displayName.isBlank()) {
            return displayName;
        }
        return "card-" + cardId;
    }

    public String getPublicUrl() {
        return publicUrl;
    }

    public Boolean getPublicCard() {
        return publicCard;
    }

    public boolean isActive() {
        return "ACTIVE".equals(status);
    }

    public String getStatus() {
        return status;
    }

    public Long getViewCount() {
        return viewCount;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
