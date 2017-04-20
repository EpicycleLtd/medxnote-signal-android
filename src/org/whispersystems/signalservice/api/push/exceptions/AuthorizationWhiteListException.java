package org.whispersystems.signalservice.api.push.exceptions;

/**
 * Created by jovannovkovic on 2/8/17.
 */

public class AuthorizationWhiteListException extends NonSuccessfulResponseCodeException {
    public AuthorizationWhiteListException(String s) {
        super(s);
    }
}
