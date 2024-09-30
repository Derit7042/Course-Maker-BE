package coursemaker.coursemaker.domain.destination.service;

import coursemaker.coursemaker.domain.destination.dto.DestinationDto;
import coursemaker.coursemaker.domain.destination.dto.LocationDto;
import coursemaker.coursemaker.domain.destination.dto.RequestDto;
import coursemaker.coursemaker.domain.destination.entity.Destination;
import coursemaker.coursemaker.domain.wish.entity.DestinationWish;
import coursemaker.coursemaker.util.CourseMakerPagination;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface DestinationService {
    // 여행지를 저장하는 메서드
    Destination save(RequestDto requestDto, boolean isApiData);

    // 여행지를 업데이트하는 메서드
    Destination update(Long id, RequestDto requestDto, boolean isApiData);

    // id 기반으로 특정 여행지를 조회하는 메서드
    Destination findById(Long id);

    // 모든 여행지를 조회하는 메서드
    List<Destination> findAll();

    // 제목으로 검색하는 메서드
    CourseMakerPagination<Destination> findByNameContaining(String name, Pageable pageable);

    // 닉네임으로 검색하는 메서드
    CourseMakerPagination<Destination> findByMemberNickname(String nickname, Pageable pageable);

    // 페이징을 지원하는 모든 여행지 조회 메서드
    CourseMakerPagination<Destination> findAll(Pageable pageable);

    // id 기반으로 여행지를 삭제하는 메서드
    void deleteById(Long id);

    // 조회수 증가 메서드 정의
    void incrementViews(Long destinationId);

    // 여행지 id에 대한 대표사진을 추가하는 메서드
    void addPictureLink(Long destinationId, String pictureLink);

    // 여행지 id로 여행지의 대표사진 URL을 조회하는 메서드
    String getPictureLink(Long destinationId);

    // 기존 여행지의 대표사진 URL을 새 URL로 변경하는 메서드
    void updatePictureLink(Long destinationId, String newPictureLink);

    // 특정 여행지의 대표사진을 삭제하는 메서드
    void deletePictureLink(Long destinationId);

    // 위치 정보 메서드
    Destination getLocation(Long destinationId, LocationDto locationDto);

    Double getAverageRating(Long destinationId);

}