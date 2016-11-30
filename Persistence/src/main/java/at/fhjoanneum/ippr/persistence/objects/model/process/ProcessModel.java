package at.fhjoanneum.ippr.persistence.objects.model.process;

import java.time.LocalDateTime;
import java.util.List;

import at.fhjoanneum.ippr.persistence.objects.model.enums.ProcessModelState;
import at.fhjoanneum.ippr.persistence.objects.model.subject.SubjectModel;

public interface ProcessModel {

  Long getPmId();

  String getName();

  String getDescription();

  LocalDateTime createdAt();

  List<SubjectModel> getSubjectModels();

  void setState(ProcessModelState state);

  float getVersion();
}
