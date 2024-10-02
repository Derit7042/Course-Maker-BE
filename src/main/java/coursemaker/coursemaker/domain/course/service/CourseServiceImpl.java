package coursemaker.coursemaker.domain.course.service;

import coursemaker.coursemaker.domain.course.dto.AddCourseDestinationRequest;
import coursemaker.coursemaker.domain.course.dto.AddTravelCourseRequest;
import coursemaker.coursemaker.domain.course.dto.UpdateCourseDestinationRequest;
import coursemaker.coursemaker.domain.course.dto.UpdateTravelCourseRequest;
import coursemaker.coursemaker.domain.course.entity.CourseDestination;
import coursemaker.coursemaker.domain.course.entity.TravelCourse;
import coursemaker.coursemaker.domain.course.exception.CourseForbiddenException;
import coursemaker.coursemaker.domain.course.exception.IllegalTravelCourseArgumentException;
import coursemaker.coursemaker.domain.course.exception.TravelCourseAlreadyDeletedException;
import coursemaker.coursemaker.domain.course.exception.TravelCourseNotFoundException;
import coursemaker.coursemaker.domain.course.repository.CourseDestinationRepository;
import coursemaker.coursemaker.domain.course.repository.TravelCourseRepository;
import coursemaker.coursemaker.domain.destination.entity.Destination;
import coursemaker.coursemaker.domain.destination.exception.PictureNotFoundException;
import coursemaker.coursemaker.domain.destination.service.DestinationService;
import coursemaker.coursemaker.domain.member.entity.Member;
import coursemaker.coursemaker.domain.member.service.MemberService;
import coursemaker.coursemaker.domain.tag.dto.TagResponseDto;
import coursemaker.coursemaker.domain.tag.service.OrderBy;
import coursemaker.coursemaker.domain.tag.service.TagService;
import coursemaker.coursemaker.exception.ErrorCode;
import coursemaker.coursemaker.util.CourseMakerPagination;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional
@Slf4j
public class CourseServiceImpl implements CourseService{

    private final CourseDestinationRepository courseDestinationRepository;

    private final TravelCourseRepository travelCourseRepository;

    private final TagService tagService;

    private final DestinationService destinationService;

    private final MemberService memberService;

    public CourseServiceImpl(CourseDestinationRepository courseDestinationRepository,
                             TravelCourseRepository travelCourseRepository,
                             @Lazy TagService tagService,
                             DestinationService destinationService,
                             MemberService memberService) {
        this.courseDestinationRepository = courseDestinationRepository;
        this.travelCourseRepository = travelCourseRepository;
        this.tagService = tagService;
        this.destinationService = destinationService;
        this.memberService = memberService;
    }

    @Override
    public TravelCourse save(AddTravelCourseRequest request) {
        log.info("[Course] 새로운 여행 코스 저장 요청: {}", request);

        if (request.getTitle().length() > 50) {
            log.warn("[Course] 코스 제목 길이 초과: {}", request.getTitle().length());
            throw new IllegalTravelCourseArgumentException("코스 제목은 50자를 넘길 수 없습니다.", "title's length is over 50");
        }

        // TODO: ROW MAPPER로 엔티티 - DTO 매핑
        /***************DTO - entity 변환**************/

        /*travel course 설정*/
        Member member = memberService.findByNickname(request.getNickname());
        log.debug("[Course] 멤버 찾기 결과: {}", member);

        TravelCourse travelCourse = TravelCourse.builder()
                .title(request.getTitle())
                .content(request.getContent())
                .duration(request.getDuration())
                .travelerCount(request.getTravelerCount())
                .travelType(request.getTravelType())
                .pictureLink(request.getPictureLink())
                .member(member)
                .averageRating(request.getAverageRating() != null ? request.getAverageRating() : 0)
                .wishCount(0)
                .likeCount(0)
                .reviewCount(0)
                .build();

        travelCourse = travelCourseRepository.save(travelCourse);
        log.info("[Course] 여행 코스 저장 완료: {}", travelCourse);

        /*destination 설정*/
        for (AddCourseDestinationRequest courseDestination : request.getCourseDestinations()) {

            CourseDestination courseDestinationEntity = CourseDestination.builder()
                    .date(courseDestination.getDate())
                    .visitOrder(courseDestination.getVisitOrder())
                    .build();
            courseDestinationEntity.setTravelCourse(travelCourse);

            Destination destination = destinationService.findById(courseDestination.getDestination().getId());
            log.debug("[Course] 목적지 찾기 결과: {}", destination);

            courseDestinationEntity.setDestination(destination);
            courseDestinationRepository.save(courseDestinationEntity);
        }


        /*태그 설정*/
        List<Long> tagIds = request.getTags().stream()
                .map(TagResponseDto::getId)
                .collect(Collectors.toList());
        tagService.addTagsByCourse(travelCourse.getId(), tagIds);
        log.info("[Course] 태그 설정 완료: {}", tagIds);

        return travelCourse;
    }

    @Override
    public CourseMakerPagination<TravelCourse> findAll(Pageable pageable) {
        log.debug("[Course] 모든 여행 코스 조회 요청: pageable={}", pageable);
        Page<TravelCourse> page = travelCourseRepository.findAllByDeletedAtIsNull(pageable);// db에서 페이지 단위로 가져옴
        long total = tagService.findAllCourseByTagIds(null, pageable, OrderBy.NEWEST).getTotalContents();
        CourseMakerPagination<TravelCourse> courseMakerPagination = new CourseMakerPagination<>(pageable, page, total);// 페이지네이션 객체 변환
        log.debug("[Course] 모든 여행 코스 조회 완료: total={}", total);
        return courseMakerPagination;
    }

    @Override
    public CourseMakerPagination<TravelCourse> getAllOrderByViewsDesc(Pageable pageable) {
        log.debug("[Course] 조회수 기준 모든 여행 코스 조회 요청: pageable={}", pageable);
        Page<TravelCourse> page = travelCourseRepository.findAllByDeletedAtIsNullOrderByViewsDesc(pageable);// db에서 페이지 단위로 가져옴
        long total = tagService.findAllCourseByTagIds(null, pageable, OrderBy.NEWEST).getTotalContents();
        CourseMakerPagination<TravelCourse> courseMakerPagination = new CourseMakerPagination<>(pageable, page, total);// 페이지네이션 객체 변환
        log.debug("[Course] 조회수 기준 모든 여행 코스 조회 완료: total={}", total);
        return courseMakerPagination;
    }

    @Override
    public TravelCourse findById(Long id) {
        log.debug("[Course] ID로 여행 코스 조회 요청: ID={}", id);
        TravelCourse travelCourse = travelCourseRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new TravelCourseNotFoundException("존재하지 않는 코스입니다.", "Course ID: " + id));
        log.debug("[Course] ID로 여행 코스 조회 완료: {}", travelCourse);
        return travelCourse;
    }

    @Override
    public CourseMakerPagination<TravelCourse> findByTitleContaining(String title, Pageable pageable) {
        log.debug("[Course] 제목 포함 여행 코스 조회 요청: title={}, pageable={}", title, pageable);
        Page<TravelCourse> page = travelCourseRepository.findByTitleContainingAndDeletedAtIsNull(title, pageable);
        long total = tagService.findAllCourseByTagIds(null, pageable, OrderBy.NEWEST).getTotalContents();
        CourseMakerPagination<TravelCourse> courseMakerPagination = new CourseMakerPagination<>(pageable, page, total);
        log.debug("[Course] 제목 포함 여행 코스 조회 완료: total={}", total);
        return courseMakerPagination;
    }

    @Override
    public CourseMakerPagination<TravelCourse> findByMemberNickname(String nickname, Pageable pageable) {
        log.debug("[Course] 멤버 닉네임으로 여행 코스 조회 요청: nickname={}, pageable={}", nickname, pageable);
        Page<TravelCourse> page = travelCourseRepository.findByMemberNicknameAndDeletedAtIsNull(nickname, pageable);
        long total = page.getTotalElements();
        log.debug("[Course] 멤버 닉네임으로 여행 코스 조회 완료: total={}", total);
        return new CourseMakerPagination<>(pageable, page, total);
    }

    @Override
    public TravelCourse update(Long id, UpdateTravelCourseRequest request, String nickname) {
        log.info("[Course] 여행 코스 업데이트 요청: ID={}, 닉네임={}", id, nickname);

        String existingCourseNickname = travelCourseRepository.findByIdAndDeletedAtIsNull(id).get().getMember().getNickname();
        if (!existingCourseNickname.equals(nickname)) {
            log.error("[Course] 권한이 없는 사용자 접근 시도: ID={}, 닉네임={}", id, nickname);
            throw new CourseForbiddenException("사용자가 해당 코스에 접근할 권한이 없습니다.", "Course Forbidden");
        }

        TravelCourse travelCourse = travelCourseRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> {
                    log.error("[Course] 존재하지 않는 코스 수정 시도: ID={}", id);
                    return new TravelCourseNotFoundException("수정할 코스가 존재하지 않습니다.", "course ID: " + id);
                });

        if (request.getTitle().length() > 50) {
            log.warn("[Course] 코스 제목 길이 초과: {}", request.getTitle().length());
            throw new IllegalTravelCourseArgumentException("코스 제목은 50자를 넘길 수 없습니다.", "title's length is over 50");
        }

        /*travel course 설정*/
        Member member = memberService.findByNickname(request.getNickname());
        log.debug("[Course] 멤버 찾기 결과: {}", member);

        /**코스 객체 기본정보 업데이트*/
        travelCourse.setTitle(request.getTitle());
        travelCourse.setContent(request.getContent());
        travelCourse.setDuration(request.getDuration());
        travelCourse.setTravelerCount(request.getTravelerCount());
        travelCourse.setTravelType(request.getTravelType());
        travelCourse.setPictureLink(request.getPictureLink());
        log.debug("[Course] 코스 객체 기본정보 업데이트 성공: {}", travelCourse);


        /**course destination 업데이트*/
        /*기존 여행지 초기화*/
        courseDestinationRepository.deleteAllByTravelCourseId(id);// 여행지 초기화
        log.debug("[Course] 기존 목적지 삭제 완료");

        /*dto를 기반으로 여행지 재설정*/
        for (UpdateCourseDestinationRequest courseDestination : request.getCourseDestinations()) {

            CourseDestination courseDestinationEntity = CourseDestination.builder()
                    .date(courseDestination.getDate())
                    .visitOrder(courseDestination.getVisitOrder())
                    .build();
            courseDestinationEntity.setTravelCourse(travelCourse);

            Destination destination = destinationService.findById(courseDestination.getDestination().getId());
            log.debug("[Course] 목적지 찾기 결과: {}", destination);

            courseDestinationEntity.setDestination(destination);
            courseDestinationRepository.save(courseDestinationEntity);
        }
        log.info("[Course] 목적지 업데이트 완료");


        /**태그 업데이트*/

        /*기존 코스에 있는 태그 초기화*/
        tagService.deleteAllTagByCourse(id);
        log.debug("[Course] 기존 태그 삭제 완료");

        /*dto를 기반으로 태그 재설정*/
        List<Long> tagIds = request.getTags().stream()
                .map(TagResponseDto::getId)
                .collect(Collectors.toList());
        tagService.addTagsByCourse(id, tagIds);
        log.info("[Course] 태그 업데이트 완료: {}", tagIds);

        return travelCourseRepository.save(travelCourse);
    }

    @Override
    public void delete(Long id, String nickname) {
        log.info("[Course] 여행 코스 삭제 요청: ID={}, 닉네임={}", id, nickname);

        String existingCourseNickname = travelCourseRepository.findByIdAndDeletedAtIsNull(id).get().getMember().getNickname();
        if (!existingCourseNickname.equals(nickname)) {
            log.error("[Course] 권한이 없는 사용자 접근 시도: ID={}, 닉네임={}", id, nickname);
            throw new CourseForbiddenException("사용자가 해당 코스에 접근할 권한이 없습니다.", "Course Forbidden");
        }

        TravelCourse travelCourse = travelCourseRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> {
                    log.error("[Course] 존재하지 않는 코스 삭제 시도: ID={}", id);
                    return new TravelCourseNotFoundException("삭제할 코스가 존재하지 않습니다.", "Course ID: " + id);
                });

        if (travelCourse.getDeletedAt() != null) {
            log.warn("[Course] 이미 삭제된 코스 삭제 시도: ID={}", id);
            throw new TravelCourseAlreadyDeletedException("해당 코스는 이미 삭제되었습니다.", "Course ID: " + id);
        }

        travelCourse.setDeletedAt(LocalDateTime.now());
        travelCourseRepository.save(travelCourse);
        log.info("[Course] 여행 코스 삭제 완료: ID={}", id);
    }

    @Override
    public TravelCourse incrementViews(Long id) {
        log.debug("[Course] 여행 코스 조회수 증가 요청: ID={}", id);
        TravelCourse travelCourse = travelCourseRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> {
                    log.error("[Course] 존재하지 않는 코스 조회 시도: ID={}", id);
                    return new TravelCourseNotFoundException("코스가 존재하지 않습니다.", "Course ID: " + id);
                });
        travelCourse.incrementViews();
        log.debug("[Course] 여행 코스 조회수 증가 완료: ID={}, 조회수={}", id, travelCourse.getViews());
        return travelCourseRepository.save(travelCourse);
    }

    @Override
    public void addPictureLink(Long courseId, String pictureLink) {
        log.info("[Course] 여행 코스에 대표사진 링크 추가 요청: courseId={}, pictureLink={}", courseId, pictureLink);
        TravelCourse travelCourse = travelCourseRepository.findByIdAndDeletedAtIsNull(courseId)
                .orElseThrow(() -> {
                    log.error("[Course] 존재하지 않는 코스에 대표사진 링크 추가 시도: courseId={}", courseId);
                    return new TravelCourseNotFoundException("해당하는 코스를 찾을수 없습니다: " + courseId, "Course id: " + courseId);
                });
        travelCourse.setPictureLink(pictureLink);
        travelCourseRepository.save(travelCourse);
        log.info("[Course] 대표사진 링크 추가 완료: courseId={}", courseId);
    }

    @Override
    public String getPictureLink(Long courseId) {
        log.debug("[Course] 코스의 대표사진 URL 조회 요청: courseId={}", courseId);
        TravelCourse travelCourse = travelCourseRepository.findByIdAndDeletedAtIsNull(courseId)
                .orElseThrow(() -> {
                    log.error("[Course] 존재하지 않는 코스의 대표사진 URL 조회 시도: courseId={}", courseId);
                    return new TravelCourseNotFoundException("해당하는 코스를 찾을수 없습니다: " + courseId, "Course id: " + courseId);
                });
        String pictureLink = travelCourse.getPictureLink();
        if (pictureLink.isEmpty()) {
            log.error("[Course] 대표사진이 없는 코스의 대표사진 URL 조회 시도: courseId={}", courseId);
            throw new PictureNotFoundException(ErrorCode.PICTURE_NOT_FOUND, "Course id: " + courseId);
        }
        log.debug("[Course] 코스의 대표사진 URL 조회 완료: courseId={}, pictureLink={}", courseId, pictureLink);
        return pictureLink;
    }

    @Override
    public void updatePictureLink(Long courseId, String newPictureLink) {
        log.info("[Course] 코스의 대표사진 URL 변경 요청: courseId={}, newPictureLink={}", courseId, newPictureLink);
        TravelCourse travelCourse = travelCourseRepository.findByIdAndDeletedAtIsNull(courseId)
                .orElseThrow(() -> {
                    log.error("[Course] 존재하지 않는 코스의 대표사진 URL 변경 시도: courseId={}", courseId);
                    return new TravelCourseNotFoundException("해당하는 코스를 찾을수 없습니다: " + courseId, "Course id: " + courseId);
                });
        travelCourse.setPictureLink(newPictureLink);
        travelCourseRepository.save(travelCourse);
        log.info("[Course] 코스의 대표사진 URL 변경 완료: courseId={}", courseId);
    }

    @Override
    public void deletePictureLink(Long courseId) {
        log.info("[Course] 코스의 대표사진 링크 삭제 요청: courseId={}", courseId);
        TravelCourse travelCourse = travelCourseRepository.findByIdAndDeletedAtIsNull(courseId)
                .orElseThrow(() -> {
                    log.error("[Course] 존재하지 않는 코스의 대표사진 링크 삭제 시도: courseId={}", courseId);
                    return new TravelCourseNotFoundException("해당하는 코스를 찾을수 없습니다: " + courseId, "Course id: " + courseId);
                });
        if (travelCourse.getPictureLink().isEmpty()) {
            log.error("[Course] 대표사진이 없는 코스의 대표사진 링크 삭제 시도: courseId={}", courseId);
            throw new PictureNotFoundException(ErrorCode.PICTURE_NOT_FOUND, "Course id: " + courseId);
        }
        travelCourse.setPictureLink(null);
        travelCourseRepository.save(travelCourse);
        log.info("[Course] 코스의 대표사진 링크 삭제 완료: courseId={}", courseId);
    }
}