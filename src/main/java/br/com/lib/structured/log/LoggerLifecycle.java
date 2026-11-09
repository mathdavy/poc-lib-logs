package br.com.lib.structured.log;

import br.com.lib.structured.log.core.LoggerContextHolder;
import br.com.lib.structured.log.model.EntryData;
import br.com.lib.structured.log.model.logger.LoggerModel;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class LoggerLifecycle {

    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    private static final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule()
                    .addSerializer(LocalDateTime.class, new LocalDateTimeSerializer(formatter)))
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private static final ThreadLocal<Long> timeSpent = new ThreadLocal<>();

    public void start(EntryData entryData) {
        timeSpent.set(System.nanoTime());
        LoggerContextHolder.init(entryData);
    }

    public void finish() {
        logFinal();
        LoggerContextHolder.clear();
        timeSpent.remove();
    }

    private void logFinal() {
        try {
            LoggerModel model = LoggerContextHolder.get();
            model.setDate(LocalDateTime.now());
            model.setTimeSpent(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - timeSpent.get())); // tempo gasto em ms

            String json = mapper.writeValueAsString(model);

            log.info(json);

        } catch (Exception e) {
            log.error("trace-log: erro ao serializar o log final", e);
        }
    }
}
