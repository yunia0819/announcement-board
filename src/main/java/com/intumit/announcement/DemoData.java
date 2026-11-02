package com.intumit.announcement;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.stream.IntStream;

@Component
@Profile("demo")
public class DemoData implements CommandLineRunner {
    private final AnnouncementRepository repository;
    public DemoData(AnnouncementRepository repository) { this.repository = repository; }

    @Override @Transactional
    public void run(String... args) {
        if (repository.count() != 0) return;
        var today = LocalDate.now(ZoneId.of("Asia/Taipei"));
        repository.saveAll(IntStream.rangeClosed(1, 23).mapToObj(i -> new Announcement(
                "範例公告 %02d：系統操作說明".formatted(i), "管理員", today.minusDays(i - 1), today.plusDays(14),
                "這是第 " + i + " 筆範例資料。\n可使用修改、刪除及分頁功能，並於表單新增附件。\n此資料僅在啟用 demo profile 且資料庫為空時建立。"
        )).toList());
    }
}
