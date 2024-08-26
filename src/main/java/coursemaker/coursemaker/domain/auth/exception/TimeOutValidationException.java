package coursemaker.coursemaker.domain.auth.exception;

import coursemaker.coursemaker.exception.ErrorCode;
import coursemaker.coursemaker.exception.RootException;
import lombok.Getter;

@Getter
public class TimeOutValidationException extends RootException {


    private final String message;
    public TimeOutValidationException(String message, String logMessage){
        super(ErrorCode.TIME_OUT, logMessage, message);
        this.message = message;
    }
}
