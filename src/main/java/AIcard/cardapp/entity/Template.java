package AIcard.cardapp.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "templates")
public class Template {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "template_id")
    private Long templateId;

    @Column(name = "template_code", length = 50, nullable = false, unique = true)
    private String templateCode;

    @Column(name = "template_name", length = 100, nullable = false)
    private String templateName;

    @Lob
    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Lob
    @Column(name = "tags", columnDefinition = "text")
    private String tags;

    @Lob
    @Column(name = "mood_tags", columnDefinition = "text")
    private String moodTags;

    @Lob
    @Column(name = "color_tags", columnDefinition = "text")
    private String colorTags;

    @Lob
    @Column(name = "recommended_jobs", columnDefinition = "text")
    private String recommendedJobs;

    @Column(name = "html_path", length = 500, nullable = false)
    private String htmlPath;

    @Column(name = "css_path", length = 500, nullable = false)
    private String cssPath;

    @Column(name = "is_active")
    private Boolean active = true;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
        if (active == null) {
            active = true;
        }
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public Long getTemplateId() {
        return templateId;
    }

    public void setTemplateId(Long templateId) {
        this.templateId = templateId;
    }

    public String getTemplateCode() {
        return templateCode;
    }

    public void setTemplateCode(String templateCode) {
        this.templateCode = templateCode;
    }

    public String getTemplateName() {
        return templateName;
    }

    public void setTemplateName(String templateName) {
        this.templateName = templateName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getTags() {
        return tags;
    }

    public void setTags(String tags) {
        this.tags = tags;
    }

    public String getMoodTags() {
        return moodTags;
    }

    public void setMoodTags(String moodTags) {
        this.moodTags = moodTags;
    }

    public String getColorTags() {
        return colorTags;
    }

    public void setColorTags(String colorTags) {
        this.colorTags = colorTags;
    }

    public String getRecommendedJobs() {
        return recommendedJobs;
    }

    public void setRecommendedJobs(String recommendedJobs) {
        this.recommendedJobs = recommendedJobs;
    }

    public String getHtmlPath() {
        return htmlPath;
    }

    public void setHtmlPath(String htmlPath) {
        this.htmlPath = htmlPath;
    }

    public String getCssPath() {
        return cssPath;
    }

    public void setCssPath(String cssPath) {
        this.cssPath = cssPath;
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
