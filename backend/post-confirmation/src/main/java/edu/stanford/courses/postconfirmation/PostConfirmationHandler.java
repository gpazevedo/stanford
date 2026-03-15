package edu.stanford.courses.postconfirmation;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.CognitoUserPoolPostConfirmationEvent;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminAddUserToGroupRequest;

public class PostConfirmationHandler
    implements RequestHandler<CognitoUserPoolPostConfirmationEvent,
                              CognitoUserPoolPostConfirmationEvent> {

    private final CognitoIdentityProviderClient cognitoClient;

    /** Production constructor — SDK creates its own client from env credentials. */
    public PostConfirmationHandler() {
        this(CognitoIdentityProviderClient.create());
    }

    /** Test constructor — inject mock. */
    PostConfirmationHandler(CognitoIdentityProviderClient cognitoClient) {
        this.cognitoClient = cognitoClient;
    }

    @Override
    public CognitoUserPoolPostConfirmationEvent handleRequest(
            CognitoUserPoolPostConfirmationEvent event, Context context) {
        cognitoClient.adminAddUserToGroup(AdminAddUserToGroupRequest.builder()
            .userPoolId(event.getUserPoolId())
            .username(event.getUserName())
            .groupName("students")
            .build());
        return event; // Cognito requires the original event returned unchanged
    }
}
