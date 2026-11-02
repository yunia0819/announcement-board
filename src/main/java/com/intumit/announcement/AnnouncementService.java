package com.intumit.announcement;

import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import java.time.LocalDate;

@Service
@Transactional(readOnly = true)
public class AnnouncementService {
    private static final int PAGE_SIZE = 10;
    private final AnnouncementRepository repository;
    private final AttachmentStorage storage;
    private final RichTextService richText;

    public AnnouncementService(AnnouncementRepository repository, AttachmentStorage storage, RichTextService richText) {
        this.repository = repository;
        this.storage = storage;
        this.richText = richText;
    }

    public Page<Announcement> list(int requestedPage) {
        return list(requestedPage, new AnnouncementListFilter("newest", ""));
    }

    public Page<Announcement> list(int requestedPage, AnnouncementListFilter filter) {
        var month = filter.selectedMonth();
        LocalDate firstDay = month == null ? null : month.atDay(1);
        LocalDate lastDay = month == null ? null : month.atEndOfMonth();
        Sort.Direction direction = filter.sort().equals("oldest") ? Sort.Direction.ASC : Sort.Direction.DESC;
        Sort sort = Sort.by(direction, "publishDate", "id");
        long count = month == null ? repository.count() : repository.countByPublishDateBetween(firstDay, lastDay);
        int lastPage = (int) Math.min(Integer.MAX_VALUE, Math.max(0, (count - 1) / PAGE_SIZE));
        int page = Math.min(Math.max(0, requestedPage), lastPage);
        Page<Announcement> results = findPage(page, sort, firstDay, lastDay);
        if (page > 0 && page >= results.getTotalPages()) {
            results = findPage(Math.max(0, results.getTotalPages() - 1), sort, firstDay, lastDay);
        }
        return results;
    }

    private Page<Announcement> findPage(int page, Sort sort, LocalDate firstDay, LocalDate lastDay) {
        var request = PageRequest.of(page, PAGE_SIZE, sort);
        return firstDay == null ? repository.findAll(request)
                : repository.findByPublishDateBetween(firstDay, lastDay, request);
    }

    public Announcement find(long id) { return repository.findById(id).orElseThrow(NotFoundException::new); }

    @Transactional
    public Announcement create(AnnouncementForm form, MultipartFile file) {
        validateDates(form);
        var body = richText.normalize(form.getContent(), form.getContentHtml());
        var announcement = new Announcement(form.getTitle(), form.getPublisher(), form.getPublishDate(), form.getDeadlineDate(), body.text());
        announcement.setContentHtml(body.html());
        applyAttachment(announcement, file, false);
        return repository.saveAndFlush(announcement);
    }

    @Transactional
    public Announcement update(long id, AnnouncementForm form, MultipartFile file, boolean removeAttachment) {
        validateDates(form);
        var body = richText.normalize(form.getContent(), form.getContentHtml());
        var announcement = find(id);
        applyAttachment(announcement, file, removeAttachment);
        announcement.revise(form.getTitle(), form.getPublisher(), form.getPublishDate(), form.getDeadlineDate(), body.text());
        announcement.setContentHtml(body.html());
        return repository.saveAndFlush(announcement);
    }

    private void validateDates(AnnouncementForm form) {
        if (form.getPublishDate() == null || form.getDeadlineDate() == null || form.getDeadlineDate().isBefore(form.getPublishDate())) {
            throw new IllegalArgumentException("截止日期不得早於發布日期");
        }
        if (form.getPublishDate().getYear() < 1000 || form.getPublishDate().getYear() > 9999
                || form.getDeadlineDate().getYear() < 1000 || form.getDeadlineDate().getYear() > 9999) {
            throw new IllegalArgumentException("日期年份須介於 1000 至 9999 年");
        }
    }

    private void applyAttachment(Announcement announcement, MultipartFile file, boolean remove) {
        var stored = storage.store(file);
        var existing = announcement.getAttachment();
        String oldName = existing == null ? null : existing.getStorageName();
        if (stored != null) {
            // Remove the newly written file if the database transaction fails.
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCompletion(int status) {
                    if (status != STATUS_COMMITTED) storage.deleteQuietly(stored.storageName());
                }
            });
            if (existing == null) announcement.setAttachment(stored.toEntity());
            else existing.replace(stored);
        } else if (remove && existing != null) {
            announcement.setAttachment(null);
        }
        if (oldName != null && (stored != null || remove)) deleteAfterCommit(oldName);
    }

    @Transactional
    public void delete(long id) {
        var announcement = find(id);
        if (announcement.getAttachment() != null) deleteAfterCommit(announcement.getAttachment().getStorageName());
        repository.delete(announcement);
        repository.flush();
    }

    private void deleteAfterCommit(String name) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() { storage.deleteQuietly(name); }
        });
    }
}
