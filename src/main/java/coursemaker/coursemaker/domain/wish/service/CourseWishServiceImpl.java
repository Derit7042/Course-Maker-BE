package coursemaker.coursemaker.domain.wish.service;


import coursemaker.coursemaker.domain.course.entity.TravelCourse;
import coursemaker.coursemaker.domain.course.repository.TravelCourseRepository;
import coursemaker.coursemaker.domain.member.entity.Member;
import coursemaker.coursemaker.domain.member.repository.MemberRepository;
import coursemaker.coursemaker.domain.wish.dto.CourseWishRequestDto;
import coursemaker.coursemaker.domain.wish.dto.CourseWishResponseDto;
import coursemaker.coursemaker.domain.wish.entity.CourseWish;
import coursemaker.coursemaker.domain.wish.repository.CourseWishRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class CourseWishServiceImpl implements CourseWishService {

    private CourseWishRepository courseWishRepository;
    private TravelCourseRepository travelCourseRepository;
    private MemberRepository memberRepository;

    public CourseWishServiceImpl(CourseWishRepository courseWishRepository,
                                 TravelCourseRepository travelCourseRepository,
                                 MemberRepository memberRepository) {
        this.courseWishRepository = courseWishRepository;
        this.travelCourseRepository = travelCourseRepository;
        this.memberRepository = memberRepository;
    }


    /* 코스 찜목록 전체조회 */
    @Override
    public List<CourseWishResponseDto> getAllCourseWishes() {
        return courseWishRepository.findAll().stream()
                .map(courseWish -> new CourseWishResponseDto(
                        courseWish.getId(),
                        courseWish.getTravelCourse().getId(),
                        courseWish.getTravelCourse().getTitle(),
                        courseWish.getMember().getNickname()))
                .collect(Collectors.toList());
    }



    /* 코스 찜목록 닉네임으로 조회 */
    @Override
    public List<CourseWishResponseDto> getCourseWishesByNickname(String nickname) {
        List<CourseWish> courseWishes = courseWishRepository.findByMemberNickname(nickname);
        if (courseWishes.isEmpty()) {
            throw new RuntimeException("해당 코스 찜 정보가 없습니다.");
        }
        return courseWishes.stream()
                .map(courseWish -> new CourseWishResponseDto(
                        courseWish.getId(),
                        courseWish.getTravelCourse().getId(),
                        courseWish.getTravelCourse().getTitle(),
                        courseWish.getMember().getNickname()))
                .collect(Collectors.toList());
    }

    /* 코스 찜하기 */
    @Override
    @Transactional
    public CourseWishResponseDto addCourseWish(CourseWishRequestDto requestDto) {
        TravelCourse travelCourse = travelCourseRepository.findById(requestDto.getCourseId())
                .orElseThrow(() -> new RuntimeException("해당 코스를 찾을 수 없습니다."));
        Member member = memberRepository.findByNickname(requestDto.getNickname())
                .orElseThrow(() -> new RuntimeException("해당 멤버를 찾을 수 없습니다."));

        CourseWish courseWish = new CourseWish();
        courseWish.setTravelCourse(travelCourse);
        courseWish.setMember(member);

        CourseWish savedWish = courseWishRepository.save(courseWish);
        return new CourseWishResponseDto(
                savedWish.getId(),
                savedWish.getTravelCourse().getId(),
                savedWish.getTravelCourse().getTitle(),
                savedWish.getMember().getNickname());
    }

    /* 찜하기 취소 */
    @Override
    @Transactional
    public void cancelCourseWish(Long courseId, String nickname) {
        Member member = memberRepository.findByNickname(nickname)
                .orElseThrow(() -> new RuntimeException("해당 닉네임을 가진 사용자가 존재하지 않습니다."));

        CourseWish courseWish = courseWishRepository.findByTravelCourseIdAndMemberId(courseId, member.getId())
                .orElseThrow(() -> new RuntimeException("해당 찜하기가 존재하지 않습니다."));

        courseWishRepository.delete(courseWish);
    }
}
