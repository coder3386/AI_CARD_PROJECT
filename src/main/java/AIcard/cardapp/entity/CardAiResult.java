package AIcard.cardapp.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.LocalDateTime;

@Entity
@Table(name = "card_ai_results")
public class CardAiResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ai_result_id")
    private Long aiResultId;

    @Column(name = "card_id", nullable = false)
    private Long cardId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "card_id", insertable = false, updatable = false, nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private BusinessCard businessCard;

    @Column(name = "model_name", length = 100)
    private String modelName;

    @Lob
    @Column(name = "prompt", columnDefinition = "text")
    private String prompt;

    @Lob
    @Column(name = "result_json", columnDefinition = "longtext")
    private String resultJson;

    @Lob
    @Column(name = "generated_html", columnDefinition = "longtext")
    private String generatedHtml;

    @Lob
    @Column(name = "generated_css", columnDefinition = "longtext")
    private String generatedCss;

    @Column(name = "selected_template_id")
    private Long selectedTemplateId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "selected_template_id", insertable = false, updatable = false)
    private Template selectedTemplate;

    @Lob
    @Column(name = "ai_reason", columnDefinition = "text")
    private String aiReason;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        createdAt = LocalDateTime.now();
    }

    public Long getAiResultId() {
        return aiResultId;
    }

    public Long getCardId() {
        return cardId;
    }

    public void setCardId(Long cardId) {
        this.cardId = cardId;
    }

    public String getModelName() {
        return modelName;
    }

    public void setModelName(String modelName) {
        this.modelName = modelName;
    }

    public String getPrompt() {
        return prompt;
    }

    public void setPrompt(String prompt) {
        this.prompt = prompt;
    }

    public String getResultJson() {
        return resultJson;
    }

    public void setResultJson(String resultJson) {
        this.resultJson = resultJson;
    }

    public String getGeneratedHtml() {
        return generatedHtml;
    }

    public void setGeneratedHtml(String generatedHtml) {
        this.generatedHtml = generatedHtml;
    }

    public String getGeneratedCss() {
        return generatedCss;
    }

    public void setGeneratedCss(String generatedCss) {
        this.generatedCss = generatedCss;
    }

    public Long getSelectedTemplateId() {
        return selectedTemplateId;
    }

    public void setSelectedTemplateId(Long selectedTemplateId) {
        this.selectedTemplateId = selectedTemplateId;
    }

    public String getAiReason() {
        return aiReason;
    }

    public void setAiReason(String aiReason) {
        this.aiReason = aiReason;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
