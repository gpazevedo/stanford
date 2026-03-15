package edu.stanford.courses.postconfirmation;

import com.amazonaws.services.lambda.runtime.events.CognitoUserPoolPostConfirmationEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminAddUserToGroupRequest;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PostConfirmationHandlerTest {

    @Mock CognitoIdentityProviderClient cognitoClient;
    PostConfirmationHandler handler;

    @BeforeEach void setUp() { handler = new PostConfirmationHandler(cognitoClient); }

    private CognitoUserPoolPostConfirmationEvent event(String userPoolId, String userName) {
        var e = new CognitoUserPoolPostConfirmationEvent();
        e.setUserPoolId(userPoolId);
        e.setUserName(userName);
        return e;
    }

    @Test
    void addsUserToStudentsGroup() {
        handler.handleRequest(event("us-east-1_ABC123", "test-user-123"), null);
        verify(cognitoClient).adminAddUserToGroup(eq(AdminAddUserToGroupRequest.builder()
            .userPoolId("us-east-1_ABC123")
            .username("test-user-123")
            .groupName("students")
            .build()));
    }

    @Test
    void returnsEventUnchanged() {
        var e = event("p1", "u1");
        var result = handler.handleRequest(e, null);
        assertThat(result).isSameAs(e);
    }
}
