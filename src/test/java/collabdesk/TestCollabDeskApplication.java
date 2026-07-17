package collabdesk;

import org.springframework.boot.SpringApplication;

public class TestCollabDeskApplication {

    public static void main(String[] args) {
        SpringApplication.
                from(CollabDeskApplication::main).
                with(TestcontainersConfiguration.class).
                run(args);
    }

}
