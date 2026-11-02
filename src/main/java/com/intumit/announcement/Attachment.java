package com.intumit.announcement;

import jakarta.persistence.*;

@Entity
@Table(name = "attachment")
public class Attachment {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Version
    private long version;
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "announcement_id", nullable = false, unique = true)
    private Announcement announcement;
    @Column(nullable = false)
    private String originalFilename;
    @Column(nullable = false, unique = true, length = 80)
    private String storageName;
    @Column(nullable = false, length = 100)
    private String contentType;
    @Column(nullable = false)
    private long sizeBytes;

    protected Attachment() { }

    public Attachment(String originalFilename, String storageName, String contentType, long sizeBytes) {
        this.originalFilename = originalFilename;
        this.storageName = storageName;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
    }

    public Long getId() { return id; }
    public String getOriginalFilename() { return originalFilename; }
    public String getStorageName() { return storageName; }
    public String getContentType() { return contentType; }
    public long getSizeBytes() { return sizeBytes; }
    void setAnnouncement(Announcement announcement) { this.announcement = announcement; }
    public void replace(AttachmentStorage.StoredFile file) {
        originalFilename = file.originalFilename();
        storageName = file.storageName();
        contentType = file.contentType();
        sizeBytes = file.sizeBytes();
    }
}
