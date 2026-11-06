# Tài liệu đồ án NoSQL – Hệ thống quản lý siêu thị

| Tài liệu | Nội dung |
|---|---|
| [SRS v2.0](SRS_He_thong_quan_ly_sieu_thi_v2_0.docx) | Đặc tả yêu cầu theo mẫu của học phần: 24 FR, 13 NFR, 25 access pattern, sơ đồ, ma trận truy vết |
| [SRS v1.1](SRS_He_thong_quan_ly_sieu_thi_v1_1_viet_lai.docx) | Bản trước khi chuyển sang NoSQL, giữ để đối chiếu |
| [Spec thiết kế](superpowers/specs/2026-11-07-nosql-redesign-design.md) | Thiết kế 4 CSDL, đồng bộ, khuyến mãi, API, frontend, kỹ thuật nâng cao |
| [Kế hoạch triển khai](superpowers/plans/) | Kế hoạch theo từng giai đoạn; kế hoạch 1 có lộ trình tổng |
| [srs-assets/](srs-assets/) | Script vẽ sơ đồ (`make_diagrams.py`) và dựng SRS (`build_srs_v2.py`, nội dung ở `srs_content.py`) |
| [Quy tắc FEFO](nosql/QUY_TAC_SAN_PHAM_LO_HANG_FEFO.md) | Quy tắc sản phẩm, lô hàng và cấp phát FEFO |

Dựng lại SRS sau khi sửa nội dung:

```bash
python -X utf8 docs/srs-assets/make_diagrams.py
python -X utf8 docs/srs-assets/build_srs_v2.py
```

Sau đó mở file trong Word, bấm cập nhật mục lục (F9) và lưu.

Bản Drill/BigData trước đây vẫn còn trong lịch sử git ở commit `267e192`.
