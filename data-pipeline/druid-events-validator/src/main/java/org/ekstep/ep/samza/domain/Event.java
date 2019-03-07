package org.ekstep.ep.samza.domain;

import java.text.MessageFormat;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang.StringUtils;
import org.ekstep.ep.samza.reader.NullableValue;
import org.ekstep.ep.samza.reader.Telemetry;
import org.ekstep.ep.samza.task.TelemetryValidatorConfig;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

import com.google.gson.Gson;

public class Event {

    private DateTimeFormatter df = DateTimeFormat.forPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZoneUTC();
    private final Telemetry telemetry;

    public Event(Map<String, Object> map) {
        this.telemetry = new Telemetry(map);
    }

    public Map<String, Object> getMap() {
        return telemetry.getMap();
    }

    public String getJson() {
        Gson gson = new Gson();
        String json = gson.toJson(getMap());
        return json;
    }

    public String getChecksum() {

        String checksum = id();
        if (checksum != null)
            return checksum;

        return mid();
    }

    public String id() {
        NullableValue<String> checksum = telemetry.read("metadata.checksum");
        return checksum.value();
    }

    public String mid() {
        NullableValue<String> checksum = telemetry.read("mid");
        return checksum.value();
    }

    public String eid() {
        NullableValue<String> eid = telemetry.read("eid");
        return eid.value();
    }

    public String pid() {
        NullableValue<String> pid = telemetry.read("context.pdata.pid");
        return pid.value();
    }

    public String schemaName() {
        String eid = eid();
        if (eid != null) {
            return MessageFormat.format("{0}.json", eid.toLowerCase());
        } else {
            return "envelope.json";
        }
    }

    public String version() {
        return (String) telemetry.read("ver").value();
    }

    @Override
    public String toString() {
        return "Event{" + "telemetry=" + telemetry + '}';
    }

    public void markSuccess() {
        telemetry.addFieldIfAbsent("flags", new HashMap<String, Boolean>());
        telemetry.add("flags.dv_processed", true);
        telemetry.add("type", "events");
    }

    public void markFailure(String error, TelemetryValidatorConfig config) {
        telemetry.addFieldIfAbsent("flags", new HashMap<String, Boolean>());
        telemetry.add("flags.dv_processed", false);

        telemetry.addFieldIfAbsent("metadata", new HashMap<String, Object>());
        if (null != error) {
            telemetry.add("metadata.dv_error", error);
            telemetry.add("metadata.src", config.jobName());
        }

    }

    public void markSkipped() {
        telemetry.addFieldIfAbsent("flags", new HashMap<String, Boolean>());
        telemetry.add("flags.dv_skipped", true);
    }

    public boolean isSummaryEvent() {
        NullableValue<String> eid = telemetry.read("eid");
        return (!eid.isNull() && eid.value().startsWith("ME_"));
    }
}
