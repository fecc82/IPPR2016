package at.fhjoanneum.ippr.processengine.akka.tasks.user;

import akka.actor.ActorRef;
import at.fhjoanneum.ippr.commons.dto.processengine.EventLoggerDTO;
import at.fhjoanneum.ippr.persistence.entities.engine.state.SubjectStateBuilder;
import at.fhjoanneum.ippr.persistence.entities.engine.state.SubjectStateImpl;
import at.fhjoanneum.ippr.persistence.objects.engine.process.ProcessInstance;
import at.fhjoanneum.ippr.persistence.objects.engine.state.SubjectState;
import at.fhjoanneum.ippr.persistence.objects.engine.subject.Subject;
import at.fhjoanneum.ippr.persistence.objects.model.enums.SubjectModelType;
import at.fhjoanneum.ippr.persistence.objects.model.messageflow.MessageFlow;
import at.fhjoanneum.ippr.persistence.objects.model.state.State;
import at.fhjoanneum.ippr.processengine.akka.messages.EmptyMessage;
import at.fhjoanneum.ippr.processengine.akka.messages.process.initialize.UserActorInitializeMessage;
import at.fhjoanneum.ippr.processengine.akka.tasks.AbstractTask;
import at.fhjoanneum.ippr.processengine.akka.tasks.TaskCallback;
import at.fhjoanneum.ippr.processengine.repositories.ProcessInstanceRepository;
import at.fhjoanneum.ippr.processengine.repositories.StateRepository;
import at.fhjoanneum.ippr.processengine.repositories.SubjectRepository;
import at.fhjoanneum.ippr.processengine.repositories.SubjectStateRepository;
import at.fhjoanneum.ippr.processengine.services.EventLoggerSender;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationAdapter;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.Optional;

@Component("User.UserActorInitializeTask")
@Scope("prototype")
public class UserActorInitializeTask extends AbstractTask<UserActorInitializeMessage.Request> {

  private final static Logger LOG = LoggerFactory.getLogger(StateObjectRetrieveTask.class);

  @Autowired
  private ProcessInstanceRepository processInstanceRepository;
  @Autowired
  private SubjectRepository subjectRepository;
  @Autowired
  private StateRepository stateRepository;
  @Autowired
  private SubjectStateRepository subjectStateRepository;
  @Autowired
  private EventLoggerSender eventLoggerSender;

  public <T> UserActorInitializeTask(final TaskCallback<T> callback) {
    super(callback);
  }

  @Override
  public boolean canHandle(final Object obj) {
    return obj instanceof UserActorInitializeMessage.Request;
  }

  @Override
  public void execute(final UserActorInitializeMessage.Request msg) throws Exception {
    final ProcessInstance processInstance =
        Optional.ofNullable(processInstanceRepository.findOne(msg.getPiId())).get();

    final Subject subject = Optional.ofNullable(subjectRepository.findOne(msg.getSId())).get();

    if (SubjectModelType.INTERNAL.equals(subject.getSubjectModel().getSubjectModelType())) {
      final State state = Optional
          .ofNullable(stateRepository.getStartStateOfSubject(subject.getSubjectModel().getSmId()))
          .get();

      final SubjectState subjectState = new SubjectStateBuilder().processInstance(processInstance)
          .subject(subject).state(state).build();

      subjectStateRepository.save((SubjectStateImpl) subjectState);
      LOG.info("Subject is now in initial state: {}", subjectState);
      long caseId = processInstance.getPiId();
      long processModelId = processInstance.getProcessModel().getPmId();
      String activity = subjectState.getCurrentState().getName();
      String timestamp = DateTime.now().toString("dd.MM.yyyy HH:mm");
      String resource = subject.getSubjectModel().getName();
      String stateType = subjectState.getCurrentState().getFunctionType().name();
      List<MessageFlow> mfs = subjectState.getCurrentState().getMessageFlow();
      String messageType = "";
      if(mfs.size() == 1) {
        messageType = subjectState.getCurrentState().getMessageFlow().get(0).getBusinessObjectModels().get(0).getName();
      }
      EventLoggerDTO event = new EventLoggerDTO(caseId, processModelId, timestamp, activity, resource, stateType, messageType);
      eventLoggerSender.send(event);
    } else {
      LOG.info("No need to set subject to initial state since it is 'EXTERNAL' [{}]", subject);
    }

    final ActorRef sender = getSender();
    TransactionSynchronizationManager
        .registerSynchronization(new TransactionSynchronizationAdapter() {
          @Override
          public void afterCommit() {
            // notify user supervisor actor
            sender.tell(new EmptyMessage(), getSelf());
            callback();
          }
        });
  }

}
