package coursemaker.coursemaker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@SpringBootApplication
@EnableJpaAuditing
@EnableAspectJAutoProxy// AOP enable
public class CoursemakerApplication {

	public static void main(String[] args) {
		SpringApplication.run(CoursemakerApplication.class, args);
	}

}
