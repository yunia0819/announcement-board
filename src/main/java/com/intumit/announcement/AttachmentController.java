package com.intumit.announcement;

import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import java.nio.charset.StandardCharsets;

@Controller
public class AttachmentController {
    private final AttachmentRepository repository;
    private final AttachmentStorage storage;
    public AttachmentController(AttachmentRepository repository, AttachmentStorage storage) {
        this.repository = repository;
        this.storage = storage;
    }

    @GetMapping("/attachments/{id}")
    public ResponseEntity<Resource> download(@PathVariable long id) {
        var attachment = repository.findById(id).orElseThrow(NotFoundException::new);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(attachment.getContentType()))
                .contentLength(attachment.getSizeBytes())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(attachment.getOriginalFilename(), StandardCharsets.UTF_8).build().toString())
                .cacheControl(CacheControl.noStore())
                .body(storage.load(attachment.getStorageName()));
    }
}
