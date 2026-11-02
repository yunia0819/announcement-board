package com.intumit.announcement;

import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.stream.IntStream;

@Controller
public class AnnouncementController {
    private final AnnouncementService service;
    private final RichTextService richText;
    public AnnouncementController(AnnouncementService service, RichTextService richText) {
        this.service = service;
        this.richText = richText;
    }

    @ModelAttribute
    public void listFilter(@RequestParam(defaultValue = "newest") String sort,
                           @RequestParam(defaultValue = "") String month, Model model) {
        try {
            var filter = new AnnouncementListFilter(sort, month);
            model.addAttribute("listFilter", filter);
            model.addAttribute("sort", filter.sort());
            model.addAttribute("month", filter.month());
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
        }
    }

    @GetMapping("/")
    public String home(RedirectAttributes redirect) { return "redirect:/announcements"; }

    @GetMapping("/announcements")
    public String list(@RequestParam(defaultValue = "0") int page,
                       @ModelAttribute(value = "listFilter", binding = false) AnnouncementListFilter filter, Model model) {
        var results = service.list(page, filter);
        int current = results.getNumber();
        model.addAttribute("announcements", results);
        model.addAttribute("currentPage", current);
        model.addAttribute("pageNumbers", IntStream.range(Math.max(0, current - 2), Math.min(results.getTotalPages(), current + 3)).boxed().toList());
        return "announcements/list";
    }

    @GetMapping("/announcements/new")
    public String newForm(@RequestParam(defaultValue = "0") int page, Model model) {
        var form = new AnnouncementForm();
        form.setPublishDate(LocalDate.now(ZoneId.of("Asia/Taipei")));
        form.setDeadlineDate(form.getPublishDate());
        model.addAttribute("form", form);
        return prepareForm(null, page, model);
    }

    @GetMapping("/announcements/{id}/edit")
    public String editForm(@PathVariable long id, @RequestParam(defaultValue = "0") int page, Model model) {
        var form = AnnouncementForm.from(service.find(id));
        if (form.getContentHtml() != null) form.setContentHtml(richText.safeHtml(form.getContentHtml()));
        model.addAttribute("form", form);
        return prepareForm(id, page, model);
    }

    @GetMapping("/announcements/{id}")
    public String detail(@PathVariable long id, @RequestParam(defaultValue = "0") int page, Model model) {
        var announcement = service.find(id);
        model.addAttribute("announcement", announcement);
        model.addAttribute("safeContentHtml", richText.safeHtml(announcement.getContentHtml()));
        model.addAttribute("currentPage", Math.max(0, page));
        return "announcements/detail";
    }

    @PostMapping("/announcements/new")
    public String create(@Valid @ModelAttribute("form") AnnouncementForm form, BindingResult errors,
                         @RequestParam(required = false) MultipartFile file,
                         @RequestParam(defaultValue = "0") int page,
                         @ModelAttribute(value = "listFilter", binding = false) AnnouncementListFilter filter,
                         Model model, RedirectAttributes redirect) {
        checkDates(form, errors);
        checkContent(form, errors);
        if (errors.hasErrors()) return prepareForm(null, page, model);
        try { service.create(form, file); }
        catch (InvalidAttachmentException ex) {
            errors.reject("attachment.invalid", ex.getMessage());
            return prepareForm(null, page, model);
        }
        redirect.addFlashAttribute("message", "公告已新增");
        return listRedirect(0, filter, redirect);
    }

    @PostMapping("/announcements/{id}/edit")
    public String update(@PathVariable long id, @Valid @ModelAttribute("form") AnnouncementForm form, BindingResult errors,
                         @RequestParam(required = false) MultipartFile file,
                         @RequestParam(defaultValue = "false") boolean removeAttachment,
                         @RequestParam(defaultValue = "0") int page,
                         @ModelAttribute(value = "listFilter", binding = false) AnnouncementListFilter filter,
                         Model model, RedirectAttributes redirect) {
        service.find(id);
        checkDates(form, errors);
        checkContent(form, errors);
        model.addAttribute("removeAttachment", removeAttachment);
        if (errors.hasErrors()) return prepareForm(id, page, model);
        try { service.update(id, form, file, removeAttachment); }
        catch (InvalidAttachmentException ex) {
            errors.reject("attachment.invalid", ex.getMessage());
            return prepareForm(id, page, model);
        }
        redirect.addFlashAttribute("message", "公告已更新");
        return listRedirect(service.list(page, filter).getNumber(), filter, redirect);
    }

    @PostMapping("/announcements/{id}/delete")
    public String delete(@PathVariable long id, @RequestParam(defaultValue = "0") int page,
                         @ModelAttribute(value = "listFilter", binding = false) AnnouncementListFilter filter,
                         RedirectAttributes redirect) {
        service.delete(id);
        int validPage = service.list(page, filter).getNumber();
        redirect.addFlashAttribute("message", "公告已刪除");
        return listRedirect(validPage, filter, redirect);
    }

    private String listRedirect(int page, AnnouncementListFilter filter, RedirectAttributes redirect) {
        redirect.addAttribute("page", Math.max(0, page));
        if (!filter.isDefault()) {
            redirect.addAttribute("sort", filter.sort());
            if (!filter.month().isEmpty()) redirect.addAttribute("month", filter.month());
        }
        return "redirect:/announcements";
    }

    private void checkContent(AnnouncementForm form, BindingResult errors) {
        try {
            var body = richText.normalize(form.getContent(), form.getContentHtml());
            form.setContent(body.text());
            form.setContentHtml(body.html());
        } catch (IllegalArgumentException ex) {
            if (!errors.hasFieldErrors("content")) errors.rejectValue("content", "content.invalid", ex.getMessage());
        }
    }

    private void checkDates(AnnouncementForm form, BindingResult errors) {
        if (form.getPublishDate() != null && (form.getPublishDate().getYear() < 1000 || form.getPublishDate().getYear() > 9999)) {
            errors.rejectValue("publishDate", "date.range", "日期年份須介於 1000 至 9999 年");
        }
        if (form.getDeadlineDate() != null && (form.getDeadlineDate().getYear() < 1000 || form.getDeadlineDate().getYear() > 9999)) {
            errors.rejectValue("deadlineDate", "date.range", "日期年份須介於 1000 至 9999 年");
        }
        if (form.getPublishDate() != null && form.getDeadlineDate() != null && form.getDeadlineDate().isBefore(form.getPublishDate())) {
            errors.rejectValue("deadlineDate", "date.order", "截止日期不得早於發布日期");
        }
    }

    private String prepareForm(Long id, int page, Model model) {
        model.addAttribute("editing", id != null);
        model.addAttribute("announcementId", id);
        model.addAttribute("currentPage", Math.max(0, page));
        model.addAttribute("existingAttachment", id == null ? null : service.find(id).getAttachment());
        return "announcements/form";
    }
}
