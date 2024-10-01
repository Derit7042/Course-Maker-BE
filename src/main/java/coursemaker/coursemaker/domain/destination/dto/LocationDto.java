package coursemaker.coursemaker.domain.destination.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class LocationDto {
    @Schema(description = "도로명 주소", example = "부산광역시 해운대구 우동")
    @NotNull(message = "도로명 주소를 입력하세요.")
    @NotEmpty(message = "도로명 주소는 비어 있을 수 없습니다.")
    @NotBlank(message = "도로명 주소는 공백 혹은 빈 문자는 허용하지 않습니다.")
    private String address; // 위치
    @Schema(description = "경도", example = "128.934")
//    @NotNull(message = "경도를 입력하세요.")
    private BigDecimal longitude; // 경도
    @Schema(description = "위도", example = "35.169")
//    @NotNull(message = "위도를 입력하세요.")
    private BigDecimal latitude; // 위도
}
