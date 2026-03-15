package edu.stanford.courses.api.users;

import edu.stanford.courses.api.users.domain.UserRepository;
import org.springframework.stereotype.Component;

@Component
public class UsersAPI {
    private final UserRepository userRepository;
    public UsersAPI(UserRepository userRepository) {
        this.userRepository = userRepository;
    }
    // delegate user methods
}
