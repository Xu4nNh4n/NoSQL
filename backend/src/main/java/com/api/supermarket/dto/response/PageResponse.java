package com.api.supermarket.dto.response;

import java.util.*;

public class PageResponse<T> { // T là kiểu dữ liệu tổng quát, có thể là bất kỳ kiểu dữ liệu nào
    private List<T> content; // Danh sách các phần tử trong trang hiện tại
    private int page; // Số trang hiện tại
    private int size; // Số lượng phần tử trên mỗi trang
    private long totalElements; // Tổng số phần tử trong toàn bộ dữ liệu
    private int totalPages; // Tổng số trang
    private boolean last; // Xác định xem đây có phải là trang cuối cùng hay không

    // Constructor để khởi tạo PageResponse với các thông tin cần thiết
    public PageResponse(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean last
    ){
        this.content = content;
        this.page = page;
        this.size = size;
        this.totalElements = totalElements;
        this.totalPages = totalPages;
        this.last = last;
    }

    // Getter và Setter cho các thuộc tính
    public List<T> getContent() {
        return content;
    }

    public void setContent(List<T> content) {
        this.content = content;
    }

    public int getPage() {
        return page;
    }

    public void setPage(int page) {
        this.page = page;
    }

    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        this.size = size;
    }

    public long getTotalElements() {
        return totalElements;
    }

    public void setTotalElements(long totalElements) {
        this.totalElements = totalElements;
    }

    public int getTotalPages() {
        return totalPages;
    }

    public void setTotalPages(int totalPages) {
        this.totalPages = totalPages;
    }

    public boolean isLast() {
        return last;
    }

    public void setLast(boolean last) {
        this.last = last;
    }
}
