package org.ekstep.ep.samza.service;

import static java.text.MessageFormat.format;

import java.io.File;
import java.text.MessageFormat;

import org.ekstep.ep.samza.core.Logger;
import org.ekstep.ep.samza.domain.Event;
import org.ekstep.ep.samza.task.TelemetryValidatorConfig;
import org.ekstep.ep.samza.task.TelemetryValidatorSink;
import org.ekstep.ep.samza.task.TelemetryValidatorSource;
import com.fasterxml.jackson.databind.JsonNode;
import com.github.fge.jackson.JsonLoader;
import com.github.fge.jsonschema.core.report.ProcessingReport;
import com.github.fge.jsonschema.main.JsonSchema;
import com.github.fge.jsonschema.main.JsonSchemaFactory;
import com.google.gson.JsonSyntaxException;

public class TelemetryValidatorService {
    static Logger LOGGER = new Logger(TelemetryValidatorService.class);
    private final TelemetryValidatorConfig config;

    public TelemetryValidatorService(TelemetryValidatorConfig config) {
        this.config = config;
    }

    public void process(TelemetryValidatorSource source, TelemetryValidatorSink sink, JsonSchemaFactory jsonSchemaFactory) {
        Event event = null;
        String schemaFile;
        try {
            event = source.getEvent();
            String telemetrySchemaFilePath = MessageFormat.format("{0}/{1}/{2}", config.telemetrySchemaPath(), event.version(), event.schemaName());
            String summaryEventSchemaFilepath = MessageFormat.format("{0}/{1}/{2}", config.summarySchemaPath(), event.version(), event.schemaName());
            schemaFile = event.isSummaryEvent() ? summaryEventSchemaFilepath : telemetrySchemaFilePath;
            File schemaFilePath = new File(schemaFile);
            if (!schemaFilePath.exists()) {
                LOGGER.info("SCHEMA DOES NOT FOUND", schemaFile);
                LOGGER.info("SKIP PROCESSING: SENDING TO SUCCESS", event.mid());
                event.markSkipped();
                sink.toSuccessTopic(event);
                return;
            }
            JsonNode schemaJson = JsonLoader.fromFile(schemaFilePath);
            JsonNode eventJson = JsonLoader.fromString(event.getJson());
            JsonSchema jsonSchema = jsonSchemaFactory.getJsonSchema(schemaJson);
            ProcessingReport report = jsonSchema.validate(eventJson);
            if (report.isSuccess()) {
                LOGGER.info("VALIDATION SUCCESS", event.mid());
                event.markSuccess();
                sink.toSuccessTopic(event);
            } else {
                String fieldName = this.getInvalidFieldName(report.toString());
                LOGGER.error(null, "VALIDATION FAILED: " + report.toString());
                sink.toFailedTopic(event, "Invalid field:" + fieldName);
            }
        } catch (JsonSyntaxException e) {
            LOGGER.error(null, "INVALID EVENT: " + source.getMessage());
            sink.toMalformedEventsTopic(source.getMessage());
        } catch (Exception e) {
            LOGGER.error(null, format(
                    "EXCEPTION. PASSING EVENT THROUGH AND ADDING IT TO EXCEPTION TOPIC. EVENT: {0}, EXCEPTION:",
                    event), e);
            sink.toErrorTopic(event, e.getMessage());
        }
    }

    private String getInvalidFieldName(String errorInfo) {
        String[] message = errorInfo.split("reports:");
        String[] fields = message[1].split(",");
        String pointer[] = fields[3].split("\"pointer\":");
        return pointer[1].substring(0, pointer[1].length() - 1);
    }
}
