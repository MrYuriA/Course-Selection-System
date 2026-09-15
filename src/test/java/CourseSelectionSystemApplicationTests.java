import org.junit.jupiter.api.Tag;
import org.springframework.boot.test.context.SpringBootTest;

@Tag("integration")  // 需要完整环境（MySQL / Redis / RabbitMQ），默认 mvn test 不跑
@SpringBootTest
class CourseSelectionSystemApplicationTests {

}
