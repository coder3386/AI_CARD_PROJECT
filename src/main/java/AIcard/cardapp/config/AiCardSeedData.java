package AIcard.cardapp.config;

import AIcard.cardapp.entity.Template;
import AIcard.cardapp.repository.TemplateRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class AiCardSeedData implements CommandLineRunner {

    private final JdbcTemplate jdbcTemplate;
    private final TemplateRepository templateRepository;

    public AiCardSeedData(JdbcTemplate jdbcTemplate, TemplateRepository templateRepository) {
        this.jdbcTemplate = jdbcTemplate;
        this.templateRepository = templateRepository;
    }

    @Override
    @Transactional
    public void run(String... args) {
        createTestUserIfMissing();
        createTemplateIfMissing(
                "modern_dark",
                "Modern Dark",
                "어두운 배경과 선명한 포인트 컬러를 사용하는 현대적인 명함",
                "modern,dark,tech",
                "전문적,세련됨,기술적",
                "navy,cyan,white",
                "개발자,기획자,디자이너"
        );
        createTemplateIfMissing(
                "simple_white",
                "Simple White",
                "흰 배경과 정돈된 타이포그래피 중심의 미니멀 명함",
                "simple,white,minimal",
                "깔끔함,신뢰감,단정함",
                "white,black,gray",
                "학생,연구원,사무직"
        );
        createTemplateIfMissing(
                "portfolio_grid",
                "Portfolio Grid",
                "포트폴리오 영역을 강조하는 크리에이터용 명함",
                "portfolio,grid,creative",
                "창의적,활동적,시각적",
                "black,green,yellow",
                "디자이너,영상편집자,마케터"
        );
    }

    private void createTestUserIfMissing() {
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from users where user_id = ? or login_id = ?",
                Integer.class,
                1L,
                "test"
        );
        if (count != null && count > 0) {
            return;
        }

        jdbcTemplate.update(
                """
                        insert into users
                        (user_id, login_id, password, name, email, phone, role, status, created_at, updated_at)
                        values (?, ?, ?, ?, ?, ?, ?, ?, current_timestamp, current_timestamp)
                        """,
                1L,
                "test",
                "test",
                "홍길동",
                "test@example.com",
                "010-1234-5678",
                "USER",
                1
        );
    }

    private void createTemplateIfMissing(
            String code,
            String name,
            String description,
            String tags,
            String moodTags,
            String colorTags,
            String recommendedJobs
    ) {
        if (templateRepository.findByTemplateCode(code).isPresent()) {
            return;
        }

        Template template = new Template();
        template.setTemplateCode(code);
        template.setTemplateName(name);
        template.setDescription(description);
        template.setTags(tags);
        template.setMoodTags(moodTags);
        template.setColorTags(colorTags);
        template.setRecommendedJobs(recommendedJobs);
        template.setHtmlPath("templates/cards/" + code + "/index.html");
        template.setCssPath("templates/cards/" + code + "/style.css");
        template.setActive(true);
        templateRepository.save(template);
    }
}
