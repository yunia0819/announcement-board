package com.intumit.announcement;

import jakarta.validation.constraints.*;
import org.springframework.format.annotation.DateTimeFormat;
import java.time.LocalDate;

public class AnnouncementForm {
    @NotBlank(message = "請輸入標題") @Size(max = 200, message = "標題最多 200 字")
    private String title;
    @NotBlank(message = "請輸入公布者") @Size(max = 100, message = "公布者最多 100 字")
    private String publisher;
    @NotNull(message = "請輸入發布日期") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate publishDate;
    @NotNull(message = "請輸入截止日期") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate deadlineDate;
    @NotBlank(message = "請輸入公布內容") @Size(max = 10000, message = "公布內容最多 10,000 字")
    private String content;
    @Size(max = 100000, message = "內文格式資料過多，請減少格式後再試")
    private String contentHtml;

    public static AnnouncementForm from(Announcement announcement) {
        var form = new AnnouncementForm();
        form.title = announcement.getTitle();
        form.publisher = announcement.getPublisher();
        form.publishDate = announcement.getPublishDate();
        form.deadlineDate = announcement.getDeadlineDate();
        form.content = announcement.getContent();
        form.contentHtml = announcement.getContentHtml();
        return form;
    }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title == null ? null : title.strip(); }
    public String getPublisher() { return publisher; }
    public void setPublisher(String publisher) { this.publisher = publisher == null ? null : publisher.strip(); }
    public LocalDate getPublishDate() { return publishDate; }
    public void setPublishDate(LocalDate publishDate) { this.publishDate = publishDate; }
    public LocalDate getDeadlineDate() { return deadlineDate; }
    public void setDeadlineDate(LocalDate deadlineDate) { this.deadlineDate = deadlineDate; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content == null ? null : content.strip(); }
    public String getContentHtml() { return contentHtml; }
    public void setContentHtml(String contentHtml) { this.contentHtml = contentHtml; }
}
