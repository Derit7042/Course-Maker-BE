package coursemaker.coursemaker.domain.destination.dto;

import coursemaker.coursemaker.domain.destination.entity.Destination;
import coursemaker.coursemaker.domain.tag.dto.TagResponseDto;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class DestinationDto {
    @Schema(description = "여행지 Id", example = "1")
    @NotNull(message = "여행지 ID를 입력해주세요.")
    private Long id;

    @Schema(description = "유저 닉네임", example = "coursemaker")
    private String nickname; // 유저 이름

    @Schema(description = "여행지 이름", defaultValue = "역시 부산은 해운대!")
    @NotNull(message = "여행지 이름을 입력하세요.")
    @NotBlank(message = "여행지 이름은 공백 혹은 빈 문자는 허용하지 않습니다.")
    private String name; // 여행지 이름

    @Schema(description = "조회수", example = "100")
    private int views; // 조회수

    @Schema(description = "태그 리스트")
    @NotNull(message = "태그 리스트는 비어 있을 수 없습니다.")
    @Size(min = 1, message = "적어도 하나의 태그가 있어야 합니다.")
    private List<@Valid TagResponseDto> tags; // 태그 리스트

    @Schema(description = "위치 정보")
    @NotNull(message = "위치 정보는 비어 있을 수 없습니다.")
    @Valid
    private LocationDto location; // 위치

    @Schema(description = "대표 사진", defaultValue = "http://example.com/coursemaker.jpg")
    private String pictureLink; // 대표 사진

    @Schema(description = "텍스트 에디터", defaultValue = "해운대 물이 깨끗하고, 친구들과 여행하기 너무 좋았어요!")
    private String content; // 텍스트 에디터

    @Schema(description = "API에서 가져온 추가 내용")
    private String apiContent;

    @Schema(description = "평균 평점", example = "4.5")
    private Double averageRating; // 평균 평점

    @Schema(description = "해당 여행지가 로그인 한 사용자가 작성한 여행지인지 여부", nullable = true)
    private Boolean isMyDestination;

    @Schema(description = "무장애 여행지 여부", nullable = true, hidden = true)
    private Boolean disabled;

    @Schema(description = "공공데이터 여부", example = "false", defaultValue = "false", nullable = true)
    private Boolean isApiData;

    @Schema(description = "여행지 찜 갯수", example = "70")
    private Integer wishCount;

    @Schema(description = "여행지 리뷰 갯수", example = "60")
    private Integer reviewCount;

    @Schema(description = "여행지 좋아요 갯수", example = "50")
    private Integer likeCount;

    @Schema(description = "해당 여행지 찜이 로그인 한 사용자가 찜한 여행지인지 여부", nullable = true)
    private Boolean isMyWishDestination;

    @Schema(description = "해당 여행지 좋아요를 로그인 한 사용자가 좋아요한 여행지인지 여부" , nullable = true)
    private Boolean isMyLikeDestination;

    // Destination 엔티티를 DestinationDto로 변환하는 메서드
    public static DestinationDto toDto(Destination destination, List<TagResponseDto> tagDtos,
                                       Boolean isApiData, Double averageRating, Boolean isMyDestination, Integer reviewCount,
                                       Integer wishCount, Integer likeCount, Boolean isMyWishDestination, Boolean isMyLikeDestination) {
        DestinationDto dto = new DestinationDto();
        dto.setId(destination.getId());
        dto.setNickname(destination.getMember().getNickname()); // 누가 만들었는지 설정
        dto.setName(destination.getName());
        dto.setViews(destination.getViews());
        dto.setPictureLink(destination.getPictureLink());
        dto.setContent(destination.getContent());
        dto.setApiContent(destination.getApiContent());
        dto.setTags(tagDtos);
        dto.setDisabled(destination.getDisabled());
        LocationDto location = new LocationDto(
                destination.getLocation(),
                destination.getLongitude(),
                destination.getLatitude()
        );
        dto.setLocation(location);
        dto.setAverageRating(averageRating);
        dto.setIsMyDestination(isMyDestination);
        dto.setIsApiData(isApiData);
        dto.setReviewCount(reviewCount);
        dto.setWishCount(wishCount);
        dto.setLikeCount(likeCount);
        dto.setIsMyWishDestination(isMyWishDestination);
        dto.setIsMyLikeDestination(isMyLikeDestination);
        return dto;
    }
}