package com.api.supermarket.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.api.supermarket.domain.PromotionSnapshot;
import com.api.supermarket.dto.request.PromotionRequest;
import com.api.supermarket.dto.response.*;
import com.api.supermarket.entity.*;
import com.api.supermarket.exception.*;
import com.api.supermarket.repository.*;

@Service
@Transactional(readOnly = true)
public class PromotionService {
    public static final ZoneId VIETNAM = ZoneId.of("Asia/Ho_Chi_Minh");
    private final PromotionRepository promotions;
    private final ProductRepository products;
    public PromotionService(PromotionRepository promotions, ProductRepository products) {
        this.promotions = promotions;
        this.products = products;
    }
    public List<PromotionResponse> list() {
        return promotions.findAllWithProduct().stream().map(this::response).toList();
    }
    @Transactional
    public PromotionResponse save(Long id, PromotionRequest request) {
        if (!request.endsAt().isAfter(request.startsAt())) {
            throw new BadRequestException("Ngày kết thúc phải sau ngày bắt đầu (giờ Việt Nam)");
        }
        Promotion promotion = id == null ? new Promotion() : load(id);
        Product product = products.findById(request.productId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy sản phẩm"));
        promotion.setProduct(product);
        promotion.setName(request.name().trim());
        promotion.setPercent(request.percent().setScale(2, RoundingMode.UNNECESSARY));
        promotion.setStartsAt(request.startsAt().withNano(0));
        promotion.setEndsAt(request.endsAt().withNano(0));
        if (!promotion.getEndsAt().isAfter(promotion.getStartsAt())) {
            throw new BadRequestException("Khoảng thời gian tối thiểu một giây");
        }
        promotion.setActive(request.active());
        return response(promotions.save(promotion));
    }
    @Transactional
    public PromotionResponse setActive(Long id, boolean active) {
        Promotion promotion = load(id);
        promotion.setActive(active);
        return response(promotions.save(promotion));
    }
    private Promotion load(Long id) {
        return promotions.findById(id).orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy khuyến mãi"));
    }
    private PromotionResponse response(Promotion p) {
        return new PromotionResponse(p.getPromotionId(), p.getProduct().getProductId(),
                p.getProduct().getProductName(), p.getName(), p.getPercent(), p.getStartsAt(), p.getEndsAt(), p.isActive());
    }

    public PosQuoteResponse quote(Map<Long, Integer> quantities, Map<Long, Product> catalog, LocalDateTime at) {
        Map<Long, Promotion> best = new HashMap<>();
        promotions.findEffective(quantities.keySet(), at).forEach(p -> best.putIfAbsent(p.getProduct().getProductId(), p));
        List<PosQuoteResponse.Item> lines = new ArrayList<>();
        BigDecimal gross = new BigDecimal("0.00");
        BigDecimal total = new BigDecimal("0.00");
        StringBuilder fingerprint = new StringBuilder();
        for (Map.Entry<Long, Integer> entry : new TreeMap<>(quantities).entrySet()) {
            Product product = catalog.get(entry.getKey());
            if (!Boolean.TRUE.equals(product.getIsActive())) throw new BadRequestException("Sản phẩm đã ngừng kinh doanh: " + product.getProductName());
            BigDecimal original = product.getPrice().setScale(2, RoundingMode.HALF_UP);
            if (original.signum() < 0) throw new BadRequestException("Giá sản phẩm không hợp lệ");
            Promotion p = best.get(entry.getKey());
            BigDecimal net = p == null ? original : original.multiply(new BigDecimal("100").subtract(p.getPercent()))
                    .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
            BigDecimal count = BigDecimal.valueOf(entry.getValue());
            BigDecimal lineGross = original.multiply(count);
            BigDecimal lineTotal = net.multiply(count);
            PromotionSnapshot snapshot = p == null ? null : new PromotionSnapshot(p.getPromotionId(), p.getName(),
                    p.getPercent(), p.getStartsAt(), p.getEndsAt());
            lines.add(new PosQuoteResponse.Item(entry.getKey(), entry.getValue(), original, net,
                    lineGross, lineGross.subtract(lineTotal), lineTotal, snapshot));
            gross = gross.add(lineGross);
            total = total.add(lineTotal);
            if (gross.compareTo(new BigDecimal("9999999999999999.99")) > 0) {
                throw new BadRequestException("Tổng tiền vượt giới hạn lưu trữ hóa đơn DECIMAL(18,2)");
            }
            // Length-prefix the name so arbitrary user text cannot make ambiguous fingerprints.
            fingerprint.append(entry.getKey()).append(':').append(entry.getValue()).append(':')
                    .append(original).append(':').append(net).append(':');
            if (p != null) fingerprint.append(p.getPromotionId()).append(':').append(p.getPercent()).append(':')
                    .append(p.getStartsAt()).append(':').append(p.getEndsAt()).append(':')
                    .append(p.getName().length()).append(':').append(p.getName());
            fingerprint.append(';');
        }
        try {
            String token = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(fingerprint.toString().getBytes(StandardCharsets.UTF_8)));
            return new PosQuoteResponse(token, at, VIETNAM.getId(), gross, gross.subtract(total), total, lines);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
