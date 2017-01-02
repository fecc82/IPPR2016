package at.fhjoanneum.ippr.processengine.akka.tasks.process;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationAdapter;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import akka.actor.ActorRef;
import at.fhjoanneum.ippr.persistence.objects.engine.enums.ProcessInstanceState;
import at.fhjoanneum.ippr.persistence.objects.engine.process.ProcessInstance;
import at.fhjoanneum.ippr.persistence.objects.engine.subject.Subject;
import at.fhjoanneum.ippr.persistence.objects.model.enums.StateEventType;
import at.fhjoanneum.ippr.processengine.akka.messages.process.workflow.StateObjectChangeMessage;
import at.fhjoanneum.ippr.processengine.akka.tasks.AbstractTask;
import at.fhjoanneum.ippr.processengine.akka.tasks.TaskCallback;
import at.fhjoanneum.ippr.processengine.repositories.ProcessInstanceRepository;

@Component("Process.ProcessStateChangeTask")
@Scope("prototype")
public class ProcessStateChangeTask extends AbstractTask {

  private final static Logger LOG = LoggerFactory.getLogger(ProcessStateChangeTask.class);

  @Autowired
  private ProcessInstanceRepository processInstanceRepository;

  public <T> ProcessStateChangeTask(final TaskCallback<T> callback) {
    super(callback);
  }

  @Override
  public boolean canHandle(final Object obj) {
    return obj instanceof StateObjectChangeMessage.Request;
  }

  @Override
  public void execute(final Object obj) throws Exception {
    final StateObjectChangeMessage.Request request = (StateObjectChangeMessage.Request) obj;
    final Optional<ProcessInstance> processOpt =
        Optional.ofNullable(processInstanceRepository.findOne(request.getPiId()));

    if (processOpt.isPresent()) {
      final ProcessInstance process = processOpt.get();
      for (final Subject subject : process.getSubjects()) {
        if (!StateEventType.END
            .equals(subject.getSubjectState().getCurrentState().getEventType())) {
          LOG.debug("State [{}] is not in 'END' state, cannot finish process instance",
              subject.getSubjectState().getCurrentState());
          getSender().tell(new StateObjectChangeMessage.Response(request.getPiId(), Boolean.FALSE),
              getSelf());
          callback(Boolean.FALSE);
          return;
        }
      }

      process.setState(ProcessInstanceState.FINISHED);

      final ActorRef sender = getSender();
      TransactionSynchronizationManager
          .registerSynchronization(new TransactionSynchronizationAdapter() {
            @Override
            public void afterCommit() {
              LOG.info("All subject states are in 'END' state, set process instance to 'FINISHED'");
              sender.tell(new StateObjectChangeMessage.Response(request.getPiId(), Boolean.TRUE),
                  getSelf());
              callback(Boolean.TRUE);
            }
          });


    }
  }
}