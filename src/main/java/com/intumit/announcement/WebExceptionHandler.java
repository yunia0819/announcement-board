package com.intumit.announcement;

import org.springframework.http.HttpStatus;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@ControllerAdvice
public class WebExceptionHandler {
    @ExceptionHandler(OptimisticLockingFailureException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public String conflict(Model model) {
        model.addAttribute("status", 409);
        model.addAttribute("error", "公告已被其他操作更新");
        model.addAttribute("message", "資料已變更，請重新開啟公告後再操作。");
        return "error";
    }

    @ExceptionHandler(NotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String notFound() { return "error/404"; }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    @ResponseStatus(HttpStatus.PAYLOAD_TOO_LARGE)
    public String tooLarge(Model model) {
        model.addAttribute("status", 413);
        model.addAttribute("error", "附件過大");
        model.addAttribute("message", "附件不得超過 5 MB，請返回表單重新選擇附件。");
        return "error";
    }
}
