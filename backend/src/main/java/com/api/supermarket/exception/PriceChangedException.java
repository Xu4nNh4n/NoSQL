package com.api.supermarket.exception;

import com.api.supermarket.dto.response.PosQuoteResponse;

public class PriceChangedException extends RuntimeException {
    private final PosQuoteResponse quote;
    public PriceChangedException(PosQuoteResponse quote) {
        super("Giá hoặc khuyến mãi đã thay đổi. Vui lòng xác nhận báo giá mới trước khi thanh toán.");
        this.quote = quote;
    }
    public PosQuoteResponse getQuote() { return quote; }
}
