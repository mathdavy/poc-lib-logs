package br.com.lib.structured.log.core;

import br.com.lib.structured.log.utils.Constants;
import org.apache.commons.lang3.StringUtils;

import java.util.Objects;

public enum RequestContextHolderEnum implements RequestContextHolderInterface {

    INSTANCE{
        public LogRequestAttributes getRequestAttributes() {
            return RequestContextHolderEnum.requestContextHolder.get();
        }

        public void setRequestAttributes(LogRequestAttributes attributes) {
            RequestContextHolderEnum.requestContextHolder.set(attributes);
        }

        public String getCallId() {
            if(Objects.nonNull(getRequestAttributes())
                    && Objects.nonNull(getRequestAttributes().getAttribute(Constants.CALL_ID_KEY))){
                return String.valueOf(getRequestAttributes().getAttribute(Constants.CALL_ID_KEY));
            }
            return StringUtils.EMPTY;
        }

        public void cleanUp() {
            RequestContextHolderEnum.requestContextHolder.remove();
        }
    };

    private static final InheritableThreadLocal<LogRequestAttributes> requestContextHolder = new InheritableThreadLocal<>();

    public static RequestContextHolderEnum get() {
        return RequestContextHolderEnum.INSTANCE;
    }
}
