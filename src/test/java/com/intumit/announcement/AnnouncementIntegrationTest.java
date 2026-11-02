package com.intumit.announcement;

import java.nio.charset.StandardCharsets;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.domain.Page;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Exercises the actual MVC, Thymeleaf, Security, Hibernate and Flyway configuration together. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AnnouncementIntegrationTest {

    @TempDir
    static Path uploadDirectory;

    @DynamicPropertySource
    static void uploadProperties(DynamicPropertyRegistry registry) {
        registry.add("app.upload-dir", () -> uploadDirectory.toString());
    }

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    AnnouncementService service;

    @Autowired
    AnnouncementRepository repository;

    @Autowired
    PlatformTransactionManager transactionManager;

    @BeforeEach
    void clearDatabase() {
        jdbc.update("DELETE FROM attachment");
        jdbc.update("DELETE FROM announcement");
    }

    @Test
    void announcementCanBeCreatedReadUpdatedAndDeleted() throws Exception {
        mvc.perform(validForm(post("/announcements/new")).with(csrf()))
                .andExpect(status().is3xxRedirection());

        long id = onlyAnnouncementId();
        assertEquals("系統維護公告", title(id));
        assertEquals("測試管理者", jdbc.queryForObject(
                "SELECT publisher FROM announcement WHERE id = ?", String.class, id));
        assertEquals(LocalDate.of(2026, 10, 27), jdbc.queryForObject(
                "SELECT publish_date FROM announcement WHERE id = ?", Date.class, id).toLocalDate());

        mvc.perform(get("/announcements/{id}/edit", id))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("系統維護公告")));

        mvc.perform(validForm(post("/announcements/{id}/edit", id))
                        .with(value("title", "修改後公告"))
                        .with(value("content", "更新後的中文內容"))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection());
        assertEquals("修改後公告", title(id));

        mvc.perform(get("/announcements/{id}", id))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("更新後的中文內容")));

        mvc.perform(post("/announcements/{id}/delete", id).param("page", "0").with(csrf()))
                .andExpect(status().is3xxRedirection());
        assertEquals(0, countAnnouncements());
    }

    @Test
    void blankRequiredFieldsAndOversizedTextAreRejectedWithoutSaving() throws Exception {
        mvc.perform(validForm(post("/announcements/new"))
                        .with(value("title", " "))
                        .with(value("publisher", "人".repeat(101)))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasErrors("form"));
        assertEquals(0, countAnnouncements());
    }

    @Test
    void deadlineBeforePublicationIsRejectedAndSameDayIsAccepted() throws Exception {
        mvc.perform(validForm(post("/announcements/new"))
                        .with(value("deadlineDate", "2026-10-26"))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasErrors("form"));
        assertEquals(0, countAnnouncements());

        mvc.perform(validForm(post("/announcements/new"))
                        .with(value("deadlineDate", "2026-10-27"))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection());
        assertEquals(1, countAnnouncements());
    }

    @Test
    void malformedDatesAndYearsOutsideMysqlRangeAreRejectedButBoundaryYearsAreAccepted() throws Exception {
        for (String field : new String[]{"publishDate", "deadlineDate"}) {
            for (String invalidDate : new String[]{
                    "not-a-date", "2026-02-30", "0999-12-31", "10000-01-01", "+10000-01-01"}) {
                mvc.perform(validForm(post("/announcements/new"))
                                .with(value(field, invalidDate)).with(csrf()))
                        .andExpect(status().isOk())
                        .andExpect(model().attributeHasFieldErrors("form", field));
                assertEquals(0, countAnnouncements(), field + "=" + invalidDate + " must not be saved");
            }
        }

        for (String validDate : new String[]{"1000-01-01", "9999-12-31"}) {
            mvc.perform(validForm(post("/announcements/new"))
                            .with(value("publishDate", validDate))
                            .with(value("deadlineDate", validDate))
                            .with(csrf()))
                    .andExpect(status().is3xxRedirection());

            long id = jdbc.queryForObject("SELECT MAX(id) FROM announcement", Long.class);
            assertEquals(validDate, jdbc.queryForObject(
                    "SELECT CAST(publish_date AS VARCHAR) FROM announcement WHERE id = ?", String.class, id));
            assertEquals(validDate, jdbc.queryForObject(
                    "SELECT CAST(deadline_date AS VARCHAR) FROM announcement WHERE id = ?", String.class, id));
            var persisted = repository.findById(id).orElseThrow();
            assertEquals(LocalDate.parse(validDate), persisted.getPublishDate());
            assertEquals(LocalDate.parse(validDate), persisted.getDeadlineDate());
        }

        assertEquals(2, countAnnouncements());
        // JDBC 4.2 avoids java.sql.Date's historic calendar/time-zone conversion.
        assertEquals(LocalDate.of(1000, 1, 1), jdbc.queryForObject(
                "SELECT MIN(publish_date) FROM announcement", (row, number) -> row.getObject(1, LocalDate.class)));
        assertEquals(LocalDate.of(9999, 12, 31), jdbc.queryForObject(
                "SELECT MAX(deadline_date) FROM announcement", (row, number) -> row.getObject(1, LocalDate.class)));
    }

    @Test
    void paginationUsesStableOrderingAndHandlesDeletingTheLastRowOnTheLastPage() throws Exception {
        long oldestId = seedAnnouncement("第 1 筆公告", "內容");
        for (int i = 2; i <= 11; i++) {
            seedAnnouncement("第 " + i + " 筆公告", "內容");
        }

        mvc.perform(get("/announcements").param("page", "0"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("currentPage", 0))
                .andExpect(content().string(containsString("第 11 筆公告")))
                .andExpect(content().string(not(containsString("第 1 筆公告"))));

        mvc.perform(get("/announcements").param("page", "1"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("第 1 筆公告")))
                .andExpect(content().string(not(containsString("第 11 筆公告"))));

        mvc.perform(get("/announcements").param("page", String.valueOf(Integer.MAX_VALUE)))
                .andExpect(status().isOk())
                .andExpect(model().attribute("currentPage", 1));

        mvc.perform(post("/announcements/{id}/delete", oldestId)
                        .param("page", "1").with(csrf()))
                .andExpect(status().is3xxRedirection());

        mvc.perform(get("/announcements").param("page", "1"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("currentPage", 0))
                .andExpect(content().string(containsString("第 11 筆公告")));
    }

    @Test
    void dateSortingUsesTheSameDirectionForPublicationDateAndTiedIds() throws Exception {
        long oldest = seedAnnouncementOnDate("月初公告", "2026-10-01");
        long firstTie = seedAnnouncementOnDate("同日較早建立", "2026-10-10");
        long secondTie = seedAnnouncementOnDate("同日較晚建立", "2026-10-10");
        long newest = seedAnnouncementOnDate("月底公告", "2026-10-31");

        assertEquals(List.of(newest, secondTie, firstTie, oldest),
                announcementPage("newest", "", 0).getContent().stream().map(Announcement::getId).toList());
        assertEquals(List.of(oldest, firstTie, secondTie, newest),
                announcementPage("oldest", "", 0).getContent().stream().map(Announcement::getId).toList());

        var defaultResult = mvc.perform(get("/announcements")).andExpect(status().isOk()).andReturn();
        var html = defaultResult.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertTrue(html.indexOf("月底公告") < html.indexOf("月初公告"));
    }

    @Test
    void publicationMonthIncludesLeapDayAndHandlesYearAndMysqlDateBoundaries() throws Exception {
        seedAnnouncementOnDate("一月最後一天", "2024-01-31");
        long februaryFirst = seedAnnouncementOnDate("二月第一天", "2024-02-01");
        long leapDay = seedAnnouncementOnDate("閏日公告", "2024-02-29");
        seedAnnouncementOnDate("三月第一天", "2024-03-01");
        long decemberLast = seedAnnouncementOnDate("年度最後一天", "2026-12-31");
        seedAnnouncementOnDate("隔年第一天", "2027-01-01");
        long minimumYear = seedAnnouncementOnDate("最小年份公告", "1000-01-01");
        long maximumYear = seedAnnouncementOnDate("最大年份公告", "9999-12-31");

        assertEquals(List.of(februaryFirst, leapDay), announcementPage("oldest", "2024-02", 0)
                .getContent().stream().map(Announcement::getId).toList());
        assertEquals(List.of(decemberLast), announcementPage("newest", "2026-12", 0)
                .getContent().stream().map(Announcement::getId).toList());
        assertEquals(List.of(minimumYear), announcementPage("newest", "1000-01", 0)
                .getContent().stream().map(Announcement::getId).toList());
        assertEquals(List.of(maximumYear), announcementPage("oldest", "9999-12", 0)
                .getContent().stream().map(Announcement::getId).toList());
    }

    @Test
    void filteredPaginationCountsOnlyMatchingRowsAndKeepsFiltersInNavigationLinks() throws Exception {
        long firstId = 0;
        long lastId = 0;
        for (int i = 1; i <= 11; i++) {
            long id = seedAnnouncementOnDate("十月公告 " + i, "2026-10-27");
            if (i == 1) firstId = id;
            lastId = id;
        }
        for (int i = 1; i <= 12; i++) seedAnnouncementOnDate("十一月公告 " + i, "2026-11-01");

        var firstPage = announcementPage("oldest", "2026-10", -1);
        assertEquals(0, firstPage.getNumber());
        assertEquals(11, firstPage.getTotalElements());
        assertEquals(2, firstPage.getTotalPages());
        assertEquals(10, firstPage.getNumberOfElements());
        assertEquals(firstId, firstPage.getContent().getFirst().getId());

        var lastPage = announcementPage("oldest", "2026-10", Integer.MAX_VALUE);
        assertEquals(1, lastPage.getNumber());
        assertEquals(List.of(lastId), lastPage.getContent().stream().map(Announcement::getId).toList());

        String html = mvc.perform(get("/announcements").param("sort", "oldest").param("month", "2026-10"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertListContextLink(html, "/announcements", 1);
        assertListContextLink(html, "/announcements/" + firstId + "/edit", 0);
        assertListContextLink(html, "/announcements/" + firstId, 0);
        assertFalse(html.contains("十一月公告"));
    }

    @Test
    void browsingContextSurvivesDetailsFormsValidationAndCreatingAnAnnouncement() throws Exception {
        for (int i = 1; i <= 10; i++) seedAnnouncementOnDate("前頁公告 " + i, "2026-10-27");
        long id = seedAnnouncementOnDate("往返測試公告", "2026-10-27");
        String detailHtml = mvc.perform(get("/announcements/{id}", id)
                        .param("page", "1").param("sort", "oldest").param("month", "2026-10"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertListContextLink(detailHtml, "/announcements", 1);
        assertListContextLink(detailHtml, "/announcements/" + id + "/edit", 1);

        for (String path : List.of("/announcements/new", "/announcements/" + id + "/edit")) {
            String html = mvc.perform(get(path).param("page", "1").param("sort", "oldest").param("month", "2026-10"))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
            assertListContextLink(html, "/announcements", 1);
            assertHiddenValue(html, "sort", "oldest");
            assertHiddenValue(html, "month", "2026-10");
        }

        String invalidHtml = mvc.perform(validForm(post("/announcements/new"))
                        .with(value("title", " ")).with(value("page", "1"))
                        .param("sort", "oldest").param("month", "2026-10").with(csrf()))
                .andExpect(status().isOk()).andExpect(model().attributeHasErrors("form"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertHiddenValue(invalidHtml, "sort", "oldest");
        assertHiddenValue(invalidHtml, "month", "2026-10");

        var created = mvc.perform(validForm(post("/announcements/new"))
                        .param("sort", "oldest").param("month", "2026-10").with(csrf()))
                .andExpect(status().is3xxRedirection()).andReturn();
        assertListContextRedirect(created.getResponse().getRedirectedUrl(), 0);
        assertEquals(12, countAnnouncements());
    }

    @Test
    void updatingOutsideTheSelectedMonthAndDeletingItsLastPageUseAValidFilteredPage() throws Exception {
        seedAnnouncementOnDate("不屬於十月", "2026-11-01");
        long lastId = 0;
        for (int i = 1; i <= 11; i++) lastId = seedAnnouncementOnDate("十月公告 " + i, "2026-10-27");

        var updated = mvc.perform(validForm(post("/announcements/{id}/edit", lastId))
                        .with(value("page", "1")).with(value("publishDate", "2026-11-01"))
                        .param("sort", "oldest").param("month", "2026-10").with(csrf()))
                .andExpect(status().is3xxRedirection()).andReturn();
        assertListContextRedirect(updated.getResponse().getRedirectedUrl(), 0);
        assertEquals(10, announcementPage("oldest", "2026-10", 1).getTotalElements());
        assertEquals(0, announcementPage("oldest", "2026-10", 1).getNumber());

        long deleteId = seedAnnouncementOnDate("最後一頁待刪除", "2026-10-27");
        assertEquals(List.of(deleteId), announcementPage("oldest", "2026-10", 1)
                .getContent().stream().map(Announcement::getId).toList());
        var deleted = mvc.perform(post("/announcements/{id}/delete", deleteId)
                        .param("page", "1").param("sort", "oldest").param("month", "2026-10").with(csrf()))
                .andExpect(status().is3xxRedirection()).andReturn();
        assertListContextRedirect(deleted.getResponse().getRedirectedUrl(), 0);
        assertEquals(10, announcementPage("oldest", "2026-10", 1).getTotalElements());
        assertEquals(12, countAnnouncements());
    }

    @Test
    void invalidFiltersAreRejectedAndAnEmptyMonthShowsAnEmptyResult() throws Exception {
        mvc.perform(get("/announcements").param("sort", "title"))
                .andExpect(status().isBadRequest());
        for (String month : List.of("2026-13", "2026-02-01", "0999-12", "10000-01", "+10000-01")) {
            mvc.perform(get("/announcements").param("month", month))
                    .andExpect(status().isBadRequest());
        }

        seedAnnouncementOnDate("僅有十月公告", "2026-10-27");
        assertTrue(announcementPage("oldest", "2026-12", Integer.MAX_VALUE).isEmpty());
        assertEquals(0, announcementPage("oldest", "2026-12", Integer.MAX_VALUE).getNumber());
        String html = mvc.perform(get("/announcements").param("sort", "oldest").param("month", "2026-12"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertFalse(html.contains("僅有十月公告"));
        assertTrue(html.contains("沒有符合") || html.contains("這個月份沒有") || html.contains("此月份沒有"));
    }

    @Test
    void unknownAnnouncementAndAttachmentReturnNotFound() throws Exception {
        mvc.perform(get("/announcements/999999999")).andExpect(status().isNotFound());
        mvc.perform(get("/announcements/999999999/edit")).andExpect(status().isNotFound());
        mvc.perform(post("/announcements/999999999/delete").with(csrf()))
                .andExpect(status().isNotFound());
        mvc.perform(get("/attachments/999999999")).andExpect(status().isNotFound());
    }

    @Test
    void writeRequestsWithoutCsrfCannotChangeData() throws Exception {
        mvc.perform(validForm(post("/announcements/new"))).andExpect(status().isForbidden());
        assertEquals(0, countAnnouncements());

        long id = seedAnnouncement("原始公告", "原始內容");
        mvc.perform(post("/announcements/{id}/delete", id)).andExpect(status().isForbidden());
        assertEquals(1, countAnnouncements());
    }

    @Test
    void announcementTextIsHtmlEscaped() throws Exception {
        long id = seedAnnouncement("<b>測試標題</b>", "<script>alert('xss')</script>");

        mvc.perform(get("/announcements"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("&lt;b&gt;測試標題&lt;/b&gt;")))
                .andExpect(content().string(not(containsString("<b>測試標題</b>"))));

        mvc.perform(get("/announcements/{id}", id))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("&lt;script&gt;")))
                .andExpect(content().string(not(containsString("<script>alert"))));
    }

    @Test
    void attachmentCanBeDownloadedReplacedAndRemoved() throws Exception {
        byte[] firstPdf = "%PDF-1.4\nfirst document\n%%EOF".getBytes(StandardCharsets.US_ASCII);
        mvc.perform(validForm(multipart("/announcements/new")
                        .file(new MockMultipartFile("file", "說明.pdf", "application/pdf", firstPdf)))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection());

        long announcementId = onlyAnnouncementId();
        long firstAttachmentId = onlyAttachmentId();
        String firstStorageName = jdbc.queryForObject("SELECT storage_name FROM attachment", String.class);
        mvc.perform(get("/attachments/{id}", firstAttachmentId))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("attachment")))
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(content().bytes(firstPdf));

        byte[] replacement = "%PDF-1.7\nreplacement\n%%EOF".getBytes(StandardCharsets.US_ASCII);
        mvc.perform(validForm(multipart("/announcements/{id}/edit", announcementId)
                        .file(new MockMultipartFile("file", "replacement.pdf", "application/pdf", replacement)))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection());

        long replacementId = onlyAttachmentId();
        String replacementStorageName = jdbc.queryForObject("SELECT storage_name FROM attachment", String.class);
        assertEquals(firstAttachmentId, replacementId);
        assertFalse(Files.exists(uploadDirectory.resolve(firstStorageName)));
        assertTrue(Files.exists(uploadDirectory.resolve(replacementStorageName)));
        mvc.perform(get("/attachments/{id}", replacementId))
                .andExpect(status().isOk())
                .andExpect(content().bytes(replacement));

        mvc.perform(validForm(post("/announcements/{id}/edit", announcementId))
                        .param("removeAttachment", "true").with(csrf()))
                .andExpect(status().is3xxRedirection());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM attachment", Integer.class));
        assertFalse(Files.exists(uploadDirectory.resolve(replacementStorageName)));
        mvc.perform(get("/attachments/{id}", replacementId)).andExpect(status().isNotFound());
    }

    @Test
    void rolledBackCreationRemovesTheNewFileAlongWithDatabaseChanges() throws Exception {
        long filesBefore;
        try (var files = Files.list(uploadDirectory)) {
            filesBefore = files.count();
        }
        new TransactionTemplate(transactionManager).executeWithoutResult(transaction -> {
            service.create(serviceForm(), pdfFile("rollback.pdf"));
            transaction.setRollbackOnly();
        });

        assertEquals(0, countAnnouncements());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM attachment", Integer.class));
        try (var files = Files.list(uploadDirectory)) {
            assertEquals(filesBefore, files.count());
        }
    }

    @Test
    void rolledBackReplacementKeepsOldFileAndRemovesUncommittedNewFile() {
        long announcementId = service.create(serviceForm(), pdfFile("original.pdf")).getId();
        String originalStorageName = jdbc.queryForObject("SELECT storage_name FROM attachment", String.class);

        String uncommittedStorageName = new TransactionTemplate(transactionManager).execute(transaction -> {
            var changed = service.update(announcementId, serviceForm(), pdfFile("replacement.pdf"), false);
            String name = changed.getAttachment().getStorageName();
            transaction.setRollbackOnly();
            return name;
        });

        assertEquals(originalStorageName, jdbc.queryForObject("SELECT storage_name FROM attachment", String.class));
        assertTrue(Files.exists(uploadDirectory.resolve(originalStorageName)));
        assertFalse(Files.exists(uploadDirectory.resolve(uncommittedStorageName)));
    }

    @Test
    void executableDisguisedAsPdfIsRejectedWithoutSavingAnnouncement() throws Exception {
        MockMultipartFile disguisedFile = new MockMultipartFile(
                "file", "fake.pdf", "application/pdf", "MZ executable".getBytes(StandardCharsets.US_ASCII));

        mvc.perform(validForm(multipart("/announcements/new").file(disguisedFile)).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasErrors("form"));
        assertEquals(0, countAnnouncements());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM attachment", Integer.class));
    }

    @Test
    void attachmentOverFiveMebibytesIsRejected() throws Exception {
        byte[] oversized = new byte[5 * 1024 * 1024 + 1];
        System.arraycopy("%PDF-1.4".getBytes(StandardCharsets.US_ASCII), 0, oversized, 0, 8);

        mvc.perform(validForm(multipart("/announcements/new")
                        .file(new MockMultipartFile("file", "large.pdf", "application/pdf", oversized)))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasErrors("form"));
        assertEquals(0, countAnnouncements());
    }

    @Test
    void formLinksAndCsrfFieldsWorkUnderAnExternalTomcatContextPath() throws Exception {
        mvc.perform(get("/board/").contextPath("/board"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", "/board/announcements"));
        mvc.perform(get("/board/announcements/new").contextPath("/board"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("action=\"/board/announcements/new\"")))
                .andExpect(content().string(containsString("name=\"_csrf\"")))
                .andExpect(content().string(containsString("/board/css/app.css")));
    }

    @Test
    void healthEndpointIsPublicAndDoesNotRevealInfrastructureDetails() throws Exception {
        mvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"status\":\"UP\"}"))
                .andExpect(content().string(not(containsString("jdbc:"))))
                .andExpect(content().string(not(containsString("components"))));
    }

    private <B extends AbstractMockHttpServletRequestBuilder<B>> B validForm(B request) {
        return request.param("title", "系統維護公告")
                .param("publisher", "測試管理者")
                .param("publishDate", "2026-10-27")
                .param("deadlineDate", "2026-11-03")
                .param("content", "這是完整的中文公告內容。")
                .param("page", "0");
    }

    private RequestPostProcessor value(String name, String value) {
        return request -> {
            request.setParameter(name, value);
            return request;
        };
    }

    private AnnouncementForm serviceForm() {
        var form = new AnnouncementForm();
        form.setTitle("交易測試公告");
        form.setPublisher("測試管理者");
        form.setPublishDate(LocalDate.of(2026, 10, 27));
        form.setDeadlineDate(LocalDate.of(2026, 11, 3));
        form.setContent("交易回滾時，資料與附件應保持一致。");
        return form;
    }

    private MockMultipartFile pdfFile(String filename) {
        return new MockMultipartFile("file", filename, "application/pdf",
                "%PDF-1.4\ntransaction test\n%%EOF".getBytes(StandardCharsets.US_ASCII));
    }

    private long seedAnnouncement(String title, String content) {
        jdbc.update("""
                INSERT INTO announcement
                    (title, publisher, publish_date, deadline_date, content, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, title, "測試管理者", Date.valueOf("2026-10-27"), Date.valueOf("2026-11-03"), content);
        return jdbc.queryForObject("SELECT MAX(id) FROM announcement", Long.class);
    }

    private long seedAnnouncementOnDate(String title, String dateText) {
        LocalDate date = LocalDate.parse(dateText);
        jdbc.update("""
                INSERT INTO announcement
                    (title, publisher, publish_date, deadline_date, content, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, title, "測試管理者", date, date, "月份與排序測試內容");
        return jdbc.queryForObject("SELECT MAX(id) FROM announcement", Long.class);
    }

    @SuppressWarnings("unchecked")
    private Page<Announcement> announcementPage(String sort, String month, int page) throws Exception {
        var result = mvc.perform(get("/announcements").param("sort", sort).param("month", month)
                        .param("page", String.valueOf(page)))
                .andExpect(status().isOk()).andReturn();
        return (Page<Announcement>) result.getModelAndView().getModel().get("announcements");
    }

    private void assertListContextLink(String html, String path, int page) {
        boolean found = Pattern.compile("href=\"([^\"]+)\"").matcher(html).results()
                .map(match -> match.group(1).replace("&amp;", "&"))
                .filter(url -> path.equals(URI.create(url).getPath()))
                .anyMatch(url -> String.valueOf(page).equals(queryParameter(url, "page"))
                        && "oldest".equals(queryParameter(url, "sort"))
                        && "2026-10".equals(queryParameter(url, "month")));
        assertTrue(found, "Missing link preserving page/sort/month for " + path);
    }

    private void assertListContextRedirect(String location, int page) {
        assertEquals("/announcements", URI.create(location).getPath());
        assertEquals(String.valueOf(page), queryParameter(location, "page"));
        assertEquals("oldest", queryParameter(location, "sort"));
        assertEquals("2026-10", queryParameter(location, "month"));
    }

    private String queryParameter(String url, String name) {
        String query = URI.create(url).getQuery();
        if (query == null) return null;
        for (String entry : query.split("&")) {
            String[] pair = entry.split("=", 2);
            if (pair[0].equals(name)) return pair.length == 2 ? pair[1] : "";
        }
        return null;
    }

    private void assertHiddenValue(String html, String name, String value) {
        assertTrue(Pattern.compile("<input\\b[^>]*>").matcher(html).results()
                .map(match -> match.group())
                .anyMatch(input -> input.contains("type=\"hidden\"")
                        && input.contains("name=\"" + name + "\"")
                        && input.contains("value=\"" + value + "\"")),
                "Missing hidden browsing field: " + name);
    }

    private long onlyAnnouncementId() {
        assertEquals(1, countAnnouncements());
        return jdbc.queryForObject("SELECT id FROM announcement", Long.class);
    }

    private long onlyAttachmentId() {
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM attachment", Integer.class));
        return jdbc.queryForObject("SELECT id FROM attachment", Long.class);
    }

    private int countAnnouncements() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM announcement", Integer.class);
    }

    private String title(long id) {
        return jdbc.queryForObject("SELECT title FROM announcement WHERE id = ?", String.class, id);
    }
}
