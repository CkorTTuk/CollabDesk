package collabdesk;

import collabdesk.auth.mail.MailService;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.testcontainers.mysql.MySQLContainer;

@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {
    @Bean
    @ServiceConnection
    public MySQLContainer mysqlContainer() {
        return new MySQLContainer("mysql:8.4.10");
    }

    @Bean
    @Primary
    public MailService testMailService() {
        return org.mockito.Mockito.mock(MailService.class);
    }
}
