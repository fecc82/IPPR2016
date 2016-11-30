package at.fhjoanneum.ippr.persistence.entities.model.process;

import static at.fhjoanneum.ippr.persistence.builder.BuilderUtils.isNotBlank;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import java.util.List;
import java.util.stream.Collectors;

import at.fhjoanneum.ippr.persistence.builder.Builder;
import at.fhjoanneum.ippr.persistence.entities.model.subject.SubjectModelImpl;
import at.fhjoanneum.ippr.persistence.objects.model.enums.ProcessModelState;
import at.fhjoanneum.ippr.persistence.objects.model.process.ProcessModel;
import at.fhjoanneum.ippr.persistence.objects.model.subject.SubjectModel;

public class ProcessModelBuilder implements Builder<ProcessModel> {

  private String name;
  private String description;
  private ProcessModelState state;
  private List<SubjectModelImpl> subjectModels;
  private SubjectModelImpl starterSubject;
  private float version;

  public ProcessModelBuilder name(final String name) {
    isNotBlank(name, "processName");
    this.name = name;
    return this;
  }

  public ProcessModelBuilder description(final String description) {
    isNotBlank(description, "processDescription");
    this.description = description;
    return this;
  }

  public ProcessModelBuilder state(final ProcessModelState state) {
    checkNotNull(state);
    this.state = state;
    return this;
  }

  public ProcessModelBuilder subjectModels(final List<SubjectModel> subjectModels) {
    checkNotNull(subjectModels);
    this.subjectModels =
        subjectModels.stream().filter(subject -> subject instanceof SubjectModelImpl)
            .map(subject -> (SubjectModelImpl) subject).collect(Collectors.toList());
    checkNotNull(this.subjectModels);
    checkArgument(!subjectModels.isEmpty());
    return this;
  }

  public ProcessModelBuilder starterSubject(final SubjectModel starterSubject) {
    checkNotNull(starterSubject);
    checkArgument(starterSubject instanceof SubjectModelImpl);
    this.starterSubject = (SubjectModelImpl) starterSubject;
    return this;
  }

  public ProcessModelBuilder version(final float version) {
    checkNotNull(version);
    this.version = version;
    return this;
  }

  @Override
  public ProcessModel build() {
    isNotBlank(name);
    isNotBlank(description);
    checkNotNull(state);
    checkNotNull(subjectModels);
    checkArgument(!subjectModels.isEmpty());
    checkNotNull(starterSubject);
    checkNotNull(version);

    final ProcessModelImpl processModel =
        new ProcessModelImpl(name, description, state, subjectModels, starterSubject, version);
    return processModel;
  }
}
