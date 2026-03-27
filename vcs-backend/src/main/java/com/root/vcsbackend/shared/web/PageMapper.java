package com.root.vcsbackend.shared.web;

import com.root.vcsbackend.model.PageMeta;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

@Component
public class PageMapper {

    public PageMeta toPageMeta(Page<?> page) {
        return new PageMeta(
            page.getNumber(),
            page.getSize(),
            (int) page.getTotalElements(),
            page.getTotalPages()
        );
    }
}
