# Kế Hoạch Xử Lý File Cấm Kỵ & Dọn Dẹp Nhạy Cảm Trong Git

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Loại bỏ hoàn toàn các file cấm kỵ (logs, file nhị phân parquet, file tạm tmp, checksum crc), làm sạch token và mật khẩu hardcode, cập nhật `.gitignore` và dọn dẹp lịch sử Git trên kho lưu trữ.

**Architecture:** 
1. Làm sạch token và mật khẩu hardcode trong cấu hình (`application.properties`, `application-test.properties`, `Bruno/OpenAPI definition/environments/Generated server url.yml`).
2. Xóa các thư mục trùng lặp và file trống (`Bruno/OpenAPI definition - 1`, `SQL/test2.sql`).
3. Dùng `git rm --cached` để gỡ bỏ toàn bộ file tạm, file parquet và file ready JSON trong `analytics/` khỏi Git index mà vẫn giữ file vật lý trên máy.
4. Củng cố quy tắc trong `.gitignore` để ngăn chặn triệt để việc tái phạm trong tương lai.
5. Kiểm tra tính toàn vẹn của mã nguồn (biên dịch thử nghiệm, kiểm tra git status).
6. Hướng dẫn và thực thi dọn dẹp commit trên nhánh `master` / GitHub remote.

**Tech Stack:** Git, Spring Boot, YAML, PowerShell

---

### Task 1: Dọn Dẹp Secret & Token Hardcode Trong Cấu Hình

**Files:**
- Modify: `src/main/resources/application.properties`
- Modify: `src/main/resources/application-test.properties`
- Modify: `Bruno/OpenAPI definition/environments/Generated server url.yml`
- Delete: `Bruno/OpenAPI definition - 1` (directory)
- Delete: `SQL/test2.sql`

- [ ] **Step 1: Xóa mật khẩu cá nhân fallback trong `application.properties` và `application-test.properties`**
  Thay thế fallback password `zedmid555` bằng `${DB_PASSWORD:}` và thay JWT secret bằng generic dev fallback.
- [ ] **Step 2: Làm sạch chuỗi JWT Token trong file môi trường Bruno**
  Trong `Bruno/OpenAPI definition/environments/Generated server url.yml`, làm sạch giá trị các biến `admin`, `quanly`, `thukho`, `thungan` thành chuỗi rỗng và đặt cờ `secret: true`.
- [ ] **Step 3: Xóa thư mục trùng lặp `Bruno/OpenAPI definition - 1` và file rỗng `SQL/test2.sql`**
  Xóa hoàn toàn thư mục duplicate `Bruno/OpenAPI definition - 1` và file `SQL/test2.sql`.

---

### Task 2: Củng Cố Quy Tắc Chặn Trong `.gitignore`

**Files:**
- Modify: `.gitignore`

- [ ] **Step 1: Cập nhật `.gitignore` đầy đủ các mẫu file cấm kỵ**
  Đảm bảo chứa đầy đủ:
  - `logs/`, `*.log`, `*.log.gz`
  - `*.tmp`, `*.crc`
  - `analytics/curated/*` và `!analytics/curated/.gitkeep`
  - `analytics/cassandra/*.tmp`, `analytics/cassandra/*.json`
  - `analytics/simulation/*` và `!analytics/simulation/.gitkeep`
  - `database/secure-connect-*.zip`
  - `.env`, `.env.*`, `!*.example`
  - `Bruno/**/environments/*.local.yml`
  - `*.ready.json`

---

### Task 3: Gỡ Bỏ File Cấm Kỵ Khỏi Git Cache (`git rm --cached`)

**Files:**
- Untrack: `analytics/cassandra/*.tmp`
- Untrack: `analytics/curated/events_2026_10_08.ready.json`
- Untrack: `analytics/curated/events_2026_10_08/0_0_0.parquet`
- Untrack: `analytics/curated/events_2026_10_08/.0_0_0.parquet.crc`
- Untrack: `analytics/curated/events_2026_10_08_cf04509a6dff4d65b27255a42d5d98cf/0_0_0.parquet`
- Untrack: `analytics/curated/events_2026_10_08_cf04509a6dff4d65b27255a42d5d98cf/.0_0_0.parquet.crc`

- [ ] **Step 1: Thực hiện `git rm --cached` cho các file dữ liệu và tạm thời trong `analytics/`**
- [ ] **Step 2: Kiểm tra lại `git status` đảm bảo các file vẫn còn trên ổ đĩa nhưng không còn trong git index**

---

### Task 4: Kiểm Tra Toàn Bộ Trạng Thái Git & Tính Toàn Vẹn Mã Nguồn

- [ ] **Step 1: Kiểm tra `git status` để xác nhận danh sách staged/unstaged changes sạch sẽ**
- [ ] **Step 2: Kiểm tra `git check-ignore` đối với các file nhạy cảm**

---

### Task 5: Xử Lý Lịch Sử Commit Trên GitHub Remote (`master`)

- [ ] **Step 1: Trình bày và xác nhận phương án xử lý lịch sử commit `cddb8df` trên `origin/master` (Hướng A tạo commit mới hay Hướng B viết lại root commit và force push)**
