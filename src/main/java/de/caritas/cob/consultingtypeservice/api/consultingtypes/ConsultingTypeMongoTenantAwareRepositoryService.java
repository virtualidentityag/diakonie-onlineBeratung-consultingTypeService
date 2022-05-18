package de.caritas.cob.consultingtypeservice.api.consultingtypes;

import de.caritas.cob.consultingtypeservice.api.exception.UnexpectedErrorException;
import de.caritas.cob.consultingtypeservice.api.exception.httpresponses.NotFoundException;
import de.caritas.cob.consultingtypeservice.api.model.ConsultingTypeEntity;
import de.caritas.cob.consultingtypeservice.api.service.LogService;
import de.caritas.cob.consultingtypeservice.api.service.tenant.TenantContext;
import de.caritas.cob.consultingtypeservice.schemas.model.ConsultingType;
import java.util.List;
import java.util.Optional;
import lombok.AllArgsConstructor;
import lombok.NonNull;
import org.springframework.beans.BeanUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;

/**
 * Repository for {@link ConsultingType}.
 */
@Repository
@Primary
@ConditionalOnExpression("${multitenancy.enabled:true}")
@AllArgsConstructor
public class ConsultingTypeMongoTenantAwareRepositoryService implements
    ConsultingTypeRepositoryService {

  private static final Long TECHNICAL_TENANT_ID = 0L;
  private @NonNull ConsultingTypeMongoTenantAwareRepository consultingTypeMongoTenantAwareRepository;
  private @NonNull ConsultingTypeConverter consultingTypeConverter;

  /**
   * Get a complete list of all {@link ConsultingType}.
   *
   * @return a {@link List} of {@link ConsultingType}
   */
  public List<ConsultingType> getListOfConsultingTypes() {
    if (isTechnicalTenantContext()) {
      List<ConsultingTypeEntity> consultingTypeEntities = consultingTypeMongoTenantAwareRepository.findAll();
      return consultingTypeConverter.convertList(consultingTypeEntities);
    } else {
      List<ConsultingTypeEntity> allHavingTenantId = consultingTypeMongoTenantAwareRepository.findAllHavingTenantId(
          TenantContext.getCurrentTenant());
      return consultingTypeConverter.convertList(allHavingTenantId);
    }
  }

  private boolean isTechnicalTenantContext() {
    return TECHNICAL_TENANT_ID
        .equals(TenantContext.getCurrentTenant());
  }

  /**
   * Get a consulting type by its id.
   *
   * @param consultingTypeId the id of the consulting type
   * @return the {@link ConsultingType} instance
   */
  public ConsultingType getConsultingTypeById(Integer consultingTypeId) {

    Optional<ConsultingType> byId = getById(consultingTypeId);

    if (byId.isEmpty()) {
      throw new NotFoundException(
          String.format("Consulting type with id %s not found.", consultingTypeId)
      );
    }
    return byId.get();
  }

  private Optional<ConsultingType> getById(Integer consultingTypeId) {
    if (isTechnicalTenantContext()) {
      return Optional.ofNullable(
          consultingTypeMongoTenantAwareRepository.findByConsultingTypeId(consultingTypeId));
    }
    return Optional.ofNullable(
        consultingTypeMongoTenantAwareRepository.findConsultingTypeByIdAndTenantId(
            consultingTypeId, TenantContext.getCurrentTenant()));
  }

  /**
   * Get a consulting type by its slug.
   *
   * @param slug the slug of the consulting type
   * @return the {@link ConsultingType} instance
   */
  public ConsultingType getConsultingTypeBySlug(String slug) {
    return findBySlug(slug).stream().findFirst()
        .orElseThrow(() -> new NotFoundException(
            String.format("Consulting type with slug %s not found.", slug)));
  }

  private List<ConsultingType> findBySlug(String slug) {
    if (isTechnicalTenantContext()) {
      List<ConsultingTypeEntity> bySlug = consultingTypeMongoTenantAwareRepository.findBySlug(slug);
      consultingTypeConverter.convertList(bySlug);
    }
    List<ConsultingTypeEntity> bySlugAndTenantId = consultingTypeMongoTenantAwareRepository.findBySlugAndTenantId(slug,
        TenantContext.getCurrentTenant());
    return consultingTypeConverter.convertList(bySlugAndTenantId);
  }

  /**
   * Add a consulting type to the repository.
   *
   * @param consultingType the {@link ConsultingType} to add
   */
  public void addConsultingType(ConsultingType consultingType) {
    if (isConsultingTypeWithGivenIdPresent(consultingType)
        || isConsultingTypeWithGivenSlugPresent(consultingType)) {
      LogService.logError(String
          .format("Could not initialize consulting type. id %s or slug %s is not unique",
              consultingType.getId(), consultingType.getSlug()));
      throw new UnexpectedErrorException();
    }
    ConsultingTypeEntity consultingTypeEntity = new ConsultingTypeEntity();
    BeanUtils.copyProperties(consultingType, consultingTypeEntity);
    this.consultingTypeMongoTenantAwareRepository.save(consultingTypeEntity);
  }

  private boolean isConsultingTypeWithGivenIdPresent(ConsultingType consultingType) {
    return getById(consultingType.getId()).isPresent();
  }

  protected boolean isConsultingTypeWithGivenSlugPresent(ConsultingType consultingType) {
    if (isTechnicalTenantContext()) {
      return !consultingTypeMongoTenantAwareRepository.findBySlug(consultingType.getSlug())
          .isEmpty();
    }
    return !consultingTypeMongoTenantAwareRepository.findBySlugAndTenantId(consultingType.getSlug(),
        TenantContext.getCurrentTenant()).isEmpty();
  }
}
