package com.intumit.announcement;

import java.time.YearMonth;

/** The list's validated URL state; month always refers to the publication date. */
public record AnnouncementListFilter(String sort, String month) {
    public AnnouncementListFilter {
        sort = sort == null || sort.isBlank() ? "newest" : sort.strip();
        month = month == null ? "" : month.strip();
        if (!sort.equals("newest") && !sort.equals("oldest")) {
            throw new IllegalArgumentException("排序方式須為 newest 或 oldest");
        }
        if (!month.isEmpty() && !month.matches("[1-9][0-9]{3}-(0[1-9]|1[0-2])")) {
            throw new IllegalArgumentException("月份須為 yyyy-MM，年份介於 1000 至 9999 年");
        }
    }

    public YearMonth selectedMonth() {
        return month.isEmpty() ? null : YearMonth.parse(month);
    }

    public boolean isDefault() {
        return sort.equals("newest") && month.isEmpty();
    }
}
