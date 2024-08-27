package coursemaker.coursemaker.domain.wish.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class DestinationWishRequestDto {

    @Schema(description = "목적지의 Id", example = "1")
    @NotNull(message = "목적지 ID를 입력해주세요.")
    private Long destinationId;

    @Schema(hidden = true)
    @NotNull(message = "사용자 nickname을 입력해주세요.")
    private String nickname;

    public DestinationWishRequestDto() {}

    public DestinationWishRequestDto(Long destinationId, String nickname) {
        this.destinationId = destinationId;
        this.nickname = nickname;
    }
}
