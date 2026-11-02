package com.intumit.announcement;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.time.LocalDate;

public interface AnnouncementRepository extends JpaRepository<Announcement, Long> {
    long countByPublishDateBetween(LocalDate firstDay, LocalDate lastDay);
    Page<Announcement> findByPublishDateBetween(LocalDate firstDay, LocalDate lastDay, Pageable pageable);
}
