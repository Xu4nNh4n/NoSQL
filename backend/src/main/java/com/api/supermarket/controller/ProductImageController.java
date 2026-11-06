package com.api.supermarket.controller;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;
import org.springframework.core.io.*;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import com.api.supermarket.exception.BadRequestException;

@RestController
@RequestMapping("/api/product-images")
public class ProductImageController {
    private final Path directory = Path.of("uploads", "products").toAbsolutePath().normalize();

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','WAREHOUSE')")
    public Map<String, String> upload(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty() || file.getSize() > 5 * 1024 * 1024) {
            throw new BadRequestException("Chọn ảnh JPG hoặc PNG, tối đa 5 MB");
        }
        String extension;
        try (var input = file.getInputStream(); var image = ImageIO.createImageInputStream(input)) {
            if (image == null) throw new BadRequestException("Không đọc được ảnh");
            var readers = ImageIO.getImageReaders(image);
            if (!readers.hasNext()) throw new BadRequestException("File không phải ảnh JPG hoặc PNG hợp lệ");
            var reader = readers.next();
            try {
                reader.setInput(image);
                String format = reader.getFormatName().toLowerCase(Locale.ROOT);
                if (!Set.of("jpeg", "png").contains(format)) throw new BadRequestException("Chỉ hỗ trợ ảnh JPG hoặc PNG");
                if (reader.getWidth(0) > 4096 || reader.getHeight(0) > 4096) {
                    throw new BadRequestException("Chiều rộng và chiều cao ảnh tối đa 4096 pixel");
                }
                extension = format.equals("jpeg") ? ".jpg" : ".png";
            } finally { reader.dispose(); }
        } catch (IOException ex) { throw new BadRequestException("File ảnh không hợp lệ hoặc đã hỏng"); }

        String filename = UUID.randomUUID() + extension;
        Path target = directory.resolve(filename);
        try {
            Files.createDirectories(directory);
            try (var input = file.getInputStream()) { Files.copy(input, target); }
        } catch (IOException ex) {
            try { Files.deleteIfExists(target); } catch (IOException ignored) { }
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Không lưu được ảnh; vui lòng thử lại");
        }
        return Map.of("imageUrl", "/api/product-images/" + filename);
    }

    @GetMapping("/{filename}")
    public ResponseEntity<Resource> image(@PathVariable String filename) {
        if (!filename.matches("[a-f0-9-]{36}\\.(jpg|png)")) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        Path file = directory.resolve(filename);
        if (!Files.isRegularFile(file)) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        MediaType type = filename.endsWith(".png") ? MediaType.IMAGE_PNG : MediaType.IMAGE_JPEG;
        return ResponseEntity.ok().contentType(type).cacheControl(CacheControl.maxAge(7, TimeUnit.DAYS))
                .header("X-Content-Type-Options", "nosniff").body(new FileSystemResource(file));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> error(ResponseStatusException ex) {
        return ResponseEntity.status(ex.getStatusCode()).body(Map.of("message",
                ex.getReason() == null ? "Không tìm thấy ảnh" : ex.getReason()));
    }
}
