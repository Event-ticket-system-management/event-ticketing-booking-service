package com.eventticketing.bookingservice.dto.response.paginate;

import com.eventticketing.bookingservice.dto.response.BookingResponseDto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class BookingPaginateResponseDto {
    private List<BookingResponseDto> dataList;
    private long dataCount;
}
