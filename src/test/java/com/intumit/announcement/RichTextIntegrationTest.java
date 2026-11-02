package com.intumit.announcement;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Verifies formatted content through the actual MVC, database, and Thymeleaf boundaries. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RichTextIntegrationTest {
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

    @BeforeEach
    void clearDatabase() {
        jdbc.update("DELETE FROM attachment");
        jdbc.update("DELETE FROM announcement");
    }

    @Test
    void formattedContentAndAttachmentsSurviveCreationViewingAndEditing() throws Exception {
        String rich = """
                <h2 class="ql-align-center"><span class="ql-size-24 ql-color-firebrick">系統維護</span></h2>
                <p><strong>重要通知</strong>，<em>請先儲存</em>，<u>留意時程</u>。</p>
                <ul><li><span class="ql-bg-lightyellow">備份資料</span></li><li>關閉系統</li></ul>
                """;
        byte[] pdf = "%PDF-1.4\nrich content attachment\n%%EOF".getBytes(StandardCharsets.US_ASCII);
        mvc.perform(validForm(multipart("/announcements/new")
                        .file(new MockMultipartFile("file", "維護說明.pdf", "application/pdf", pdf)))
                        .param("contentHtml", rich).with(csrf()))
                .andExpect(status().is3xxRedirection());

        long id = onlyId();
        String storedHtml = storedHtml(id);
        for (String token : List.of("ql-align-center", "ql-size-24", "ql-color-firebrick", "ql-bg-lightyellow")) {
            assertTrue(storedHtml.contains(token), "Lost supported formatting: " + token);
        }
        assertTrue(storedText(id).contains("系統維護"));
        assertTrue(storedText(id).contains("備份資料"));
        assertFalse(storedText(id).contains("客戶端傳來的純文字"), "Plain text must be derived from sanitized HTML");
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM attachment", Integer.class));

        String displayed = displayedContent(id);
        assertTrue(displayed.contains("<h2"));
        assertTrue(displayed.contains("<strong>重要通知</strong>"));
        assertTrue(displayed.contains("ql-color-firebrick"));
        assertFalse(displayed.contains("&lt;h2"));

        var edit = mvc.perform(get("/announcements/{id}/edit", id))
                .andExpect(status().isOk()).andReturn();
        var form = (AnnouncementForm) edit.getModelAndView().getModel().get("form");
        assertTrue(form.getContentHtml().contains("ql-size-24"));
        assertEquals(storedText(id), form.getContent());

        mvc.perform(validForm(post("/announcements/{id}/edit", id))
                        .param("contentHtml", "<p class=\"ql-align-right\"><s>舊時程</s><span class=\"ql-size-18 ql-color-royalblue\">新時程</span></p>")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection());
        assertTrue(storedHtml(id).contains("ql-align-right"));
        assertTrue(storedHtml(id).contains("ql-color-royalblue"));
        assertFalse(storedHtml(id).contains("系統維護"));
        assertTrue(displayedContent(id).contains("<s>舊時程</s>"));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM attachment", Integer.class));
    }

    @Test
    void unsafeMarkupIsRemovedWhenSavingAndAgainWhenRenderingStoredHtml() throws Exception {
        String dangerous = """
                <p class="ql-align-center unexpected ql-size-999" style="color:red" onclick="DANGEROUS_EVENT()">
                  安全內容<span class="ql-color-firebrick ql-bg-lightyellow alien" onmouseover="DANGEROUS_EVENT()">保留文字</span>
                  <a href="javascript:DANGEROUS_URL()">連結文字</a>
                </p>
                <script>DANGEROUS_SCRIPT()</script><img src="https://example.invalid/tracker" onerror="DANGEROUS_IMAGE()">
                <iframe src="https://example.invalid/frame"></iframe><svg onload="DANGEROUS_SVG()"></svg>
                <form action="https://example.invalid/post"><input name="password"></form>
                """;
        mvc.perform(validForm(post("/announcements/new")).param("contentHtml", dangerous).with(csrf()))
                .andExpect(status().is3xxRedirection());
        long id = onlyId();
        String clean = storedHtml(id);
        assertTrue(clean.contains("安全內容"));
        assertTrue(clean.contains("ql-color-firebrick"));
        assertTrue(clean.contains("ql-align-center"));
        assertSafeContent(clean);
        assertFalse(storedText(id).contains("DANGEROUS_SCRIPT"));

        // A renderer must also protect data inserted outside the normal write path.
        jdbc.update("UPDATE announcement SET content_html = ? WHERE id = ?", dangerous, id);
        String rendered = displayedContent(id);
        assertTrue(rendered.contains("安全內容"));
        assertTrue(rendered.contains("ql-bg-lightyellow"));
        assertSafeContent(rendered);
    }

    @Test
    void visuallyEmptyRichTextIsRejectedEvenWhenClientPlainTextIsNonempty() throws Exception {
        for (String empty : List.of("<p><br></p>", "<p>&nbsp;</p>", "<script>ONLY_SCRIPT()</script>")) {
            mvc.perform(validForm(post("/announcements/new")).param("contentHtml", empty).with(csrf()))
                    .andExpect(status().isOk())
                    .andExpect(model().attributeHasErrors("form"));
            assertEquals(0, announcementCount(), "Visually empty HTML must not create an announcement");
        }
    }

    @Test
    void visibleTextAndRawMarkupLimitsAreEnforcedBeforeSaving() throws Exception {
        mvc.perform(validForm(post("/announcements/new"))
                        .param("contentHtml", "<p>" + "字".repeat(10001) + "</p>").with(csrf()))
                .andExpect(status().isOk()).andExpect(model().attributeHasErrors("form"));
        assertEquals(0, announcementCount());

        String oversizedMarkup = "<p title=\"" + "x".repeat(100001) + "\">短文</p>";
        mvc.perform(validForm(post("/announcements/new")).param("contentHtml", oversizedMarkup).with(csrf()))
                .andExpect(status().isOk()).andExpect(model().attributeHasErrors("form"));
        assertEquals(0, announcementCount());

        mvc.perform(validForm(post("/announcements/new"))
                        .param("contentHtml", "<p>" + "字".repeat(10000) + "</p>").with(csrf()))
                .andExpect(status().is3xxRedirection());
        assertEquals(10000, storedText(onlyId()).length());
    }

    @Test
    void unrelatedValidationErrorsPreserveFormattingWithoutInjectingExecutableHtmlIntoTheForm() throws Exception {
        String rich = "<p class=\"ql-align-center\"><span class=\"ql-size-20 ql-color-indigo\">未儲存的格式</span></p>"
                + "</textarea><script>FORM_XSS()</script>";
        var result = mvc.perform(post("/announcements/new")
                        .param("title", " ").param("publisher", "資訊服務組")
                        .param("publishDate", "2026-10-30").param("deadlineDate", "2026-11-03")
                        .param("content", "未儲存的格式").param("contentHtml", rich).with(csrf()))
                .andExpect(status().isOk()).andExpect(model().attributeHasErrors("form"))
                .andReturn();
        var form = (AnnouncementForm) result.getModelAndView().getModel().get("form");
        assertTrue(form.getContentHtml().contains("ql-size-20"));
        assertTrue(form.getContentHtml().contains("ql-color-indigo"));
        String html = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertTrue(html.contains("&lt;p"), "Editor HTML must be escaped inside the form payload field");
        assertFalse(html.contains("<script>FORM_XSS()"));
        assertEquals(0, announcementCount());
    }

    @Test
    void legacyPlainTextKeepsItsLiteralMarkupAndLineBreaks() throws Exception {
        String text = "舊公告 <p>文字</p>\n<script>LEGACY_XSS()</script>\n第二行 & 符號";
        long id = seedPlainAnnouncement(text);
        assertNull(storedHtml(id));
        String rendered = displayedContent(id);
        assertTrue(rendered.contains("&lt;p&gt;文字&lt;/p&gt;"));
        assertTrue(rendered.contains("&lt;script&gt;LEGACY_XSS()&lt;/script&gt;"));
        assertTrue(rendered.contains("\n"));
        assertFalse(rendered.contains("<script>"));
        assertFalse(rendered.contains("<p>文字</p>"));

        var result = mvc.perform(get("/announcements/{id}/edit", id)).andExpect(status().isOk()).andReturn();
        var form = (AnnouncementForm) result.getModelAndView().getModel().get("form");
        assertEquals(text, form.getContent());
        assertNull(form.getContentHtml());
    }

    @Test
    void anUpdateWithoutRichHtmlCanReturnAnAnnouncementToPlainText() throws Exception {
        mvc.perform(validForm(post("/announcements/new"))
                        .param("contentHtml", "<p><strong>原有粗體</strong></p>").with(csrf()))
                .andExpect(status().is3xxRedirection());
        long id = onlyId();
        String plain = "改回純文字\n<em>保留字面</em>";
        mvc.perform(post("/announcements/{id}/edit", id)
                        .param("title", "純文字公告").param("publisher", "資訊服務組")
                        .param("publishDate", "2026-10-30").param("deadlineDate", "2026-11-03")
                        .param("content", plain).with(csrf()))
                .andExpect(status().is3xxRedirection());
        assertNull(storedHtml(id));
        assertEquals(plain, storedText(id));
        assertTrue(displayedContent(id).contains("&lt;em&gt;保留字面&lt;/em&gt;"));
    }

    private <B extends AbstractMockHttpServletRequestBuilder<B>> B validForm(B request) {
        return request.param("title", "富文字公告").param("publisher", "資訊服務組")
                .param("publishDate", "2026-10-30").param("deadlineDate", "2026-11-03")
                .param("content", "客戶端傳來的純文字").param("page", "0");
    }

    private String displayedContent(long id) throws Exception {
        String html = mvc.perform(get("/announcements/{id}", id)).andExpect(status().isOk()).andReturn()
                .getResponse().getContentAsString(StandardCharsets.UTF_8);
        var matcher = Pattern.compile("(?s)<div\\b[^>]*class=\"[^\"]*\\bannouncement-content\\b[^\"]*\"[^>]*>(.*?)</div>").matcher(html);
        assertTrue(matcher.find(), "Missing announcement content region");
        return matcher.group(1);
    }

    private void assertSafeContent(String html) {
        for (String forbidden : List.of("<script", "<img", "<iframe", "<svg", "<form", "<input", "<a ",
                "onclick", "onmouseover", "onerror", "onload", "style=", "href=", "src=", "javascript:",
                "unexpected", "alien", "ql-size-999", "DANGEROUS_")) {
            assertFalse(html.toLowerCase().contains(forbidden.toLowerCase()), "Unsafe content survived: " + forbidden);
        }
    }

    private int announcementCount() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM announcement", Integer.class);
    }

    private long onlyId() {
        assertEquals(1, announcementCount());
        return jdbc.queryForObject("SELECT id FROM announcement", Long.class);
    }

    private String storedText(long id) {
        return jdbc.queryForObject("SELECT content FROM announcement WHERE id = ?", String.class, id);
    }

    private String storedHtml(long id) {
        return jdbc.queryForObject("SELECT content_html FROM announcement WHERE id = ?", String.class, id);
    }

    private long seedPlainAnnouncement(String content) {
        jdbc.update("""
                INSERT INTO announcement
                    (title, publisher, publish_date, deadline_date, content, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, "舊公告", "資訊服務組", LocalDate.of(2026, 10, 30), LocalDate.of(2026, 11, 3), content);
        return jdbc.queryForObject("SELECT MAX(id) FROM announcement", Long.class);
    }
}
