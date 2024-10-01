package coursemaker.coursemaker.domain.tag.service;

import com.querydsl.core.types.Order;
import com.querydsl.core.types.OrderSpecifier;
import coursemaker.coursemaker.domain.course.entity.TravelCourse;
import coursemaker.coursemaker.domain.course.service.CourseService;
import coursemaker.coursemaker.domain.destination.entity.Destination;
import coursemaker.coursemaker.domain.destination.service.DestinationService;
import coursemaker.coursemaker.domain.tag.dto.TagPostDto;
import coursemaker.coursemaker.domain.tag.dto.TagResponseDto;
import coursemaker.coursemaker.domain.tag.dto.TagUpdateDto;
import coursemaker.coursemaker.domain.tag.entity.CourseTag;
import coursemaker.coursemaker.domain.tag.entity.DestinationTag;
import coursemaker.coursemaker.domain.tag.entity.Tag;
import coursemaker.coursemaker.domain.tag.exception.IllegalTagArgumentException;
import coursemaker.coursemaker.domain.tag.exception.TagDuplicatedException;
import coursemaker.coursemaker.domain.tag.exception.TagNotFoundException;
import coursemaker.coursemaker.domain.tag.repository.CourseTagRepository;
import coursemaker.coursemaker.domain.tag.repository.DestinationTagRepository;
import coursemaker.coursemaker.domain.tag.repository.TagRepository;
import coursemaker.coursemaker.util.CourseMakerPagination;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;
import com.querydsl.jpa.impl.JPAQueryFactory;


import static coursemaker.coursemaker.domain.course.entity.QTravelCourse.travelCourse;
import static coursemaker.coursemaker.domain.destination.entity.QDestination.destination;
import static coursemaker.coursemaker.domain.tag.entity.QCourseTag.courseTag;
import static coursemaker.coursemaker.domain.tag.entity.QDestinationTag.destinationTag;

// TODO: 삭제 연산 soft delete로 전환
@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class TagServiceImpl implements TagService{

    private final CourseTagRepository courseTagRepository;
    private final TagRepository tagRepository;
    private final DestinationTagRepository destinationTagRepository;

    private final CourseService courseService;
    private final DestinationService destinationService;

    private final JPAQueryFactory queryFactory;

    /*****태그 기본 CRUD*****/
    @Override
    public TagResponseDto createTag(TagPostDto tag){

        /*태그의 이름은 고유해야 한다.*/
        if(tagRepository.findByname(tag.getName()).isPresent()) {
            throw new TagDuplicatedException("이미 존재하는 태그입니다.", "tag name: " + tag.getName() );
        }

        Tag created = tag.toEntity();

        created = tagRepository.save(created);

        log.info("[Tag] 태그 추가. id: {}", created.getId());

        TagResponseDto response = created.toResponseDto();

        return response;
    }

    @Override
    public TagResponseDto findById(Long id){
        log.debug("[Tag] 태그 조회. id: {}", id);

        return tagRepository.findById(id)
                .orElseThrow(() -> new TagNotFoundException("해당 태그가 존재하지 않습니다.", "tag id: " + id ))
                .toResponseDto();
    }

    @Override
    public TagResponseDto findByTagName(String name){
        log.debug("[Tag] 태그 조회. 태그명: {}", name);

        return tagRepository.findByname(name)
                .orElseThrow(() -> new TagNotFoundException("해당 태그가 존재하지 않습니다.", "tag name: " + name ))
                .toResponseDto();
    }

    @Override
    public List<TagResponseDto> findAllTags(){
        log.debug("[Tag] 전체 태그 조회");

        return tagRepository.findAll()
                .stream()
                .map(Tag::toResponseDto)
                .toList();
    }

    @Override
    public TagResponseDto updateTag(TagUpdateDto tag){

        /*업데이트할 엔티티 생성*/
        Tag updatedTag = new Tag();
        updatedTag.setId(tag.getId());
        updatedTag.setName(tag.getName());
        updatedTag.setDescription(tag.getDescription());

        /*변경할 타겟 태그를 못찾음.*/
        tagRepository.findById(updatedTag.getId()).orElseThrow(() ->
                new TagNotFoundException("변경할 태그를 찾지 못했습니다.",
                        "tag id: " + tag.getId() ));

        /*태그의 이름 중복 확인*/
        Optional<Tag> tagByName = tagRepository.findByname(updatedTag.getName());
        if(tagByName.isPresent()
                && !tagByName.get().getId().equals(updatedTag.getId()))// 태그의 설명만 바꾸는 경우가 아닐때
        {
            throw new TagDuplicatedException("이미 존재하는 태그 이름입니다.", "tag name: " + updatedTag.getName() );
        }

        log.info("[Tag] 태그 업데이트. id: {}", updatedTag.getId());
        updatedTag  = tagRepository.save(updatedTag);


        return updatedTag.toResponseDto();
    }


    @Override
    public void deleteById(Long id){
        /*삭제할 태그가 존재하는지 확인*/
        tagRepository.findById(id).orElseThrow(() ->
                new TagNotFoundException("삭제할 태그가 존재하지 않습니다.", "tag id: " + id ));

        /*태그와 연결된 여행지, 코스 연관관계 삭제*/
        courseTagRepository.deleteAllByTagId(id);
        destinationTagRepository.deleteAllByTagId(id);

        /*태그 삭제*/
        tagRepository.deleteById(id);

        log.info("[Tag] 태그 삭제. id: {}", id);
    }

    /******태그-코스 ******/

    @Override
    public void addTagsByCourse(Long courseId, List<Long> tagIds){

        if(tagIds == null || tagIds.isEmpty()){
            throw new IllegalTagArgumentException("코스에 추가할 태그가 없습니다.", "course id: " + courseId );
        }

        // 중복된 태그를 제외하고 추가함
        for (Long tagId : tagIds) {
            CourseTag courseTag = new CourseTag();
            Tag tag = tagRepository.findById(tagId).orElseThrow(() ->
                    new TagNotFoundException("해당 태그가 존재하지 않습니다.",
                            "tag id: " + tagId ));

            if(courseTagRepository.findByCourseIdAndTagId(courseId, tagId).isEmpty()){
                courseTag.setTag(tag);
                courseTag.setCourse(courseService.findById(courseId));
                courseTagRepository.save(courseTag);
            }
        }

        log.info("[Tag] 코스 태그 추가. 코스 id: {}, 태그 id: {}",courseId, tagIds);
    }


    @Override
    public List<TagResponseDto> findAllByCourseId(Long courseId){

        log.debug("[Tag] 코스에 해당하는 태그 조회. 코스 id: {}", courseId);

        List<CourseTag> courseTags = courseTagRepository.findAllByCourseId(courseId);

        // CourseTag에서 태그 추출
        List<TagResponseDto> tags = courseTags
                .stream()
                .map(CourseTag::getTag)
                .map(Tag::toResponseDto)
                .collect(Collectors.toList());

        return tags;
    }

    @Override
    public CourseMakerPagination<TravelCourse> findAllCourseByTagIds(List<Long> tagIds, Pageable pageable, OrderBy orderBy){

        log.debug("[Tag] 태그 해당하는 코스 조회. 태그 id: {}", tagIds);

        /*검색할때 아무 태그도 선택 안했을 경우 = 모든 태그를 기준으로 검색*/
        if(tagIds == null || tagIds.isEmpty()){
            tagIds = tagRepository.findAll()
                    .stream()
                    .map(Tag::getId)
                    .collect(Collectors.toList());
//            System.out.println("------------------"+tagIds);
        }

        OrderSpecifier<?> orderBySpecifier = null;
        OrderSpecifier<?> subOrder = new OrderSpecifier<>(Order.DESC, travelCourse.createdAt);

        switch(orderBy) {
            case VIEWS:
                orderBySpecifier = new OrderSpecifier<>(Order.DESC, travelCourse.views);
                break;
            case NEWEST:
                orderBySpecifier = new OrderSpecifier<>(Order.DESC, travelCourse.createdAt);
                break;
            case POPULAR:
                orderBySpecifier = new OrderSpecifier<>(Order.DESC, travelCourse.wishCount);
                break;
            case RATING:
                orderBySpecifier = new OrderSpecifier<>(Order.DESC, travelCourse.averageRating);
                break;
            case LIKE:
                orderBySpecifier = new OrderSpecifier<>(Order.DESC, travelCourse.likeCount);
                break;
        }

        List<TravelCourse> courses = queryFactory
                .select(courseTag, courseTag.course.count())
                .from(courseTag)// 코스태그에서 선택(코스에는 FK가 없음)
                .leftJoin(courseTag.course, travelCourse)// 코스-코스태그 조인
                .where(courseTag.tag.id.in(tagIds).and(travelCourse.deletedAt.isNull())).fetchJoin()// 다중태그 및 삭제되지 않은 코스 필터링
                .groupBy(courseTag.course)// 코스로 묶어서
//                .having(courseTag.course.count().gt(tagIds.size()-1))// 중복된 부분만 추출함
                .orderBy(orderBySpecifier)// 정렬 조건 설정
                .orderBy(subOrder)
                .offset(pageable.getOffset())// 페이지네이션
                .limit(pageable.getPageSize())
                .fetch()
                .stream()
                .map(n -> n.get(courseTag).getCourse())
                .collect(Collectors.toList());


        Long total = queryFactory
                .select(courseTag.course.count())
                .from(courseTag)// 코스태그에서 선택(코스에는 FK가 없음)
                .leftJoin(courseTag.course, travelCourse)// 코스-코스태그 조인
                .where(courseTag.tag.id.in(tagIds).and(travelCourse.deletedAt.isNull()))// 다중태그 및 삭제되지 않은 코스 필터링
                .groupBy(courseTag.course)// 코스로 묶어서
//                .having(courseTag.course.count().gt(tagIds.size()-1))// 중복된 부분만 추출함
                .orderBy(orderBySpecifier)// 정렬 조건 설정
                .fetch()
                .stream()
                .count();

        System.out.println("total = " + total);

        Page<TravelCourse> coursePage = new PageImpl<>(courses, pageable, total);


        CourseMakerPagination<TravelCourse> courseMakerPagination = new CourseMakerPagination<>(pageable, coursePage, total);


        return courseMakerPagination;
    }



    @Override
    public void deleteTagByCourse(Long courseId, List<TagResponseDto> tags){

        if(tags==null || tags.isEmpty()){
            throw new IllegalTagArgumentException("코스에서 삭제할 태그가 없습니다.", "course id: " + courseId );
        }

        List<Tag> tagEntitys = tags
                .stream()
                .map(TagResponseDto::toEntity)
                .toList();

        // 코스에 태그들 삭제
        queryFactory
                .delete(courseTag)
                .where(courseTag.course.id.eq(courseId), courseTag.tag.in(tagEntitys))
                .execute();

        log.info("[Tag] 코스 태그 삭제. 코스 id: {}, 태그: {}",courseId, tags);
    }

    @Override
    public void deleteAllTagByCourse(Long courseId){
        courseTagRepository.deleteAllByCourseId(courseId);

        log.info("[Tag] 모든 코스 태그 삭제. 코스 id: {}",courseId);
    }



    /******태그-여행지 ******/

    @Override
    public void addTagsByDestination(Long destinationId, List<Long> tagIds){

        if(tagIds == null || tagIds.isEmpty()){
            throw new IllegalTagArgumentException("여행지에 추가할 태그가 없습니다.", "destination id: " + destinationId );
        }

        // 중복된 태그를 제외하고 추가함
        for (Long tagId : tagIds) {
            DestinationTag destinationTag = new DestinationTag();
            Tag tag = tagRepository.findById(tagId).orElseThrow(() -> new TagNotFoundException("해당 태그가 존재하지 않습니다.", "tag id: " + tagId ));

            if(destinationTagRepository.findByDestinationIdAndTagId(destinationId, tagId).isEmpty()){// JOIN 걸어서 하기
                destinationTag.setTag(tag);
                destinationTag.setDestination(destinationService.findById(destinationId)); // JOIN 걸어서 하기
                destinationTagRepository.save(destinationTag);
            }
        }

        log.info("[Tag] 여행지 태그 추가. 여행지 id: {}, 태그: {}",destinationId, tagIds);
    }

    @Override
    public List<TagResponseDto> findAllByDestinationId(Long destinationId){
        List<DestinationTag> destinationTags = destinationTagRepository.findAllByDestinationId(destinationId);
        List<TagResponseDto> tags = new ArrayList<>();

        // CourseTag에서 태그 추출
        tags = destinationTags
                .stream()
                .map(DestinationTag::getTag)
                .map(Tag::toResponseDto)
                .collect(Collectors.toList());

        return tags;
    }


    @Override
    public CourseMakerPagination<Destination> findAllDestinationByTagIds(List<Long> tagIds, Pageable pageable, OrderBy orderBy) {

        log.debug("[Tag] 태그 해당하는 여행지 조회. 태그 id: {}", tagIds);

        /*검색할때 아무 태그도 선택 안했을 경우 = 모든 태그를 기준으로 검색*/
        if(tagIds == null || tagIds.isEmpty()){
            tagIds = tagRepository.findAll()
                    .stream()
                    .map(Tag::getId)
                    .collect(Collectors.toList());
        }

        OrderSpecifier<?> orderBySpecifier = null;
        OrderSpecifier<?> subOrder = new OrderSpecifier<>(Order.DESC, destination.createdAt);


        switch(orderBy) {
            case VIEWS:
                orderBySpecifier = new OrderSpecifier<>(Order.DESC, destination.views);
                break;
            case NEWEST:
                orderBySpecifier = new OrderSpecifier<>(Order.DESC, destination.createdAt);
                break;
            case POPULAR:
                orderBySpecifier = new OrderSpecifier<>(Order.DESC, destination.wishCount);
                break;
            case RATING:
                orderBySpecifier = new OrderSpecifier<>(Order.DESC, destination.averageRating);
                break;
            case LIKE:
                orderBySpecifier = new OrderSpecifier<>(Order.DESC, destination.likeCount);
                break;
        }


        List<Destination> destinations = queryFactory
                .select(destinationTag, destinationTag.destination.count())
                .from(destinationTag)// 여행지 태그에서 선택(여행지에는 FK가 없음)
                .leftJoin(destinationTag.destination, destination)// 여행지-여행지태그 조인
                .where(destinationTag.tag.id.in(tagIds).and(destination.deletedAt.isNull())).fetchJoin()// 다중태그 조건 검색
                .groupBy(destinationTag.destination)// 여행지로 묶어서
//                .having(destinationTag.destination.count().gt(tagIds.size()-1))// 중복된 부분만 추출
                .orderBy(orderBySpecifier)// 정렬 조건 설정
                .orderBy(subOrder)
                .offset(pageable.getOffset())// 페이지네이션
                .limit(pageable.getPageSize())
                .fetch()// 쿼리 실행
                .stream()// 리스트 -> 스트림으로 처리
                .map(n -> n.get(destinationTag).getDestination())// 여행지태그 -> 여행지 변환
                .collect(Collectors.toList());

        long total = queryFactory
                .select(destinationTag.destination.count())
                .from(destinationTag)// 여행지 태그에서 선택(코스에는 FK가 없음)
                .leftJoin(destinationTag.destination, destination)// 여행지-여행지태그 조인
                .where(destinationTag.tag.id.in(tagIds).and(destination.deletedAt.isNull()))// 다중태그
                .groupBy(destinationTag.destination)// 여행지로 묶어서
//                .having(destinationTag.destination.count().gt(tagIds.size()-1))// 중복된 부분만 추출함.fetch()
                .orderBy(orderBySpecifier)// 정렬 조건 설정
                .fetch()
                .stream()
                .count();
        System.out.println("total = " + total);

        Page<Destination> destinationPage = new PageImpl<>(destinations, pageable, total);

        CourseMakerPagination<Destination> courseMakerPagination = new CourseMakerPagination<>(pageable, destinationPage, total);

        return courseMakerPagination;
    }


    @Override
    public void deleteTagByDestination(Long destinationId, List<TagResponseDto> tags){

        if(tags==null || tags.isEmpty()){
            throw new IllegalTagArgumentException("삭제할 태그가 없습니다.", "destination id: " + destinationId );
        }

        List<Tag> tagsEntitys = tags
                .stream()
                .map(TagResponseDto::toEntity)
                .toList();

        queryFactory
                .delete(destinationTag)
                .where(destinationTag.destination.id.eq(destinationId), destinationTag.tag.in(tagsEntitys))
                .execute();

        log.info("[Tag] 여행지 태그 삭제. 여행지 id: {}, 태그: {}",destinationId, tags);
    }

    @Override
    public void deleteAllTagByDestination(Long destinationId){
        destinationTagRepository.deleteAllByDestinationId(destinationId);

        log.info("[Tag] 모든 여행지 태그 삭제. 여행지 id: {}",destinationId);
    }

}
