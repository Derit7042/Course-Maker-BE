package coursemaker.coursemaker.domain.auth.dto.login_logout;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class LoginResponseDTO {
    @Schema(description = "액세스 토큰", example = "access_token_example")
    String accessToken;

    @Schema(description = "리프레시 토큰", example = "refresh_token_example")
    String refreshToken;

    @Schema(description = "유저 등급. 유저 등급은 '초보 여행가' , '중급 여행가', '프로 여행가', '관리자' 로 분류됩니다.", example = "초보 여행가")
    String role;
}

