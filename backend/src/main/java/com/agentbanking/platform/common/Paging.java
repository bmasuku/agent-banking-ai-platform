package com.agentbanking.platform.common;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

public final class Paging {

    public static final int MAX_SIZE = 100;

    private Paging() {
    }

    public static Pageable of(int page, int size, String sortProperty) {
        return PageRequest.of(Math.max(0, page), Math.max(1, Math.min(size, MAX_SIZE)), Sort.by(Sort.Direction.DESC, sortProperty));
    }

    public static Pageable newestFirst(int page, int size) {
        return of(page, size, "createdAt");
    }
}
