package coursemaker.coursemaker.domain.member.service;

import coursemaker.coursemaker.domain.member.dto.ValidateEmailResponse;
import coursemaker.coursemaker.config.EmailConfig;
import coursemaker.coursemaker.domain.member.repository.MemberRepository;
import jakarta.annotation.PostConstruct;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.util.Random;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {
    private final JavaMailSender javaMailSender;
    private final MemberRepository memberRepository;
    private final EmailConfig emailConfig;
    private String fromEmail;

    @PostConstruct // 의존성 주입이 완료된 후 초기화를 수행하는 메서드
    private void init() {
        fromEmail = emailConfig.getUsername();
    }

    public String generateAuthCode() {
        int leftLimit = 48; // '0' 아스키 코드
        int rightLimit = 122; // 'z' 아스키 코드
        int stringLength = 6; // 인증 코드의 길이
        Random random = new Random();

        return random.ints(leftLimit, rightLimit + 1) // leftLimit 부터 rightLimit+1 사이의 난수 생성
                .filter(i -> (i < 57 || i >= 65) && ( i <= 90 || i >= 97)) // // 영문자와 숫자만 사용
                .limit(stringLength)
                .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append) // 생성된 난수를 ASCII 테이블에서 대응되는 문자로 변환
                .toString(); // StringBuilder 객체를 문자열로 변환해 반환
    }

    public void sendMail(String toEmail, String title, String content) throws MessagingException {
        MimeMessage mimeMessage = javaMailSender.createMimeMessage(); // JavaMailSender 객체를 이용해 MimeMessage 객체 생성

        mimeMessage.addRecipients(MimeMessage.RecipientType.TO, toEmail);
        mimeMessage.setSubject(title);
        mimeMessage.setFrom(fromEmail);
        mimeMessage.setText(content, "utf-8", "html");

        javaMailSender.send(mimeMessage);
    }

    public ValidateEmailResponse sendValidateSignupMail(String toEmail) throws MessagingException {
        String authCode = generateAuthCode();
        String title = "CourseMaker 회원가입 인증코드입니다.";
        String content =
                "CourseMaker 방문해주셔서 감사합니다.<br><br>"
                        + "인증 코드는 <code>" + authCode + "</code>입니다.<br>"
                        + "인증 코드를 바르게 입력해주세요."
                ;

        sendMail(toEmail, title, content); // 생성된 메일 발송

        log.info("[sendValidateSignupResult] 인증코드 메일이 발송됨. 수신자 id : {}", memberRepository.findByEmail(toEmail));
        ValidateEmailResponse validateEmailResponse = ValidateEmailResponse.builder()
                .fromMail(fromEmail)
                .toMail(toEmail)
                .title(title)
                .authCode(authCode)
                .build();

        return validateEmailResponse;
    }
}
