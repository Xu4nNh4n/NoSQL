// PaginationValidator.java dùng để validate các tham số phân trang (pagination) trong các API. Nó kiểm tra các giá trị của page, size, sortBy, sortDir và allowedSortFields để đảm bảo rằng chúng hợp lệ trước khi thực hiện truy vấn cơ sở dữ liệu.
package com.api.supermarket.validation;

import java.util.Set;

import com.api.supermarket.exception.BadRequestException;

public final class PaginationValidator {
    private PaginationValidator(){
        //Không cho tạo object vì class này chỉ chứa các phương thức tĩnh
    }

    public  static void validate(
        int page,
        int size,
        String sortBy,
        String sortDir, 
        Set<String> allowedSortFields // Danh sách các trường được phép sắp xếp
    ){
        // Kiểm tra giá trị của page và size
        if(page < 0){
            throw new BadRequestException("Trang không được nhỏ hơn 0");
        }
        if(size < 1 || size > 100){
            throw new BadRequestException("Kích thước trang không được nhỏ hơn 1 hoặc lớn hơn 100");
        }
        if(sortDir == null // Nếu sortDir là null hoặc không phải "asc" hoặc "desc" thì ném lỗi
            || (!sortDir.equalsIgnoreCase("asc") // Nếu sortDir không phải "asc" hoặc "desc" thì ném lỗi
            && !sortDir.equalsIgnoreCase("desc"))){ // Nếu sortDir không phải "asc" hoặc "desc" thì ném lỗi
            throw new BadRequestException("Hướng sắp xếp không hợp lệ. Vui lòng sử dụng 'asc' hoặc 'desc'");
        }

        if(sortBy == null ||
            !allowedSortFields.contains(sortBy)
        ){
             throw new BadRequestException("SortBy không hợp lệ. Các field được phép: " + allowedSortFields);
        }
    }

}
