package com.fir.netty.config.exception;


/**
 * @author dpe
 */
public class CommonException extends RuntimeException {
    protected String errorCode;
    protected String[] errorMessageArguments;

    protected CommonException() {
        this("");
    }


    @Override
    public synchronized Throwable fillInStackTrace() {
        return null;
    }

    public CommonException(String message) {
        super(message);
        this.errorCode = "fail";
        this.errorMessageArguments = new String[]{message};
    }

    public CommonException(String message, Throwable cause) {
        super(message, cause);
        this.errorCode = "fail";
        this.errorMessageArguments = new String[]{message};
    }

    public String getErrorCode() {
        return this.errorCode;
    }

    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }

    public String[] getErrorMessageArguments() {
        return this.errorMessageArguments;
    }

    public void setErrorMessageArguments(String[] errorMessageArguments) {
        this.errorMessageArguments = errorMessageArguments;
    }

    public static CommonException withErrorCode(String errorCode) {
        CommonException businessException = new CommonException();
        businessException.errorCode = errorCode;
        return businessException;
    }

    public CommonException withErrorMessageArguments(String... errorMessageArguments) {
        if (errorMessageArguments != null) {
            this.errorMessageArguments = errorMessageArguments;
        }
        return this;
    }
}
