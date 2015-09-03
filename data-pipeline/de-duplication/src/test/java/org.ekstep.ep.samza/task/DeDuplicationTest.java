package org.ekstep.ep.samza.task;

import com.google.gson.Gson;
import org.apache.samza.config.Config;
import org.apache.samza.storage.kv.KeyValueStore;
import org.apache.samza.system.IncomingMessageEnvelope;
import org.apache.samza.system.OutgoingMessageEnvelope;
import org.apache.samza.system.SystemStream;
import org.apache.samza.task.MessageCollector;
import org.apache.samza.task.TaskContext;
import org.apache.samza.task.TaskCoordinator;
import org.ekstep.ep.samza.system.Event;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.HashMap;
import java.util.Map;

import static junit.framework.Assert.assertEquals;
import static org.mockito.Mockito.*;

public class DeDuplicationTest {
    private final String SUCCESS_TOPIC = "unique_events";
    private final String FAILED_TOPIC = "duplicate_events";
    KeyValueStore deDuplicationStore;
    private MessageCollector collector;
    private Config configMock;
    private TaskContext contextMock;
    private IncomingMessageEnvelope envelope;
    private TaskCoordinator coordinator;

    @Before
    public void setMock() {
        deDuplicationStore = mock(KeyValueStore.class);
        collector = mock(MessageCollector.class);
        configMock = Mockito.mock(Config.class);
        contextMock = Mockito.mock(TaskContext.class);
        envelope = Mockito.mock(IncomingMessageEnvelope.class);
        coordinator = Mockito.mock(TaskCoordinator.class);

        stub(configMock.get("output.success.topic.name", SUCCESS_TOPIC)).toReturn(SUCCESS_TOPIC);
        stub(configMock.get("output.failed.topic.name", FAILED_TOPIC)).toReturn(FAILED_TOPIC);
        stub(contextMock.getStore("de-duplication")).toReturn(deDuplicationStore);

    }

    @Test
    public void ShouldSendOutPutToFailedTopicIfChecksumIsPresentInStore() throws Exception{

        Event event = createEvent();
        when(deDuplicationStore.get(event.getChecksum())).thenReturn("bc811958-b4b7-4873-a43a-03718edba45b");

        DeDuplicationStreamTask deDuplicationStreamTask = new DeDuplicationStreamTask(deDuplicationStore);
        deDuplicationStreamTask.init(configMock, contextMock);

        ArgumentCaptor<OutgoingMessageEnvelope> outgoingMessageEnvelope = ArgumentCaptor.forClass(OutgoingMessageEnvelope.class);

        deDuplicationStreamTask.processEvent(event, collector);

        verify(collector,times(1)).send(outgoingMessageEnvelope.capture());

        SystemStream systemStream = outgoingMessageEnvelope.getValue().getSystemStream();
        assertEquals("kafka", systemStream.getSystem());
        assertEquals("duplicate_events", systemStream.getStream());

    }

    @Test
    public void ShouldSendOutPutToSuccessTopicAndCreateNewEntryInStoreIfChecksumIsNotPresentInStore() throws Exception{

        Event event = createEvent();
        when(deDuplicationStore.get(event.getChecksum())).thenReturn(null);


        DeDuplicationStreamTask deDuplicationStreamTask = new DeDuplicationStreamTask(deDuplicationStore);
        deDuplicationStreamTask.init(configMock,contextMock);

        ArgumentCaptor<OutgoingMessageEnvelope> outgoingMessageEnvelope = ArgumentCaptor.forClass(OutgoingMessageEnvelope.class);

        deDuplicationStreamTask.processEvent(event, collector);

        verify(collector,times(1)).send(outgoingMessageEnvelope.capture());

        SystemStream systemStream = outgoingMessageEnvelope.getValue().getSystemStream();
        assertEquals("kafka", systemStream.getSystem());
        assertEquals("unique_events", systemStream.getStream());

    }

    @Test(expected=com.google.gson.JsonSyntaxException.class)
    public void itShouldThrowAnExceptionIfTheJsonInputIsInValid() throws Exception {

        when(envelope.getMessage()).thenReturn("{'metadata':{'checksum':'sajksajska'}");

        String message = (String) envelope.getMessage();
        Map<String,Object> jsonObject = new HashMap<String,Object>();

        Gson gson = new Gson();

        DeDuplicationStreamTask deDuplicationStreamTask = new DeDuplicationStreamTask(deDuplicationStore);

        ArgumentCaptor<OutgoingMessageEnvelope> outgoingMessageEnvelope = ArgumentCaptor.forClass(OutgoingMessageEnvelope.class);

        deDuplicationStreamTask.validateJson(collector, message, gson, jsonObject);
    }

    private Event createEvent() {

        Event event = mock(Event.class);
        when(event.getChecksum()).thenReturn("bc811958-b4b7-4873-a43a-03718edba45b");
        Map<String, Object> map = createMap();
        when(event.getMap()).thenReturn(map);

        return event;
    }

    private Map<String,Object> createMap(){
        Map<String, Object> metadata = new HashMap<String, Object>();;
        metadata.put("checksum","bc811958-b4b7-4873-a43a-03718edba45b");
        Map<String, Object> event = new HashMap<String,Object>();
        event.put("metadata",metadata);
        return event;
    }

}