package com.intumit.announcement;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

@Entity
@Table(name = "announcement")
public class Announcement {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Version
    private long version;
    @Column(nullable = false, length = 200)
    private String title;
    @Column(nullable = false, length = 100)
    private String publisher;
    @Column(nullable = false)
    private LocalDate publishDate;
    @Column(nullable = false)
    private LocalDate deadlineDate;
    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;
    @Column(columnDefinition = "MEDIUMTEXT")
    private String contentHtml;
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
    @Column(nullable = false)
    private LocalDateTime updatedAt;
    @OneToOne(mappedBy = "announcement", cascade = CascadeType.ALL, orphanRemoval = true)
    private Attachment attachment;

    protected Announcement() { }

    public Announcement(String title, String publisher, LocalDate publishDate, LocalDate deadlineDate, String content) {
        revise(title, publisher, publishDate, deadlineDate, content);
    }

    public void revise(String title, String publisher, LocalDate publishDate, LocalDate deadlineDate, String content) {
        this.title = title;
        this.publisher = publisher;
        this.publishDate = publishDate;
        this.deadlineDate = deadlineDate;
        this.content = content;
    }

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now(ZoneId.of("Asia/Taipei"));
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() { updatedAt = LocalDateTime.now(ZoneId.of("Asia/Taipei")); }

    public Long getId() { return id; }
    public String getTitle() { return title; }
    public String getPublisher() { return publisher; }
    public LocalDate getPublishDate() { return publishDate; }
    public LocalDate getDeadlineDate() { return deadlineDate; }
    public String getContent() { return content; }
    public String getContentHtml() { return contentHtml; }
    public void setContentHtml(String contentHtml) { this.contentHtml = contentHtml; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public Attachment getAttachment() { return attachment; }
    public void setAttachment(Attachment attachment) {
        this.attachment = attachment;
        if (attachment != null) attachment.setAnnouncement(this);
    }
}
