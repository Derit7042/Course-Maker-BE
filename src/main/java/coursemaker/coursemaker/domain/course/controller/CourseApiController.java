package coursemaker.coursemaker.domain.course.controller;

import coursemaker.coursemaker.domain.auth.dto.LoginedInfo;
import coursemaker.coursemaker.domain.auth.exception.UnAuthorizedException;
import coursemaker.coursemaker.domain.course.dto.AddTravelCourseRequest;
import coursemaker.coursemaker.domain.course.dto.CourseDestinationResponse;
import coursemaker.coursemaker.domain.course.dto.TravelCourseResponse;
import coursemaker.coursemaker.domain.course.dto.UpdateTravelCourseRequest;
import coursemaker.coursemaker.domain.course.entity.TravelCourse;
import coursemaker.coursemaker.domain.course.service.CourseDestinationService;
import coursemaker.coursemaker.domain.course.service.CourseService;

import coursemaker.coursemaker.domain.like.service.CourseLikeService;
import coursemaker.coursemaker.domain.review.service.CourseReviewService;

import coursemaker.coursemaker.domain.tag.service.OrderBy;
import coursemaker.coursemaker.domain.tag.dto.TagResponseDto;

import coursemaker.coursemaker.domain.tag.service.TagService;
import coursemaker.coursemaker.domain.wish.service.CourseWishService;
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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/v1/courses")
@io.swagger.v3.oas.annotations.tags.Tag(name = "Course", description = "여행 코스 API")
public class CourseApiController {

    private final CourseService courseService;

    private final TagService tagService;
    private final CourseReviewService courseReviewService;
    private final CourseDestinationService courseDestinationService;
    private final CourseWishService courseWishService;
    private final CourseLikeService courseLikeService;

    // POST
    /*********스웨거 어노테이션**********/
    @Operation(summary = "코스 등록", description = "유저가 코스를 등록합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "코스 등록 성공, 헤더의 location에 생성된 데이터에 접근할 수 있는 주소를 반환하고, 생성된 코스 정보는 body에 반환됩니다."),
            @ApiResponse(responseCode = "400", description = "생성하려는 여행지의 인자값이 올바르지 않을 때 반환합니다.", content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = ErrorResponse.class),
                    examples = @ExampleObject(
                            value = "{\"status\": 400, \"errorType\": \"Illegal argument\", \"message\": \"코스 이름은 공백 혹은 빈 문자는 허용하지 않습니다.\"}"
                    )
            )),
            @ApiResponse(responseCode = "401", description = "로그인 후 이용이 가능합니다.", content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = ErrorResponse.class),
                    examples = @ExampleObject(
                            value = "{\"status\": 401, \"errorType\": \"login required\", \"message\": \"로그인 후 이용이 가능합니다.\"}"
                    )
            )),
            @ApiResponse(responseCode = "403", description = "여행자 등급이 부족해서 코스를 생성하지 못할때.", content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = ErrorResponse.class),
                    examples = @ExampleObject(
                            value = "{\"status\": 403, \"errorType\": \"Access denied\", \"message\": \"여행자 등급이 부족해서 접근할 수 없습니다.\"}"
                    )
            )),
            @ApiResponse(responseCode = "409", description = "생성하려는 코스의 이름이 이미 있을 때 반환합니다.", content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = ErrorResponse.class),
                    examples = @ExampleObject(
                            value = "{\"status\": 409, \"errorType\": \"Duplicated item\", \"message\": \"코스 이름이 이미 존재합니다.\"}"
                    )
            ))
    })
/*********스웨거 어노테이션**********/
    @PostMapping
    @PreAuthorize(value = "" +
            "hasAnyRole(" +
            "'ROLE_INTERMEDIATE_TRAVELER', " +
            "'ROLE_PRO_TRAVELER', " +
            "'ROLE_PARTNER', " +
            "'ROLE_ADMIN'" +
            ")"
    )
    public ResponseEntity<TravelCourseResponse> createTravelCourse(@RequestBody @Valid AddTravelCourseRequest request,
                                                                   @AuthenticationPrincipal LoginedInfo loginedInfo) {
        /*로그인 한 사용자 닉네임*/
        // 로그인한 사용자 닉네임을 설정, 로그인이 되어 있지 않으면 null
        String nickname = loginedInfo != null ? loginedInfo.getNickname() : null;

        // 로그인이 되어 있지 않으면 401 Unauthorized 응답을 반환
        if (nickname == null) {
            throw new UnAuthorizedException("login required", "로그인 후 이용이 가능합니다.");
        }
        request.setNickname(nickname);
        TravelCourse savedTravelCourse = courseService.save(request);

        /*생성한 코스 바디에 넣어서 반환*/
        TravelCourseResponse newCourseResponse = findTravelCourseById(savedTravelCourse.getId(), loginedInfo).getBody();

        return (savedTravelCourse != null) ?
                ResponseEntity.created(URI.create("/v1/courses/" + savedTravelCourse.getId())).body(newCourseResponse):
                ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
    }


    // GET
    /*********스웨거 어노테이션**********/
    @Operation(summary = "모든 여행 코스 조회", description = "조회수를 기준으로 정렬된 모든 여행 코스를 페이지네이션하여 조회합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "400", description = "잘못된 요청 형식", content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = ErrorResponse.class),
                    examples = @ExampleObject(
                            value = "{\"status\": 400, \"errorType\": \"Illegal argument\", \"message\": \"잘못된 요청 형식입니다.\"}"
                    )
            )),
            @ApiResponse(responseCode = "404", description = "리소스를 찾을 수 없음", content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = ErrorResponse.class),
                    examples = @ExampleObject(
                            value = "{\"status\": 404, \"errorType\": \"Invalid item\", \"message\": \"해당하는 코스를 찾을 수 없습니다.\"}"
                    )
            ))
    })
    @Parameters({
            @Parameter(name = "record", description = "한 페이지당 표시할 데이터 수"),
            @Parameter(name = "page", description = "조회할 페이지 번호(페이지는 1 페이지 부터 시작합니다.)"),
            @Parameter(name = "tagIds", description = "태그를 선택하지 않으면 전체 태그로 조회됩니다."),
            @Parameter(name = "orderBy", description = "정렬하는 기능을 나타냅니다. NEWEST는 최신순을 의미합니다.")
    })
    /*********스웨거 어노테이션**********/
    @GetMapping
    public ResponseEntity<CourseMakerPagination<TravelCourseResponse>> findAllTravelCourse(@RequestParam(name = "tagIds", required = false) List<Long> tagIds,
                                                                                           @RequestParam(defaultValue = "20", name = "record") Integer record,
                                                                                           @RequestParam(defaultValue = "1", name = "page") Integer page,
                                                                                           @RequestParam(defaultValue = "NEWEST", name = "orderBy") OrderBy orderBy,
                                                                                           @AuthenticationPrincipal LoginedInfo loginedInfo) {

        Pageable pageable = PageRequest.of(page - 1, record);
        List<TravelCourseResponse> contents = new ArrayList<>();
        CourseMakerPagination<TravelCourse> travelCoursePage = tagService.findAllCourseByTagIds(tagIds, pageable, orderBy);
        Long totalContents = travelCoursePage.getTotalContents();
        List<TravelCourse> travelCourses = travelCoursePage.getContents();

        for (TravelCourse travelCourse : travelCourses) {
            Boolean isMyCourse = loginedInfo != null && loginedInfo.getNickname().equals(travelCourse.getMember().getNickname());
            Boolean isMyWishCourse = loginedInfo != null && courseWishService.isCourseWishedByUser(travelCourse.getId(), loginedInfo.getNickname());
            Boolean isMyLikeCourse = loginedInfo != null && courseLikeService.isCourseLikedByUser(travelCourse.getId(), loginedInfo.getNickname());

            List<CourseDestinationResponse> courseDestinationResponses = courseDestinationService.getCourseDestinations(travelCourse)
                    .stream()
                    .map(courseDestination -> courseDestinationService.toResponse(courseDestination, loginedInfo))
                    .toList();

            List<TagResponseDto> tags = tagService.findAllByCourseId(travelCourse.getId());
            Double averageRating = courseReviewService.getAverageRating(travelCourse.getId());
            Integer reviewCount = courseReviewService.getReviewCount(travelCourse.getId());
            Integer wishCount = courseWishService.getCourseWishCount(travelCourse.getId());
            Integer likeCount = courseLikeService.getCourseLikeCount(travelCourse.getId());

            contents.add(new TravelCourseResponse(travelCourse, courseDestinationResponses, tags, isMyCourse, averageRating, reviewCount, wishCount, likeCount, isMyWishCourse, isMyLikeCourse));
        }

        Page<TravelCourseResponse> responsePage = new PageImpl<>(contents, pageable, totalContents);
        CourseMakerPagination<TravelCourseResponse> response = new CourseMakerPagination<>(pageable, responsePage, totalContents);

        return ResponseEntity.ok(response);
    }


    /*********스웨거 어노테이션**********/
    @Operation(summary = "ID로 여행 코스 조회", description = "ID를 사용하여 특정 여행 코스를 조회합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "해당 Id에 맞는 코스 상세 정보 조회 성공"),
            @ApiResponse(responseCode = "404", description = "해당 Id에 맞는 코스를 찾지 못할 때 반환합니다.", content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = ErrorResponse.class),
                    examples = @ExampleObject(
                            value = "{\"status\": 404, \"errorType\": \"Invalid item\", \"message\": \"해당하는 코스를 찾을 수 없습니다.\"}"
                    )
            ))
    })
    @Parameters({
            @Parameter(name = "id", description = "조회할 코스의 id값 입니다.")
    })
    /*********스웨거 어노테이션**********/
    @GetMapping("/{id}")
    public ResponseEntity<TravelCourseResponse> findTravelCourseById(@PathVariable("id") Long id, @AuthenticationPrincipal LoginedInfo loginedInfo) {
        TravelCourse travelCourse = courseService.incrementViews(id);

        // 로그인 한 사용자의 닉네임과 코스를 작성한 사용자의 닉네임을 비교
        // 로그인 정보가 없으면 isMine을 false로 설정, 있으면 기존 로직대로 설정
        Boolean isMyCourse = loginedInfo != null && loginedInfo.getNickname().equals(travelCourse.getMember().getNickname());
        Boolean isMyWishCourse = loginedInfo != null && courseWishService.isCourseWishedByUser(travelCourse.getId(), loginedInfo.getNickname());
        Boolean isMyLikeCourse = loginedInfo != null && courseLikeService.isCourseLikedByUser(travelCourse.getId(), loginedInfo.getNickname());

        /*TODO: ROW MAPPER로 DTO-entity 변환*/
        List<CourseDestinationResponse> courseDestinationResponses = courseDestinationService.getCourseDestinations(travelCourse)
                .stream()
                .map(courseDestination -> courseDestinationService.toResponse(courseDestination, loginedInfo))
                .toList();

        /*TODO: 제가 왜 이렇게 해결했는지 설명ㄱㄱ*/
//        List<TagResponseDto> tags = tagService.findAllByCourseId(travelCourse.getId())
//                .stream().map(Tag::toResponseDto).toList();
        List<TagResponseDto> tags = tagService.findAllByCourseId(travelCourse.getId());
        Double averageRating = courseReviewService.getAverageRating(travelCourse.getId());
        Integer reviewCount = courseReviewService.getReviewCount(travelCourse.getId());
        Integer wishCount = courseWishService.getCourseWishCount(travelCourse.getId());
        Integer likeCount = courseLikeService.getCourseLikeCount(travelCourse.getId());

        return ResponseEntity.ok(new TravelCourseResponse(travelCourse, courseDestinationResponses, tags, isMyCourse, averageRating, reviewCount, wishCount, likeCount, isMyWishCourse, isMyLikeCourse));
    }

    // GET - Search by title
    @Operation(summary = "제목으로 여행 코스 검색", description = "제목에 특정 문자열이 포함된 여행 코스를 검색합니다.")
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
            @Parameter(name = "title", description = "검색할 코스의 제목"),
            @Parameter(name = "record", description = "한 페이지당 표시할 데이터 수"),
            @Parameter(name = "page", description = "조회할 페이지 번호(페이지는 1 페이지부터 시작합니다.)")
    })
    @GetMapping("/search")
    public ResponseEntity<CourseMakerPagination<TravelCourseResponse>> searchCoursesByTitle(@RequestParam String title,
                                                                                            @RequestParam(defaultValue = "20", name = "record") Integer record,
                                                                                            @RequestParam(defaultValue = "1", name = "page") Integer page,
                                                                                            @AuthenticationPrincipal LoginedInfo loginedInfo) {
        Pageable pageable = PageRequest.of(page - 1, record);
        CourseMakerPagination<TravelCourse> travelCoursePage = courseService.findByTitleContaining(title, pageable);

        List<TravelCourseResponse> contents = new ArrayList<>();
        for (TravelCourse travelCourse : travelCoursePage.getContents()) {
            // 로그인 정보가 없으면 isMine을 false로 설정, 있으면 기존 로직대로 설정
            Boolean isMyCourse = loginedInfo != null && loginedInfo.getNickname().equals(travelCourse.getMember().getNickname());
            Boolean isMyWishCourse = loginedInfo != null && courseWishService.isCourseWishedByUser(travelCourse.getId(), loginedInfo.getNickname());
            Boolean isMyLikeCourse = loginedInfo != null && courseLikeService.isCourseLikedByUser(travelCourse.getId(), loginedInfo.getNickname());

            List<CourseDestinationResponse> courseDestinationResponses = courseDestinationService.getCourseDestinations(travelCourse)
                    .stream()
                    .map(courseDestination -> courseDestinationService.toResponse(courseDestination, loginedInfo))
                    .toList();

            List<TagResponseDto> tags = tagService.findAllByCourseId(travelCourse.getId());
            Double averageRating = courseReviewService.getAverageRating(travelCourse.getId());
            Integer reviewCount = courseReviewService.getReviewCount(travelCourse.getId());
            Integer wishCount = courseWishService.getCourseWishCount(travelCourse.getId());
            Integer likeCount = courseLikeService.getCourseLikeCount(travelCourse.getId());

            contents.add(new TravelCourseResponse(travelCourse, courseDestinationResponses, tags, isMyCourse, averageRating, reviewCount, wishCount, likeCount, isMyWishCourse, isMyLikeCourse));
        }

        Page<TravelCourseResponse> responsePage = new PageImpl<>(contents, pageable, travelCoursePage.getTotalPage());
        CourseMakerPagination<TravelCourseResponse> response = new CourseMakerPagination<>(pageable, responsePage, travelCoursePage.getTotalContents());

        return ResponseEntity.ok(response);
    }

    @Operation(summary = "닉네임으로 여행 코스 조회", description = "특정 닉네임의 사용자가 생성한 모든 여행 코스를 조회합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "404", description = "리소스를 찾을 수 없음", content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = ErrorResponse.class),
                    examples = @ExampleObject(
                            value = "{\"status\": 404, \"errorType\": \"Invalid item\", \"message\": \"해당하는 코스를 찾을 수 없습니다.\"}"
                    )
            ))
    })
    @Parameters({
            @Parameter(name = "nickname", description = "조회할 사용자의 닉네임"),
            @Parameter(name = "record", description = "한 페이지당 표시할 데이터 수"),
            @Parameter(name = "page", description = "조회할 페이지 번호(페이지는 1 페이지부터 시작합니다.)")
    })
    @GetMapping("/nickname/{nickname}")
    public ResponseEntity<CourseMakerPagination<TravelCourseResponse>> findCoursesByNickname(@PathVariable("nickname") String nickname,
                                                                                             @RequestParam(defaultValue = "20", name = "record") Integer record,
                                                                                             @RequestParam(defaultValue = "1", name = "page") Integer page,
                                                                                             @AuthenticationPrincipal LoginedInfo loginedInfo) {
        Pageable pageable = PageRequest.of(page - 1, record);
        CourseMakerPagination<TravelCourse> travelCoursePage = courseService.findByMemberNickname(nickname, pageable);

        List<TravelCourseResponse> contents = new ArrayList<>();
        for (TravelCourse travelCourse : travelCoursePage.getContents()) {
            // 로그인 정보가 없으면 isMine을 false로 설정, 있으면 기존 로직대로 설정
            Boolean isMyCourse = loginedInfo != null && loginedInfo.getNickname().equals(travelCourse.getMember().getNickname());
            Boolean isMyWishCourse = loginedInfo != null && courseWishService.isCourseWishedByUser(travelCourse.getId(), loginedInfo.getNickname());
            Boolean isMyLikeCourse = loginedInfo != null && courseLikeService.isCourseLikedByUser(travelCourse.getId(), loginedInfo.getNickname());

            List<CourseDestinationResponse> courseDestinationResponses = courseDestinationService.getCourseDestinations(travelCourse)
                    .stream()
                    .map(courseDestination -> courseDestinationService.toResponse(courseDestination, loginedInfo))
                    .toList();

            List<TagResponseDto> tags = tagService.findAllByCourseId(travelCourse.getId());
            Double averageRating = courseReviewService.getAverageRating(travelCourse.getId());
            Integer reviewCount = courseReviewService.getReviewCount(travelCourse.getId());
            Integer wishCount = courseWishService.getCourseWishCount(travelCourse.getId());
            Integer likeCount = courseLikeService.getCourseLikeCount(travelCourse.getId());

            contents.add(new TravelCourseResponse(travelCourse, courseDestinationResponses, tags, isMyCourse, averageRating, reviewCount, wishCount, likeCount, isMyWishCourse, isMyLikeCourse));
        }

        Page<TravelCourseResponse> responsePage = new PageImpl<>(contents, pageable, travelCoursePage.getTotalContents());
        CourseMakerPagination<TravelCourseResponse> response = new CourseMakerPagination<>(pageable, responsePage, travelCoursePage.getTotalContents());

        return ResponseEntity.ok(response);
    }

    // PUT
    /*********스웨거 어노테이션**********/
    @Operation(summary = "코스 수정", description = "유저가 등록한 코스를 수정합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "업데이트 성공"),
            @ApiResponse(responseCode = "400", description = "수정하려는 코스의 인자값이 올바르지 않을 때 반환합니다.", content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = ErrorResponse.class),
                    examples = @ExampleObject(
                            value = "{\"status\": 400, \"errorType\": \"Illegal argument\", \"message\": \"코스 이름은 공백 혹은 빈 문자는 허용하지 않습니다.\"}"
                    )
            )),
            @ApiResponse(responseCode = "401", description = "로그인 후 이용이 가능합니다.", content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = ErrorResponse.class),
                    examples = @ExampleObject(
                            value = "{\"status\": 401, \"errorType\": \"login required\", \"message\": \"로그인 후 이용이 가능합니다.\"}"
                    )
            )),
            @ApiResponse(responseCode = "403", description = "여행자 등급이 부족해서 코스를 수정하지 못할때.", content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = ErrorResponse.class),
                    examples = @ExampleObject(
                            value = "{\"status\": 403, \"errorType\": \"Access denied\", \"message\": \"여행자 등급이 부족해서 접근할 수 없습니다.\"}"
                    )
            )),
            @ApiResponse(responseCode = "403", description = "여행자 등급이 부족해서 코스를 삭제하지 못할때.", content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = ErrorResponse.class),
                    examples = @ExampleObject(
                            value = "{\"status\": 403, \"errorType\": \"Access denied\", \"message\": \"여행자 등급이 부족해서 접근할 수 없습니다.\"}"
                    )
            )),
            @ApiResponse(responseCode = "409", description = "수정하려는 코스의 이름이 이미 있을 때 반환합니다.", content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = ErrorResponse.class),
                    examples = @ExampleObject(
                            value = "{\"status\": 409, \"errorType\": \"Duplicated item\", \"message\": \"코스 이름이 이미 존재합니다.\"}"
                    )
            ))
    })
    @Parameters({
            @Parameter(name = "id", description = "수정할 코스의 id값 입니다.")
    })
    /*********스웨거 어노테이션**********/
    @PutMapping("/{id}")
    @PreAuthorize(value = "" +
            "hasAnyRole(" +
            "'ROLE_INTERMEDIATE_TRAVELER', " +
            "'ROLE_PRO_TRAVELER', " +
            "'ROLE_PARTNER', " +
            "'ROLE_ADMIN'" +
            ")"
    )
    public ResponseEntity<TravelCourseResponse> updateTravelCourse(@PathVariable("id") Long id,
                                                                   @Valid @RequestBody UpdateTravelCourseRequest request,
                                                                   @AuthenticationPrincipal LoginedInfo loginedInfo) {
        /*로그인 한 사용자 닉네임*/
        // 로그인한 사용자 닉네임을 설정, 로그인이 되어 있지 않으면 null
        String nickname = loginedInfo != null ? loginedInfo.getNickname() : null;

        // 로그인이 되어 있지 않으면 401 Unauthorized 응답을 반환
        if (nickname == null) {
            throw new UnAuthorizedException("login required", "로그인 후 이용이 가능합니다.");
        }

        request.setNickname(nickname);
        log.info("---------------------------------------------------id = {}", id);
        TravelCourse updatedTravelCourse = courseService.update(id, request, nickname);

        // 코스 작성자와 현재 로그인한 사용자가 동일한지 여부를 확인
        Boolean isMyCourse = nickname.equals(updatedTravelCourse.getMember().getNickname());
        Boolean isMyWishCourse = loginedInfo != null && courseWishService.isCourseWishedByUser(updatedTravelCourse.getId(), loginedInfo.getNickname());
        Boolean isMyLikeCourse = loginedInfo != null && courseLikeService.isCourseLikedByUser(updatedTravelCourse.getId(), loginedInfo.getNickname());

        /*TODO: ROW MAPPER로 DTO-entity 변환*/
        List<CourseDestinationResponse> courseDestinationResponses = courseDestinationService.getCourseDestinations(updatedTravelCourse)
                .stream()
                .map(courseDestination -> courseDestinationService.toResponse(courseDestination, loginedInfo))
                .toList();

        /*순환참조*/
//        List<TagResponseDto> tags = tagService.findAllByCourseId(updatedTravelCourse.getId())
//                .stream().map(Tag::toResponseDto).toList();
        List<TagResponseDto> tags = tagService.findAllByCourseId(updatedTravelCourse.getId());
        Double averageRating = courseReviewService.getAverageRating(updatedTravelCourse.getId());
        Integer reviewCount = courseReviewService.getReviewCount(updatedTravelCourse.getId());
        Integer wishCount = courseWishService.getCourseWishCount(updatedTravelCourse.getId());
        Integer likeCount = courseLikeService.getCourseLikeCount(updatedTravelCourse.getId());

        TravelCourseResponse response = new TravelCourseResponse(updatedTravelCourse, courseDestinationResponses, tags, isMyCourse, averageRating, reviewCount, wishCount, likeCount, isMyWishCourse, isMyLikeCourse);

        return (updatedTravelCourse != null) ?
                ResponseEntity.status(HttpStatus.OK).body(response) :
                ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
    }

    // DELETE
    /*********스웨거 어노테이션**********/
    @Operation(summary = "여행 코스 삭제", description = "ID를 사용하여 특정 여행 코스를 삭제합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "삭제 성공"),
            @ApiResponse(responseCode = "401", description = "로그인 후 이용이 가능합니다.", content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = ErrorResponse.class),
                    examples = @ExampleObject(
                            value = "{\"status\": 401, \"errorType\": \"login required\", \"message\": \"로그인 후 이용이 가능합니다.\"}"
                    )
            )),
            @ApiResponse(responseCode = "403", description = "해당 코스에 접근 권한이 없을 때 반환합니다.", content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = ErrorResponse.class),
                    examples = @ExampleObject(
                            value = "{\"status\": 403, \"errorType\": \"Forbidden\", \"message\": \"접근 권한이 없습니다.\"}"
                    )
            )),
            @ApiResponse(responseCode = "404", description = "삭제하려는 코스의 id를 찾지 못할 때 반환합니다.", content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = ErrorResponse.class),
                    examples = @ExampleObject(
                            value = "{\"status\": 404, \"errorType\": \"Invalid item\", \"message\": \"해당하는 코스를 찾을 수 없습니다.\"}"
                    )
            ))
    })
    @Parameters({
            @Parameter(name = "id", description = "삭제할 코스의 id값 입니다.")
    })
    /*********스웨거 어노테이션**********/
    @DeleteMapping("/{id}")
    @PreAuthorize(value = "" +
            "hasAnyRole(" +
            "'ROLE_INTERMEDIATE_TRAVELER', " +
            "'ROLE_PRO_TRAVELER', " +
            "'ROLE_PARTNER', " +
            "'ROLE_ADMIN'" +
            ")"
    )
    public ResponseEntity<Void> deleteTravelCourse(@PathVariable("id") Long id,
                                                   @AuthenticationPrincipal LoginedInfo loginedInfo) {
        // 로그인한 사용자 닉네임을 설정, 로그인이 되어 있지 않으면 null
        String nickname = loginedInfo != null ? loginedInfo.getNickname() : null;

        // 로그인이 되어 있지 않으면 401 Unauthorized 응답을 반환
        if (nickname == null) {
            throw new UnAuthorizedException("login required", "로그인 후 이용이 가능합니다.");
        }
        courseService.delete(id, nickname);

        return ResponseEntity.ok()
                .build();
    }
}