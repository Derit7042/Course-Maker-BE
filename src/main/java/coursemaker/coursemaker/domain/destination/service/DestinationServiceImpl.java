package coursemaker.coursemaker.domain.destination.service;

import coursemaker.coursemaker.domain.destination.dto.LocationDto;
import coursemaker.coursemaker.domain.destination.dto.RequestDto;
import coursemaker.coursemaker.domain.destination.entity.Destination;
import coursemaker.coursemaker.domain.destination.exception.DestinationDuplicatedException;
import coursemaker.coursemaker.domain.destination.exception.DestinationNotFoundException;
import coursemaker.coursemaker.domain.destination.exception.PictureNotFoundException;
import coursemaker.coursemaker.domain.destination.repository.DestinationRepository;
import coursemaker.coursemaker.domain.member.entity.Member;
import coursemaker.coursemaker.domain.member.service.MemberService;
import coursemaker.coursemaker.domain.review.service.DestinationReviewService;
import coursemaker.coursemaker.domain.tag.dto.TagResponseDto;
import coursemaker.coursemaker.domain.tag.service.OrderBy;
import coursemaker.coursemaker.domain.tag.service.TagService;
import coursemaker.coursemaker.exception.ErrorCode;
import coursemaker.coursemaker.util.CourseMakerPagination;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
public class DestinationServiceImpl implements DestinationService {
    private final DestinationRepository destinationRepository;
    private final TagService tagService;
    private final MemberService memberService;
    private final DestinationReviewService destinationReviewService;

    @Autowired
    public DestinationServiceImpl(DestinationRepository destinationRepository, @Lazy TagService tagService, MemberService memberService, @Lazy DestinationReviewService destinationReviewService) {
        this.destinationRepository = destinationRepository;
        this.tagService = tagService;
        this.memberService = memberService;
        this.destinationReviewService = destinationReviewService;
    }

    @Transactional
    @Override
    public Destination save(@Valid RequestDto requestDto, boolean isApiData) {
        log.info("[Destination] 여행지 저장 시작 - 이름: {}, 저장한 사람: {}", requestDto.getName(), requestDto.getNickname());
        Member member = memberService.findByNickname(requestDto.getNickname());

        if (destinationRepository.existsByNameAndDeletedAtIsNull(requestDto.getName())) {
            log.error("[Destination] 여행지 중복 오류 - 이름: {}, 저장한 사람: {}", requestDto.getName(), requestDto.getNickname());
            throw new DestinationDuplicatedException("여행지 이름이 이미 존재합니다.", "Destination name: " + requestDto.getName());
        }

        if (isApiData) {
            if (requestDto.getIsApiData() == null || !requestDto.getIsApiData()) {
                throw new IllegalArgumentException("API에서 apiContent가 필요합니다.");
            }
        }

        Destination destination = requestDto.toEntity(member);
        destination.setAverageRating(requestDto.getAverageRating() != null ? requestDto.getAverageRating() : 0.0);
        log.debug("[Destination] 여행지 평균 평점 설정 - 평점: {}", destination.getAverageRating());

        Destination savedDestination = destinationRepository.save(destination);
        log.info("[Destination] 여행지 저장 완료 - ID: {}, 이름: {}, 저장한 사람: {}", savedDestination.getId(), savedDestination.getName(), requestDto.getNickname());

        tagService.addTagsByDestination(savedDestination.getId(), getTagIds(requestDto));

        return savedDestination;
    }

    @Transactional
    @Override
    public Destination update(Long id, @Valid RequestDto requestDto, boolean isApiData) {
        log.info("[Destination] 여행지 업데이트 시작 - ID: {}, 업데이트한 사람: {}", id, requestDto.getNickname());

        Destination destination = destinationRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> {
                    log.error("[Destination] 여행지 찾기 실패 - ID: {}", id);
                    return new DestinationNotFoundException("해당하는 여행지가 없습니다.", "Destination id: " + id);
                });

        if (destinationRepository.existsByNameAndIdNotAndDeletedAtIsNull(requestDto.getName(), id)) {
            log.error("[Destination] 여행지 이름 중복 오류 - 이름: {}, 업데이트한 사람: {}", requestDto.getName(), requestDto.getNickname());
            throw new DestinationDuplicatedException("여행지 이름이 이미 존재합니다.", "Destination name: " + requestDto.getName());
        }

        if (isApiData) {
            if (requestDto.getIsApiData() == null || !requestDto.getIsApiData()) {
                throw new IllegalArgumentException("API에서 apiContent가 필요합니다.");
            }
        }

        destination.setName(requestDto.getName());
        destination.setPictureLink(requestDto.getPictureLink());
        destination.setContent(requestDto.getContent());
        destination.setLocation(requestDto.getLocation().getAddress());
        destination.setLongitude(requestDto.getLocation().getLongitude());
        destination.setLatitude(requestDto.getLocation().getLatitude());

        log.debug("[Destination] 여행지 업데이트 - 이름: {}, 업데이트한 사람: {}", requestDto.getName(), requestDto.getNickname());

        tagService.deleteAllTagByDestination(destination.getId());
        tagService.addTagsByDestination(destination.getId(), getTagIds(requestDto));

        log.info("[Destination] 여행지 업데이트 완료 - ID: {}, 업데이트한 사람: {}", destination.getId(), requestDto.getNickname());

        return destinationRepository.save(destination);
    }

    // 통합 예외 처리

    // 1. RequestDto 객체에서 태그 ID를 추출합니다.
    // 2. 중복된 태그 ID를 제거합니다.
    // 3. 태그 ID를 List<Long> 형태로 반환합니다.
    private List<Long> getTagIds(RequestDto requestDto) {
        return requestDto.getTags().stream()
                .map(TagResponseDto::getId)
                .distinct()
                .toList();
    }
    @Override
    public Destination findById(Long id) {
        log.info("[Destination] 여행지 조회 시작 - ID: {}", id);

        Destination destination = destinationRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> {
                    log.error("[Destination] 여행지 찾기 실패 - ID: {}", id);
                    return new DestinationNotFoundException("해당하는 여행지가 없습니다.", "Destination id: " + id);
                });

        incrementViews(destination.getId());
        log.info("[Destination] 여행지 조회 완료 - ID: {}, 이름: {}", destination.getId(), destination.getName());

        return destination;
    }

    @Override
    public List<Destination> findAll() {
        log.info("[Destination] 모든 여행지 조회");
        return destinationRepository.findAllByDeletedAtIsNull();
    }

    @Override
    public CourseMakerPagination<Destination> findByNameContaining(String name, Pageable pageable) {
        log.info("[Destination] 여행지 이름으로 검색 - 이름: {}", name);
        Page<Destination> page = destinationRepository.findByNameContainingAndDeletedAtIsNull(name, pageable);
        long total = tagService.findAllDestinationByTagIds(null, pageable, OrderBy.NEWEST).getTotalContents();
        return new CourseMakerPagination<>(pageable, page, total);
    }

    @Override
    public CourseMakerPagination<Destination> findByMemberNickname(String nickname, Pageable pageable) {
        log.info("[Destination] 멤버 닉네임으로 여행지 조회 - 닉네임: {}", nickname);
        Page<Destination> page = destinationRepository.findByMemberNicknameAndDeletedAtIsNull(nickname, pageable);
        long total = page.getTotalElements();
        return new CourseMakerPagination<>(pageable, page, total);
    }

    @Override
    public CourseMakerPagination<Destination> findAll(Pageable pageable) {
        log.info("[Destination] 모든 여행지 페이지 조회");
        Page<Destination> page = destinationRepository.findAllByDeletedAtIsNull(pageable);
        long total = tagService.findAllDestinationByTagIds(null, pageable, OrderBy.NEWEST).getTotalContents();
        return new CourseMakerPagination<>(pageable, page, total);
    }

    @Transactional
    @Override
    public void deleteById(Long id) {
        log.info("[Destination] 여행지 삭제 - ID: {}", id);
        Destination destination = destinationRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> {
                    log.error("[Destination] 여행지 찾기 실패 - ID: {}", id);
                    return new DestinationNotFoundException("해당하는 여행지가 없습니다.", "Destination id: " + id);
                });
        destination.softDelete();
        destinationRepository.save(destination);
        log.info("[Destination] 여행지 삭제 완료 - ID: {}", id);
    }

    @Transactional
    @Override
    public void incrementViews(Long destinationId) {
        log.info("[Destination] 여행지 조회수 증가 - ID: {}", destinationId);
        Destination destination = destinationRepository.findByIdAndDeletedAtIsNull(destinationId)
                .orElseThrow(() -> {
                    log.error("[Destination] 여행지 찾기 실패 - ID: {}", destinationId);
                    return new DestinationNotFoundException("해당하는 여행지가 없습니다.", "Destination id: " + destinationId);
                });

        destination.incrementViews();
        destinationRepository.save(destination);
        log.info("[Destination] 여행지 조회수 증가 완료 - ID: {}, 조회수: {}", destination.getId(), destination.getViews());
    }

    @Transactional
    @Override
    public void addPictureLink(Long destinationId, String pictureLink) {
        log.info("[Destination] 여행지 대표사진 추가 - ID: {}", destinationId);
        Destination destination = destinationRepository.findByIdAndDeletedAtIsNull(destinationId)
                .orElseThrow(() -> {
                    log.error("[Destination] 여행지 찾기 실패 - ID: {}", destinationId);
                    return new DestinationNotFoundException("해당하는 여행지를 찾을수 없습니다: " + destinationId, "Destination id: " + destinationId);
                });
        destination.setPictureLink(pictureLink);
        destinationRepository.save(destination);
        log.info("[Destination] 여행지 대표사진 추가 완료 - ID: {}, 링크: {}", destinationId, pictureLink);
    }

    @Override
    public String getPictureLink(Long destinationId) {
        log.info("[Destination] 여행지 대표사진 조회 - ID: {}", destinationId);
        Destination destination = destinationRepository.findByIdAndDeletedAtIsNull(destinationId)
                .orElseThrow(() -> {
                    log.error("[Destination] 여행지 찾기 실패 - ID: {}", destinationId);
                    return new DestinationNotFoundException("해당하는 여행지를 찾을수 없습니다: " + destinationId, "Destination id: " + destinationId);
                });
        String pictureLink = destination.getPictureLink();
        if (pictureLink.isEmpty()) {
            log.error("[Destination] 대표사진 없음 - ID: {}", destinationId);
            throw new PictureNotFoundException(ErrorCode.ILLEGAL_DESTINATION_ARGUMENT, "Destination id: " + destinationId);
        }
        log.info("[Destination] 여행지 대표사진 조회 완료 - ID: {}, 링크: {}", destinationId, pictureLink);
        return pictureLink;
    }

    @Transactional
    @Override
    public void updatePictureLink(Long destinationId, String newPictureLink) {
        log.info("[Destination] 여행지 대표사진 업데이트 - ID: {}", destinationId);
        Destination destination = destinationRepository.findByIdAndDeletedAtIsNull(destinationId)
                .orElseThrow(() -> {
                    log.error("[Destination] 여행지 찾기 실패 - ID: {}", destinationId);
                    return new DestinationNotFoundException("해당하는 여행지를 찾을수 없습니다: " + destinationId, "Destination id: " + destinationId);
                });
        destination.setPictureLink(newPictureLink);
        destinationRepository.save(destination);
        log.info("[Destination] 여행지 대표사진 업데이트 완료 - ID: {}, 새로운 링크: {}", destinationId, newPictureLink);
    }

    @Transactional
    @Override
    public void deletePictureLink(Long destinationId) {
        log.info("[Destination] 여행지 대표사진 삭제 - ID: {}", destinationId);
        Destination destination = destinationRepository.findByIdAndDeletedAtIsNull(destinationId)
                .orElseThrow(() -> {
                    log.error("[Destination] 여행지 찾기 실패 - ID: {}", destinationId);
                    return new DestinationNotFoundException("해당하는 여행지를 찾을수 없습니다: " + destinationId, "Destination id: " + destinationId);
                });
        if (destination.getPictureLink().isEmpty()) {
            log.error("[Destination] 대표사진 없음 - ID: {}", destinationId);
            throw new PictureNotFoundException(ErrorCode.ILLEGAL_DESTINATION_ARGUMENT, "Destination id: " + destinationId);
        }
        destination.setPictureLink(null);
        destinationRepository.save(destination);
        log.info("[Destination] 여행지 대표사진 삭제 완료 - ID: {}", destinationId);
    }

    @Transactional
    @Override
    public Destination getLocation(Long destinationId, LocationDto locationDto) {
        log.info("[Destination] 여행지 위치 업데이트 - ID: {}", destinationId);
        Destination destination = destinationRepository.findByIdAndDeletedAtIsNull(destinationId)
                .orElseThrow(() -> {
                    log.error("[Destination] 여행지 찾기 실패 - ID: {}", destinationId);
                    return new DestinationNotFoundException("해당하는 여행지를 찾을 수 없습니다: " + destinationId, "Destination id: " + destinationId);
                });

        destination.setLocation(locationDto.getAddress());
        destination.setLatitude(locationDto.getLatitude());
        destination.setLongitude(locationDto.getLongitude());
        log.info("[Destination] 여행지 위치 업데이트 완료 - ID: {}, 주소: {}", destinationId, locationDto.getAddress());

        return destinationRepository.save(destination);
    }

    @Override
    public Double getAverageRating(Long destinationId) {
        log.info("[Destination] 여행지 평균 평점 조회 - ID: {}", destinationId);
        Double averageRating = destinationReviewService.getAverageRating(destinationId);
        log.info("[Destination] 여행지 평균 평점 조회 완료 - ID: {}, 평점: {}", destinationId, averageRating);
        return averageRating;
    }
}