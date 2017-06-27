package at.fhjoanneum.ippr.commons.dto.processengine;

import java.io.Serializable;

import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement
public class EventLoggerDTO implements Serializable {

    private Long caseId;
    private Long processModelId;
    private String timestamp;
    private String activity;
    private String resource;
    private String state;
    private String messageType;

    public EventLoggerDTO() {
    }

    public EventLoggerDTO(Long caseId, Long processModelId, String timestamp, String activity, String resource, String state, String messageType) {
        this.caseId = caseId;
        this.processModelId = processModelId;
        this.timestamp = timestamp;
        this.activity = activity;
        this.resource = resource;
        this.state = state;
        this.messageType = messageType;
    }

    public Long getCaseId() {
        return caseId;
    }

    public void setCaseId(Long caseId) {
        this.caseId = caseId;
    }

    public Long getProcessModelId() {
        return processModelId;
    }

    public void setProcessModelId(Long processModelId) {
        this.processModelId = processModelId;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }

    public String getActivity() {
        return activity;
    }

    public void setActivity(String activity) {
        this.activity = activity;
    }

    public String getResource() {
        return resource;
    }

    public void setResource(String resource) {
        this.resource = resource;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public String getMessageType() {
        return messageType;
    }

    public void setMessageType(String messageType) {
        this.messageType = messageType;
    }
}
