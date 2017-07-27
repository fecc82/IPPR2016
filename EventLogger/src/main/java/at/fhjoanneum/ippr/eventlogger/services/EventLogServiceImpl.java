package at.fhjoanneum.ippr.eventlogger.services;


import at.fhjoanneum.ippr.commons.dto.eventlogger.EventLoggerDTO;
import at.fhjoanneum.ippr.eventlogger.helper.LogKey;
import at.fhjoanneum.ippr.eventlogger.persistence.EventLogEntry;
import at.fhjoanneum.ippr.eventlogger.persistence.EventLogRepository;
import at.fhjoanneum.ippr.persistence.objects.model.enums.StateFunctionType;
import com.google.common.collect.Lists;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.AsyncResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.supercsv.cellprocessor.Optional;
import org.supercsv.cellprocessor.ParseLong;
import org.supercsv.cellprocessor.constraint.NotNull;
import org.supercsv.cellprocessor.ift.CellProcessor;
import org.supercsv.io.CsvBeanReader;
import org.supercsv.io.ICsvBeanReader;
import org.supercsv.prefs.CsvPreference;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.*;
import java.util.concurrent.Future;


@Transactional(isolation = Isolation.READ_COMMITTED)
@Service
public class EventLogServiceImpl implements EventLogService {

  private static final Logger LOG = LoggerFactory.getLogger(EventLogServiceImpl.class);

  @Autowired
  private EventLogRepository eventLogRepository;

  @Async
  @Override
  public Future<List<EventLoggerDTO>> getEventLogForProcessModelAndSubject(final int processModelId,
      final String subject) {
    final List<EventLogEntry> results =
        eventLogRepository.getEventLogForProcessModelAndSubject(processModelId, subject);
    final List<EventLoggerDTO> eventLog = createEventLoggerDTO(results);
    return new AsyncResult<List<EventLoggerDTO>>(eventLog);
  }

  private static List<EventLoggerDTO> createEventLoggerDTO(final List<EventLogEntry> results) {
    final List<EventLoggerDTO> eventLog = Lists.newArrayList();

    results.forEach(event -> {
      final EventLoggerDTO dto = new EventLoggerDTO(event.getEventId(), event.getCaseId(),
          event.getProcessModelId(), event.getTimestamp(), event.getActivity(), event.getResource(),
          event.getState(), event.getMessageType(), event.getRecipient(), event.getSender());
      eventLog.add(dto);
    });

    return eventLog;
  }

  @Override
  public StreamResult manipulatePNML(final String pnmlContent, final String csvLog)
      throws Exception {
    StreamResult result = new StreamResult();

    try {
      final LinkedHashMap<LogKey, EventLogEntry> logQuintuplets = parseCSV(csvLog);
      final DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
      final InputSource input = new InputSource(new StringReader(pnmlContent));
      final Document document = documentBuilderFactory.newDocumentBuilder().parse(input);
      final Element root = document.getDocumentElement();
      final NodeList nets = root.getElementsByTagName("net");

      for (int temp = 0; temp < nets.getLength(); temp++) {
        final Node netNode = nets.item(temp);
        if (netNode.getNodeType() == Node.ELEMENT_NODE) {
          final Element net = (Element) netNode;
          final HashMap<String, String> transitions = getTransitions(net);

          int numOfCustomPlaces = 1;
          int numOfCustomArcs = 1;
          final Iterator<Map.Entry<LogKey, EventLogEntry>> it = logQuintuplets.entrySet().iterator();
          Map.Entry<LogKey, EventLogEntry> afterReceiveStateEntry = null;
          while (it.hasNext() || afterReceiveStateEntry != null) {
            Map.Entry<LogKey, EventLogEntry> entry;
            if (afterReceiveStateEntry != null) {
              entry = afterReceiveStateEntry;
            } else {
              entry = it.next();
            }

            final EventLogEntry eventLogEntry = entry.getValue();

            final String state = eventLogEntry.getState();
            final String activity = eventLogEntry.getActivity();
            final String messageType = eventLogEntry.getMessageType();
            final String recipient = eventLogEntry.getRecipient();
            final String sender = eventLogEntry.getSender();

            if (state.equals(StateFunctionType.SEND.name())) {
              afterReceiveStateEntry = null;
              final String placeId = addPlace(document, net, numOfCustomPlaces++, messageType, recipient, sender);
              addArc(document, net, numOfCustomArcs++, transitions.get(activity), placeId,
                  messageType);
            } else if (state.equals(StateFunctionType.RECEIVE.name())) {
              if (!it.hasNext()) {
                throw (new Exception("Letzter Log-Eintrag darf kein Receive State sein!"));
              }

              final Map.Entry<LogKey, EventLogEntry> nextEntry = it.next();
              final EventLogEntry nextEventLogEntry = nextEntry.getValue();

              final String nextActivity = nextEventLogEntry.getActivity();
              final String nextMessageType = nextEventLogEntry.getMessageType();

              final String placeId = addPlace(document, net, numOfCustomPlaces++, nextMessageType, recipient, sender);
              addArc(document, net, numOfCustomArcs++, placeId, transitions.get(nextActivity),
                  nextMessageType);

              afterReceiveStateEntry = nextEntry;
            } else {
              afterReceiveStateEntry = null;
            }
          }
        }

        final DOMSource source = new DOMSource(document);

        result = new StreamResult(new StringWriter());
        final TransformerFactory transformerFactory = TransformerFactory.newInstance();
        final Transformer transformer = transformerFactory.newTransformer();
        transformer.transform(source, result);
      }
    } catch (final Exception e) {
      LOG.error(e.getMessage());
      LOG.error("Exception while manipulating PNML");
      throw (e);
    }

    return result;
  }

  private LinkedHashMap<LogKey, EventLogEntry> parseCSV(final String csvLog) throws Exception {

    final LinkedHashMap<LogKey, EventLogEntry> result = new LinkedHashMap<>();
    ICsvBeanReader beanReader = null;
    try {
      beanReader =
          new CsvBeanReader(new StringReader(csvLog), CsvPreference.EXCEL_NORTH_EUROPE_PREFERENCE);

      // the header elements are used to map the values to the bean (names must match)
      final String[] header = beanReader.getHeader(true);
      final String[] uncapitalizedHeader = new String[header.length];
      for (int i = 0; i < header.length; i++) {
        uncapitalizedHeader[i] = StringUtils.uncapitalize(header[i]);
      }

      final CellProcessor[] processors = getProcessors();

      EventLogEntry eventLogEntry;
      while ((eventLogEntry =
          beanReader.read(EventLogEntry.class, uncapitalizedHeader, processors)) != null) {

        final LogKey key = new LogKey(eventLogEntry.getActivity(), eventLogEntry.getState(),
            eventLogEntry.getMessageType(), eventLogEntry.getRecipient(), eventLogEntry.getSender());
        if (!result.containsKey(key)) {
          result.put(key, eventLogEntry);
        }
      }
    } finally {
      if (beanReader != null) {
        beanReader.close();
      }

      return result;
    }
  }

  private static CellProcessor[] getProcessors() {
    final CellProcessor[] processors = new CellProcessor[] {new NotNull(new ParseLong()), // EventId
        new NotNull(new ParseLong()), // CaseId
        new NotNull(), // Timestamp
        new NotNull(), // Activity
        new NotNull(), // Resource
        new NotNull(), // State
        new Optional(), // MessageType
        new Optional(), // To
        new Optional() // From
    };

    return processors;
  }

  private String addArc(final Document document, final Element net, final int id,
      final String sourceId, final String targetId, final String name) {
    // Custom Arc
    final Element newArc = document.createElement("arc");
    net.appendChild(newArc);

    // Id
    final String arcId = "ca" + id;
    newArc.setAttribute("id", arcId);

    // Source Id
    newArc.setAttribute("source", sourceId);

    // Target Id
    newArc.setAttribute("target", targetId);

    // Name
    final Element nameElement = document.createElement("name");
    newArc.appendChild(nameElement);

    // Name Text
    final Element nameText = document.createElement("text");
    nameText.appendChild(document.createTextNode(name));
    nameElement.appendChild(nameText);

    // Arctype
    final Element arcTypeElement = document.createElement("arcType");
    newArc.appendChild(arcTypeElement);

    // Arctype Text
    final Element arcTypeText = document.createElement("text");
    arcTypeText.appendChild(document.createTextNode("normal"));
    arcTypeElement.appendChild(arcTypeText);

    return arcId;
  }

  private String addPlace(final Document document, final Element net, final int id,
      final String name, final String recipient, final String sender) {
    // Custom Place
    final Element newPlace = document.createElement("place");
    net.appendChild(newPlace);

    // Id
    final String placeId = "cp" + id;
    newPlace.setAttribute("id", placeId);

    // Name
    final Element nameElement = document.createElement("name");
    newPlace.appendChild(nameElement);

    // Name Text
    final Element text = document.createElement("text");
    text.appendChild(document.createTextNode(name +  " Recipient: " + recipient + " Sender: " + sender));
    nameElement.appendChild(text);

    // Graphics
    final Element graphics = document.createElement("graphics");
    newPlace.appendChild(graphics);

    // Graphics Position
    final Element position = document.createElement("position");
    position.setAttribute("x", "0");
    position.setAttribute("y", "0");
    graphics.appendChild(position);

    // Graphics Dimension
    final Element dimension = document.createElement("dimension");
    dimension.setAttribute("x", "12.5");
    dimension.setAttribute("y", "12.5");
    graphics.appendChild(position);

    return placeId;
  }

  private HashMap<String, String> getTransitions(final Element net) {
    final HashMap<String, String> transitions = new HashMap<>();
    final NodeList transitionNodes = net.getElementsByTagName("transition");
    for (int i = 0; i < transitionNodes.getLength(); i++) {
      final Element transitionNode = (Element) transitionNodes.item(i);
      final String id = transitionNode.getAttributes().getNamedItem("id").getNodeValue();
      final NodeList nameNodes = transitionNode.getElementsByTagName("name");

      if (nameNodes.getLength() > 0) {
        final Node nameNode = nameNodes.item(0);
        final String name = nameNode.getChildNodes().item(0).getTextContent();
        transitions.put(name, id);
      }
    }

    return transitions;
  }
}
