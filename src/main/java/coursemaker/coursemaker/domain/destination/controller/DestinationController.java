package coursemaker.coursemaker.domain.destination.controller;

import coursemaker.coursemaker.domain.auth.dto.LoginedInfo;
import coursemaker.coursemaker.domain.auth.exception.UnAuthorizedException;
import coursemaker.coursemaker.domain.destination.dto.DestinationDto;
import coursemaker.coursemaker.domain.destination.dto.RequestDto;
import coursemaker.coursemaker.domain.destination.entity.Destination;
import coursemaker.coursemaker.domain.destination.exception.ForbiddenException;
import coursemaker.coursemaker.domain.destination.service.DestinationService;
import coursemaker.coursemaker.domain.like.service.DestinationLikeService;
import coursemaker.coursemaker.domain.review.service.DestinationReviewService;
import coursemaker.coursemaker.domain.tag.dto.TagResponseDto;
import coursemaker.coursemaker.domain.tag.service.OrderBy;
import coursemaker.coursemaker.domain.tag.service.TagService;
import coursemaker.coursemaker.domain.wish.service.DestinationWishService;
import coursemaker.coursemaker.exception.ErrorResponse;
import coursemaker.coursemaker.util.CourseMakerPagination;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

@io.swagger.v3.oas.annotations.tags.Tag(name = "Destination", description = "여행지 API")
@RestController
@RequestMapping("v1/destination")
public class DestinationController {
    private final DestinationService destinationService;
    private final TagService tagService;
    private final DestinationReviewService destinationReviewService;
    private final DestinationWishService destinationWishService;
    private final DestinationLikeService destinationLikeService;

    public DestinationController(DestinationService destinationService,
                                 TagService tagService,
                                 DestinationReviewService destinationReviewService,
                                 DestinationWishService destinationWishService,
                                 DestinationLikeService destinationLikeService) {
        this.destinationService = destinationService;
        this.tagService = tagService;
        this.destinationReviewService = destinationReviewService;
        this.destinationWishService = destinationWishService;
        this.destinationLikeService = destinationLikeService;
    }

    @Operation(summary = "전체 여행지 목록 조회", description = "한 페이지에 표시할 데이터 수(record)와 조회할 페이지 번호(page)를 입력하여 전체 여행지 목록을 조회합니다. 페이지 번호는 1부터 시작합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "전체 여행지 목록 조회 성공"),
            @ApiResponse(responseCode = "400", description = "잘못된 요청 형식", content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = ErrorResponse.class),
                    examples = @ExampleObject(
                            value = "{\"status\": 400, \"errorType\": \"Illegal argument\", \"message\": \"잘못된 요청 형식입니다.\"}"
                    )
            ))
    })
    @Parameter(name = "tagIds", description = "필터링할 태그 ID 목록(선택 안할 시 전체 태그 조회)", example = "[1, 2, 3]")
    @Parameter(name = "record", description = "한 페이지 당 표시할 데이터 수")
    @Parameter(name = "page", description = "조회할 페이지 번호 (페이지는 1 페이지 부터 시작합니다.)")
    @Parameter(name = "orderBy", description = "정렬 기준 (VIEWS: 조회수, NEWEST: 최신순, POPULAR: 인기순, RATING: 평점순 중 하나)", example = "NEWEST")
    @GetMapping
    public ResponseEntity<CourseMakerPagination<DestinationDto>> getAllDestinations(@RequestParam(name = "tagIds", required = false) List<Long> tagIds,
                                                                                    @RequestParam(defaultValue = "20", name = "record") int record,
                                                                                    @RequestParam(defaultValue = "1", name = "page") int page,
                                                                                    @RequestParam(name = "orderBy", defaultValue = "NEWEST") OrderBy orderBy,
                                                                                    @AuthenticationPrincipal LoginedInfo loginedInfo) {
        Pageable pageable = PageRequest.of(page - 1, record);

        List<DestinationDto> destinationDtos = new ArrayList<>();
        CourseMakerPagination<Destination> destinations = tagService.findAllDestinationByTagIds(tagIds, pageable, orderBy);
        int totalPage = destinations.getTotalPage();
        List<Destination> destinationList = destinations.getContents();

        for (Destination destination : destinationList) {
            // 로그인 정보가 없으면 isMine을 false로 설정, 있으면 기존 로직대로 설정
            Boolean isMyDestination = loginedInfo != null && loginedInfo.getNickname().equals(destination.getMember().getNickname());
            Boolean isMyWishDestination = loginedInfo != null && destinationWishService.isDestinationWishedByUser(destination.getId(), loginedInfo.getNickname());
            List<TagResponseDto> tags = tagService.findAllByDestinationId(destination.getId());
            Double averageRating = destinationReviewService.getAverageRating(destination.getId());
            Integer reviewCount = destinationReviewService.getReviewCount(destination.getId());
            Integer wishCount = destinationWishService.getDestinationWishCount(destination.getId());
            Integer likeCount = destinationLikeService.getDestinationLikeCount(destination.getId());

            destinationDtos.add(DestinationDto.toDto(destination, tags, destination.getIsApiData(), averageRating, isMyDestination, reviewCount, wishCount, likeCount, isMyWishDestination));
        }


        Page<DestinationDto> responsePage = new PageImpl<>(destinationDtos, pageable, totalPage);
        CourseMakerPagination<DestinationDto> response = new CourseMakerPagination<>(pageable, responsePage, totalPage);

        return ResponseEntity.ok(response);
    }

    @Operation(summary = "id에 맞는 여행지 상세 정보 조회", description = "여행지 ID를 입력하여 해당 여행지의 상세 정보를 조회합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "해당 Id에 맞는 여행지 상세 정보 조회 성공", content = @Content(schema = @Schema(implementation = DestinationDto.class))),
            @ApiResponse(responseCode = "401", description = "로그인 후 이용이 가능합니다.", content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = ErrorResponse.class),
                    examples = @ExampleObject(
                            value = "{\"status\": 401, \"errorType\": \"login required\", \"message\": \"로그인 후 이용이 가능합니다.\"}"
                    )
            )),
            @ApiResponse(responseCode = "404", description = "해당 Id에 맞는 여행지를 찾지 못할 때 반환합니다.", content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = ErrorResponse.class),
                    examples = @ExampleObject(
                            value = "{\"status\": 404, \"errorType\": \"Invalid item\", \"message\": \"해당하는 여행지가 없습니다.\"}"
                    )
            ))
    })
    @Parameter(name = "id", description = "여행지 Id")
    @GetMapping("/{id}")
    public ResponseEntity<DestinationDto> getDestinationById(@PathVariable("id") Long id, @AuthenticationPrincipal LoginedInfo loginedInfo) {
        Destination destination = destinationService.findById(id);

        // 로그인한 사용자와 여행지를 작성한 사용자가 동일한지 확인
        // 로그인 정보가 없으면 isMine을 false로 설정, 있으면 기존 로직대로 설정
        Boolean isMyDestination = loginedInfo != null && loginedInfo.getNickname().equals(destination.getMember().getNickname());
        Boolean isMyWishDestination = loginedInfo != null && destinationWishService.isDestinationWishedByUser(destination.getId(), loginedInfo.getNickname());
        List<TagResponseDto> tags = tagService.findAllByDestinationId(id);
        Double averageRating = destinationReviewService.getAverageRating(id);
        Integer reviewCount = destinationReviewService.getReviewCount(destination.getId());
        Integer wishCount = destinationWishService.getDestinationWishCount(destination.getId());
        Integer likeCount = destinationLikeService.getDestinationLikeCount(destination.getId());

        DestinationDto destinationDto = DestinationDto.toDto(destination, tags, destination.getIsApiData(), averageRating, isMyDestination, reviewCount, wishCount, likeCount, isMyWishDestination);
        return ResponseEntity.ok(destinationDto);
    }

    @Operation(summary = "제목으로 여행지 검색", description = "제목에 특정 문자열이 포함된 여행지를 검색합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "검색 성공"),
            @ApiResponse(responseCode = "400", description = "잘못된 요청 형식", content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = ErrorResponse.class),
                    examples = @ExampleObject(
                            value = "{\"status\": 400, \"errorType\": \"Illegal argument\", \"message\": \"잘못된 요청 형식입니다.\"}"
                    )
            ))
    })
    @Parameters({
            @Parameter(name = "title", description = "검색할 여행지의 제목", required = true),
            @Parameter(name = "record", description = "한 페이지당 표시할 데이터 수", example = "20"),
            @Parameter(name = "page", description = "조회할 페이지 번호 (페이지는 1부터 시작합니다)", example = "1")
    })
    @GetMapping("/search")
    public ResponseEntity<CourseMakerPagination<DestinationDto>> searchDestinationsByTitle(
            @RequestParam String title,
            @RequestParam(defaultValue = "20", name = "record") Integer record,
            @RequestParam(defaultValue = "1", name = "page") Integer page,
            @AuthenticationPrincipal LoginedInfo loginedInfo) {

        Pageable pageable = PageRequest.of(page - 1, record);
        CourseMakerPagination<Destination> destinationPage = destinationService.findByNameContaining(title, pageable);

        // 변환 로직 (CourseMakerPagination<Destination> -> CourseMakerPagination<DestinationDto>)
        List<DestinationDto> contents = destinationPage.getContents().stream()
                .map(destination -> {
                    Boolean isMyDestination = loginedInfo != null && loginedInfo.getNickname().equals(destination.getMember().getNickname());
                    Boolean isMyWishDestination = loginedInfo != null && destinationWishService.isDestinationWishedByUser(destination.getId(), loginedInfo.getNickname());
                    List<TagResponseDto> tags = tagService.findAllByDestinationId(destination.getId());
                    Double averageRating = destinationReviewService.getAverageRating(destination.getId());
                    Integer reviewCount = destinationReviewService.getReviewCount(destination.getId());
                    Integer wishCount = destinationWishService.getDestinationWishCount(destination.getId());
                    Integer likeCount = destinationLikeService.getDestinationLikeCount(destination.getId());
                    return DestinationDto.toDto(destination, tags, destination.getIsApiData(), averageRating, isMyDestination, reviewCount, wishCount, likeCount, isMyWishDestination);
                })
                .toList();

        Page<DestinationDto> responsePage = new PageImpl<>(contents, pageable, destinationPage.getTotalPage());
        CourseMakerPagination<DestinationDto> response = new CourseMakerPagination<>(pageable, responsePage, destinationPage.getTotalContents());

        return ResponseEntity.ok(response);
    }

    @Operation(summary = "닉네임으로 여행지 조회", description = "특정 닉네임의 사용자가 생성한 모든 여행지를 조회합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "404", description = "해당 닉네임에 맞는 여행지를 찾을 수 없음", content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = ErrorResponse.class),
                    examples = @ExampleObject(
                            value = "{\"status\": 404, \"errorType\": \"Invalid item\", \"message\": \"해당하는 여행지를 찾을 수 없습니다.\"}"
                    )
            ))
    })
    @Parameters({
            @Parameter(name = "nickname", description = "조회할 사용자의 닉네임", required = true),
            @Parameter(name = "record", description = "한 페이지당 표시할 데이터 수", example = "20"),
            @Parameter(name = "page", description = "조회할 페이지 번호 (페이지는 1부터 시작합니다)", example = "1")
    })
    @GetMapping("/nickname/{nickname}")
    public ResponseEntity<CourseMakerPagination<DestinationDto>> findDestinationsByNickname(
            @PathVariable("nickname") String nickname,
            @RequestParam(defaultValue = "20", name = "record") Integer record,
            @RequestParam(defaultValue = "1", name = "page") Integer page,
            @AuthenticationPrincipal LoginedInfo loginedInfo) {

        Pageable pageable = PageRequest.of(page - 1, record);
        CourseMakerPagination<Destination> destinationPage = destinationService.findByMemberNickname(nickname, pageable);

        // 변환 로직 (CourseMakerPagination<Destination> -> CourseMakerPagination<DestinationDto>)
        List<DestinationDto> contents = destinationPage.getContents().stream()
                .map(destination -> {
                    Boolean isMyDestination = loginedInfo != null && loginedInfo.getNickname().equals(destination.getMember().getNickname());
                    Boolean isMyWishDestination = loginedInfo != null && destinationWishService.isDestinationWishedByUser(destination.getId(), loginedInfo.getNickname());
                    List<TagResponseDto> tags = tagService.findAllByDestinationId(destination.getId());
                    Double averageRating = destinationReviewService.getAverageRating(destination.getId());
                    Integer reviewCount = destinationReviewService.getReviewCount(destination.getId());
                    Integer wishCount = destinationWishService.getDestinationWishCount(destination.getId());
                    Integer likeCount = destinationLikeService.getDestinationLikeCount(destination.getId());
                    return DestinationDto.toDto(destination, tags, destination.getIsApiData(), averageRating, isMyDestination, reviewCount, wishCount, likeCount, isMyWishDestination);
                })
                .toList();

        Page<DestinationDto> responsePage = new PageImpl<>(contents, pageable, destinationPage.getTotalPage());
        CourseMakerPagination<DestinationDto> response = new CourseMakerPagination<>(pageable, responsePage, destinationPage.getTotalContents());

        return ResponseEntity.ok(response);
    }

    @Operation(summary = "여행지 생성", description = "새로운 여행지 정보를 입력하여 여행지를 생성합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "여행지가 성공적으로 생성되었습니다. 헤더의 Location 필드에 생성된 데이터에 접근할 수 있는 주소를 반환합니다."),
            @ApiResponse(responseCode = "400", description = "생성하려는 여행지의 인자값이 올바르지 않을 때 반환합니다.", content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = ErrorResponse.class),
                    examples = @ExampleObject(
                            value = "{\"status\": 400, \"errorType\": \"Illegal argument\", \"message\": \"여행지 이름은 공백 혹은 빈 문자는 허용하지 않습니다.\"}"
                    )
            )),
            @ApiResponse(responseCode = "401", description = "로그인 후 이용이 가능합니다.", content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = ErrorResponse.class),
                    examples = @ExampleObject(
                            value = "{\"status\": 401, \"errorType\": \"login required\", \"message\": \"로그인 후 이용이 가능합니다.\"}"
                    )
            )),
            @ApiResponse(responseCode = "409", description = "생성하려는 여행지의 이름이 이미 있을 때 반환합니다.", content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = ErrorResponse.class),
                    examples = @ExampleObject(
                            value = "{\"status\": 409, \"errorType\": \"Duplicated item\", \"message\": \"여행지 이름이 이미 존재합니다.\"}"
                    )
            ))
    })
    @PostMapping
    public ResponseEntity<DestinationDto> createDestination(@Valid @RequestBody RequestDto request, @AuthenticationPrincipal LoginedInfo loginedInfo) {
        // 로그인 한 사용자 닉네임 가져오기
        // 로그인한 사용자 닉네임을 설정, 로그인이 되어 있지 않으면 null
        String nickname = loginedInfo != null ? loginedInfo.getNickname() : null;

        // 로그인이 되어 있지 않으면 401 Unauthorized 응답을 반환
        if (nickname == null) {
            throw new UnAuthorizedException("login required", "로그인 후 이용이 가능합니다.");
        }
        request.setNickname(nickname);
        Destination savedDestination = destinationService.save(request);
        Boolean isMyDestination = loginedInfo != null && loginedInfo.getNickname().equals(savedDestination.getMember().getNickname());
        Boolean isMyWishDestination = loginedInfo != null && destinationWishService.isDestinationWishedByUser(savedDestination.getId(), loginedInfo.getNickname());
        Double averageRating = destinationReviewService.getAverageRating(savedDestination.getId());
        List<TagResponseDto> tags = tagService.findAllByDestinationId(savedDestination.getId());
        Integer reviewCount = destinationReviewService.getReviewCount(savedDestination.getId());
        Integer wishCount = destinationWishService.getDestinationWishCount(savedDestination.getId());
        Integer likeCount = destinationLikeService.getDestinationLikeCount(savedDestination.getId());
        DestinationDto response = DestinationDto.toDto(savedDestination, tags, request.getIsApiData(), averageRating, isMyDestination, reviewCount, wishCount, likeCount, isMyWishDestination);

        return ResponseEntity.created(URI.create("/v1/destination/" + savedDestination.getId())).body(response);
    }

    @Operation(summary = "id에 해당하는 여행지 수정", description = "여행지 ID와 수정할 정보를 입력하여 해당 여행지의 정보를 수정합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Id에 해당하는 여행지 수정 성공", content = @Content(schema = @Schema(implementation = DestinationDto.class))),
            @ApiResponse(responseCode = "400", description = "수정하려는 여행지의 인자값이 올바르지 않을 때 반환합니다.", content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = ErrorResponse.class),
                    examples = {
                            @ExampleObject(
                                    name = "여행지 공백",
                                    summary = "여행지 이름이 공백일 때",
                                    description = "여행지 이름이 공백일 때 반환되는 오류입니다.",
                                    value = "{\"status\": 400, \"errorType\": \"Illegal argument\", \"message\": \"여행지 이름은 공백 혹은 빈 문자는 허용하지 않습니다.\"}"
                            ),
                            @ExampleObject(
                                    name = "태그 리스트 공백",
                                    summary = "태그 리스트가 비어 있을 때",
                                    description = "태그 리스트가 비어 있을 때 반환되는 오류입니다.",
                                    value = "{\"status\": 400, \"errorType\": \"Illegal argument\", \"message\": \"태그 리스트는 비어 있을 수 없습니다.\"}"
                            ),
                            @ExampleObject(
                                    name = "태그 ID 공백",
                                    summary = "태그 ID가 비어 있을 때",
                                    description = "태그 ID가 비어 있을 때 반환되는 오류입니다.",
                                    value = "{\"status\": 400, \"errorType\": \"Illegal argument\", \"message\": \"태그 ID를 입력해주세요.\"}"
                            ),
                            @ExampleObject(
                                    name = "태그 이름 공백",
                                    summary = "태그 이름이 공백일 때",
                                    description = "태그 이름이 공백일 때 반환되는 오류입니다.",
                                    value = "{\"status\": 400, \"errorType\": \"Illegal argument\", \"message\": \"태그 이름은 공백 혹은 빈 문자는 허용하지 않습니다.\"}"
                            ),
                            @ExampleObject(
                                    name = "태그 설명 공백",
                                    summary = "태그 설명이 공백일 때",
                                    description = "태그 설명이 공백일 때 반환되는 오류입니다.",
                                    value = "{\"status\": 400, \"errorType\": \"Illegal argument\", \"message\": \"태그에 대한 설명은 공백 혹은 빈 문자는 허용하지 않습니다.\"}"
                            ),
                            @ExampleObject(
                                    name = "위치 리스트 공백",
                                    summary = "위치 정보가 비어 있을 때",
                                    description = "위치 정보가 비어 있을 때 반환되는 오류입니다.",
                                    value = "{\"status\": 400, \"errorType\": \"Illegal argument\", \"message\": \"위치 정보는 비어 있을 수 없습니다.\"}"
                            ),
                            @ExampleObject(
                                    name = "도로명 주소 공백",
                                    summary = "도로명 주소가 공백일 때",
                                    description = "도로명 주소가 공백일 때 반환되는 오류입니다.",
                                    value = "{\"status\": 400, \"errorType\": \"Illegal argument\", \"message\": \"도로명 주소는 공백 혹은 빈 문자는 허용하지 않습니다.\"}"
                            ),
                            @ExampleObject(
                                    name = "경도 공백",
                                    summary = "경도가 비어 있을 때",
                                    description = "경도가 비어 있을 때 반환되는 오류입니다.",
                                    value = "{\"status\": 400, \"errorType\": \"Illegal argument\", \"message\": \"경도를 입력하세요.\"}"
                            ),
                            @ExampleObject(
                                    name = "위도 공백",
                                    summary = "위도가 비어 있을 때",
                                    description = "위도가 비어 있을 때 반환되는 오류입니다.",
                                    value = "{\"status\": 400, \"errorType\": \"Illegal argument\", \"message\": \"위도를 입력하세요.\"}"
                            ),
                            @ExampleObject(
                                    name = "대표 사진 링크 공백",
                                    summary = "대표 사진 링크가 공백일 때",
                                    description = "대표 사진 링크가 공백일 때 반환되는 오류입니다.",
                                    value = "{\"status\": 400, \"errorType\": \"Illegal argument\", \"message\": \"대표 사진 링크는 공백 혹은 빈 문자는 허용하지 않습니다.\"}"
                            ),
                            @ExampleObject(
                                    name = "내용 공백",
                                    summary = "내용이 공백일 때",
                                    description = "내용이 공백일 때 반환되는 오류입니다.",
                                    value = "{\"status\": 400, \"errorType\": \"Illegal argument\", \"message\": \"내용은 공백 혹은 빈 문자는 허용하지 않습니다.\"}"
                            )
                    }
            )),
            @ApiResponse(responseCode = "401", description = "로그인 후 이용이 가능합니다.", content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = ErrorResponse.class),
                    examples = @ExampleObject(
                            value = "{\"status\": 401, \"errorType\": \"login required\", \"message\": \"로그인 후 이용이 가능합니다.\"}"
                    )
            )),
            @ApiResponse(responseCode = "403", description = "접근 권한이 없을 때 반환합니다.", content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = ErrorResponse.class),
                    examples = @ExampleObject(
                            value = "{\"status\": 403, \"errorType\": \"Forbidden\", \"message\": \"접근 권한이 없습니다.\"}"
                    )
            )),
            @ApiResponse(responseCode = "404", description = "수정하려는 여행지의 id를 찾지 못할 때 반환합니다.", content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = ErrorResponse.class),
                    examples = @ExampleObject(
                            value = "{\"status\": 404, \"errorType\": \"Invalid item\", \"message\": \"해당하는 여행지가 없습니다.\"}"
                    )
            )),
            @ApiResponse(responseCode = "409", description = "수정하려는 여행지의 이름이 이미 있을 때 반환합니다.", content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = ErrorResponse.class),
                    examples = @ExampleObject(
                            value = "{\"status\": 409, \"errorType\": \"Duplicated item\", \"message\": \"여행지 이름이 이미 존재합니다.\"}"
                    )
            ))
    })
    @Parameter(name = "id", description = "여행지 Id")
    @PatchMapping("/{id}")
    public ResponseEntity<DestinationDto> updateDestination(@PathVariable("id") Long id, @Valid @RequestBody RequestDto request, @AuthenticationPrincipal LoginedInfo loginedInfo) {
        // 로그인 한 사용자 닉네임 가져오기
        // 로그인한 사용자 닉네임을 설정, 로그인이 되어 있지 않으면 null
        String nickname = loginedInfo != null ? loginedInfo.getNickname() : null;

        // 로그인이 되어 있지 않으면 401 Unauthorized 응답을 반환
        if (nickname == null) {
            throw new UnAuthorizedException("login required", "로그인 후 이용이 가능합니다.");
        }
        request.setNickname(nickname);
        // 해당 여행지가 로그인한 사용자에게 속하는지 확인
        Destination existingDestination = destinationService.findById(id);
        if (!existingDestination.getMember().getNickname().equals(nickname)) {
            throw new ForbiddenException("Forbidden", "사용자가 이 자원에 접근할 권한이 없습니다.");
        }
        Destination updatedDestination = destinationService.update(id, request);
        Boolean isMyDestination = loginedInfo != null && loginedInfo.getNickname().equals(updatedDestination.getMember().getNickname());
        Boolean isMyWishDestination = loginedInfo != null && destinationWishService.isDestinationWishedByUser(updatedDestination.getId(), loginedInfo.getNickname());
        List<TagResponseDto> updatedTags = tagService.findAllByDestinationId(updatedDestination.getId());
        Double averageRating = destinationReviewService.getAverageRating(updatedDestination.getId());
        Integer reviewCount = destinationReviewService.getReviewCount(updatedDestination.getId());
        Integer wishCount = destinationWishService.getDestinationWishCount(updatedDestination.getId());
        Integer likeCount = destinationLikeService.getDestinationLikeCount(updatedDestination.getId());
        DestinationDto updatedDto = DestinationDto.toDto(updatedDestination, updatedTags, request.getIsApiData(), averageRating, isMyDestination, reviewCount, wishCount, likeCount, isMyWishDestination);
        return ResponseEntity.ok(updatedDto);
    }


    @Operation(summary = "id에 해당하는 여행지 삭제", description = "여행지 ID를 입력하여 해당 여행지를 삭제합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Id에 해당하는 여행지 삭제 성공", content = @Content),
            @ApiResponse(responseCode = "401", description = "로그인 후 이용이 가능합니다.", content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = ErrorResponse.class),
                    examples = @ExampleObject(
                            value = "{\"status\": 401, \"errorType\": \"login required\", \"message\": \"로그인 후 이용이 가능합니다.\"}"
                    )
            )),
            @ApiResponse(responseCode = "403", description = "접근 권한이 없을 때 반환합니다.", content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = ErrorResponse.class),
                    examples = @ExampleObject(
                            value = "{\"status\": 403, \"errorType\": \"Forbidden\", \"message\": \"접근 권한이 없습니다.\"}"
                    )
            )),
            @ApiResponse(responseCode = "404", description = "삭제하려는 여행지의 id를 찾지 못할 때 반환합니다.", content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = ErrorResponse.class),
                    examples = @ExampleObject(
                            value = "{\"status\": 404, \"errorType\": \"Invalid item\", \"message\": \"해당하는 여행지가 없습니다.\"}"
                    )
            ))
    })
    @Parameter(name = "id", description = "여행지 Id")
    @DeleteMapping("/{id}")
    public ResponseEntity<Long> deleteDestinationById(@PathVariable("id") Long id, @AuthenticationPrincipal LoginedInfo loginedInfo) {
        // 로그인 한 사용자 닉네임 가져오기
        // 로그인한 사용자 닉네임을 설정, 로그인이 되어 있지 않으면 null
        String nickname = loginedInfo != null ? loginedInfo.getNickname() : null;

        // 로그인이 되어 있지 않으면 401 Unauthorized 응답을 반환
        if (nickname == null) {
            throw new UnAuthorizedException("login required", "로그인 후 이용이 가능합니다.");
        }
        // 해당 ID의 여행지가 존재하는지 확인합니다.
        Destination destination = destinationService.findById(id);
        if (destination == null) {
            return ResponseEntity.notFound().build();
        }
        // 해당 여행지가 로그인한 사용자에게 속하는지 확인
        if (!destination.getMember().getNickname().equals(nickname)) {
            throw new ForbiddenException("Forbidden", "사용자가 이 자원에 접근할 권한이 없습니다.");
        }
        // 여행지에 연결된 태그들을 먼저 삭제합니다.
        tagService.deleteAllTagByDestination(id);
        // 여행지 자체를 삭제합니다.
        destinationService.deleteById(id);
        return ResponseEntity.ok(id);
    }
}