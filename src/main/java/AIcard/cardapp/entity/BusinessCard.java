package AIcard.cardapp.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.LocalDateTime;

@Entity
@Table(name = "business_cards")
public class BusinessCard {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "card_id")
    private Long cardId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", insertable = false, updatable = false, nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private User user;

    @Column(name = "template_id", nullable = false)
    private Long templateId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "template_id", insertable = false, updatable = false, nullable = false)
    private Template template;

    @OneToOne(fetch = FetchType.LAZY, mappedBy = "businessCard")
    private BusinessCardDetail detail;

    @Column(name = "title", length = 100)
    private String title;

    @Column(name = "display_name", length = 50)
    private String displayName;

    @Column(name = "public_url", length = 255, unique = true)
    private String publicUrl;

    @Column(name = "output_path", length = 255)
    private String outputPath;

    @Column(name = "output_html", length = 255)
    private String outputHtml;

    @Column(name = "is_public")
    private Boolean publicCard = true;

    @Column(name = "status", length = 20)
    private String status = "ACTIVE";

    @Column(name = "view_count")
    private Long viewCount = 0L;

    @Column(name = "last_exported_at")
    private LocalDateTime lastExportedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
        if (publicCard == null) {
            publicCard = true;
        }
        if (status == null) {
            status = "ACTIVE";
        }
        if (viewCount == null) {
            viewCount = 0L;
        }
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public Long getCardId() {
        return cardId;
    }

    public void setCardId(Long cardId) {
        this.cardId = cardId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Long getTemplateId() {
        return templateId;
    }

    public void setTemplateId(Long templateId) {
        this.templateId = templateId;
    }

    public BusinessCardDetail getDetail() {
        return detail;
    }

    public void setDetail(BusinessCardDetail detail) {
        this.detail = detail;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getPublicUrl() {
        return publicUrl;
    }

    public void setPublicUrl(String publicUrl) {
        this.publicUrl = publicUrl;
    }

    public String getOutputPath() {
        return outputPath;
    }

    public void setOutputPath(String outputPath) {
        this.outputPath = outputPath;
    }

    public String getOutputHtml() {
        return outputHtml;
    }

    public void setOutputHtml(String outputHtml) {
        this.outputHtml = outputHtml;
    }

    public Boolean getPublicCard() {
        return publicCard;
    }

    public void setPublicCard(Boolean publicCard) {
        this.publicCard = publicCard;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Long getViewCount() {
        return viewCount;
    }

    public void setViewCount(Long viewCount) {
        this.viewCount = viewCount;
    }

    public LocalDateTime getLastExportedAt() {
        return lastExportedAt;
    }

    public void setLastExportedAt(LocalDateTime lastExportedAt) {
        this.lastExportedAt = lastExportedAt;
    }

    public LocalDateTime getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(LocalDateTime deletedAt) {
        this.deletedAt = deletedAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public String getJobTitle() {
        return detail == null ? null : detail.getJobTitle();
    }

    public String getCompany() {
        return detail == null ? null : detail.getCompany();
    }

    public String getDepartment() {
        return detail == null ? null : detail.getDepartment();
    }

    public String getEmail() {
        return detail == null ? null : detail.getEmail();
    }

    public String getPhone() {
        return detail == null ? null : detail.getPhone();
    }

    public String getIntro() {
        return detail == null ? null : detail.getIntro();
    }

    public boolean hasProfileImage() {
        return detail != null && detail.hasProfileImage();
    }

    public String getProfileImageContentType() {
        return detail == null ? null : detail.getProfileImageContentType();
    }
}
