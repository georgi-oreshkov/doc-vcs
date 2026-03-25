package com.root.vcsbackend.shared.web;

import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

@Component
public class PageMapper {

    public <T> Object toPageMeta(Page<T> page) {
        // TODO: implement — convert Page<?> to PageMeta DTO
        return null;
    }
}

