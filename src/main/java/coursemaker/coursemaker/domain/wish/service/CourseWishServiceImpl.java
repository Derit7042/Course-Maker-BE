package coursemaker.coursemaker.domain.wish.service;


import coursemaker.coursemaker.domain.course.entity.TravelCourse;
import coursemaker.coursemaker.domain.course.exception.TravelCourseNotFoundException;
import coursemaker.coursemaker.domain.course.repository.TravelCourseRepository;
import coursemaker.coursemaker.domain.member.entity.Member;
import coursemaker.coursemaker.domain.member.exception.UserNotFoundException;
import coursemaker.coursemaker.domain.member.repository.MemberRepository;
import coursemaker.coursemaker.domain.wish.dto.CourseWishRequestDto;
import coursemaker.coursemaker.domain.wish.dto.CourseWishResponseDto;
import coursemaker.coursemaker.domain.wish.entity.CourseWish;
import coursemaker.coursemaker.domain.wish.entity.DestinationWish;
import coursemaker.coursemaker.domain.wish.exception.CourseWishNotFoundException;
import coursemaker.coursemaker.domain.wish.repository.CourseWishRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
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

        List<CourseWish> wishes = courseWishRepository.findAll();

        if (wishes.isEmpty()) {
            throw new CourseWishNotFoundException("Invalid wish", "코스 찜이 존재하지 않습니다.");
        }

        return wishes.stream()
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
            throw new CourseWishNotFoundException("코스 찜이 존재하지 않습니다.", "Nickname: " + nickname);
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
                .orElseThrow(() -> new TravelCourseNotFoundException("해당 코스를 찾을 수 없습니다.", "CourseId: " + requestDto.getCourseId()));

        Member member = memberRepository.findByNickname(requestDto.getNickname())
                .orElseThrow(() -> new UserNotFoundException("해당 멤버를 찾을 수 없습니다.", "Nickname: " + requestDto.getNickname()));

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
        // 회원 정보 가져오기
        Member member = memberRepository.findByNickname(nickname)
                .orElseThrow(() -> new UserNotFoundException("해당 닉네임을 가진 사용자가 존재하지 않습니다.", "Nickname: " + nickname));

        // 찜 정보 가져오기
        CourseWish courseWish = courseWishRepository.findByTravelCourseIdAndMemberId(courseId, member.getId())

                .orElseThrow(() -> new CourseWishNotFoundException("해당 코스 찜이 존재하지 않습니다.", "CourseId: " + courseId + ", Nickname: " + nickname));


        // 코스 찜 삭제
        courseWishRepository.delete(courseWish);
    }


}
