package org.opencdmp.deposit.dspacerepository.model.builder;

import gr.cite.tools.logging.LoggerService;
import org.opencdmp.commonmodels.enums.PlanAccessType;
import org.opencdmp.commonmodels.models.PlanUserModel;
import org.opencdmp.commonmodels.models.description.*;
import org.opencdmp.commonmodels.models.descriptiotemplate.DefinitionModel;
import org.opencdmp.commonmodels.models.descriptiotemplate.fielddata.RadioBoxDataModel;
import org.opencdmp.commonmodels.models.descriptiotemplate.fielddata.SelectDataModel;
import org.opencdmp.commonmodels.models.plan.PlanBlueprintValueModel;
import org.opencdmp.commonmodels.models.plan.PlanModel;
import org.opencdmp.commonmodels.models.planblueprint.SectionModel;
import org.opencdmp.commonmodels.models.reference.ReferenceModel;
import org.opencdmp.deposit.dspacerepository.configuration.semantics.SemanticsProperties;
import org.opencdmp.deposit.dspacerepository.model.*;
import org.opencdmp.deposit.dspacerepository.service.dspace.DspaceDepositServiceImpl;
import org.opencdmp.deposit.dspacerepository.service.dspace.DspaceServiceProperties;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class DspaceBuilder {
    private static final LoggerService logger = new LoggerService(LoggerFactory.getLogger(DspaceDepositServiceImpl.class));

    private final  DspaceServiceProperties dspaceServiceProperties;
    private final  SemanticsProperties semanticsProperties;
    private static final String DSPACE_OP_ADD = "add";
    private static final String DSPACE_DC_TYPE = "Dataset";

    private static final String SEMANTIC_PROVENANCE = "provenance";
    private static final String SEMANTIC_IDENTIFIER = "identifier";
    private static final String SEMANTIC_TITLE_ALTERNATIVE = "title.alternative";
    private static final String SEMANTIC_AUTHOR = "author";
    private static final String SEMANTIC_AUTHOR_PATH = "/sections/publicationStep/dc.contributor.author";
    private static final String SEMANTIC_TYPE = "type";
    private static final String SEMANTIC_TYPE_PATH = "/sections/publicationStep/dc.type";
    private static final String SEMANTIC_OTHER = "other";
    private static final String SEMANTIC_DATE_ACCESSIONED = "date.accessioned";
    private static final String SEMANTIC_DATE_AVAILABLE = "date.available";
    private static final String SEMANTIC_LANGUAGE_PATH = "/sections/publicationStep/dc.language.iso";


    @Autowired
    public DspaceBuilder(DspaceServiceProperties dspaceServiceProperties, SemanticsProperties semanticsProperties){
            this.dspaceServiceProperties = dspaceServiceProperties;
            this.semanticsProperties = semanticsProperties;
    }

    public List<PatchEntity> build(PlanModel planModel) {

        List<PatchEntity> entities = new ArrayList<>();

        entities.addAll(this.buildSingleValue(planModel.getLabel(), DSPACE_OP_ADD, "/sections/publicationStep/dc.title"));

        DateFormat df = new SimpleDateFormat("yyyy-MM-dd");
        entities.addAll(this.buildSingleValue(df.format(new Date()),DSPACE_OP_ADD, "/sections/publicationStep/dc.date.issued"));
        entities.addAll(this.buildDescription(planModel.getDescription(), DSPACE_OP_ADD, "/sections/traditionalpagetwo/dc.description.abstract"));
        if(planModel.getAccessType().equals(PlanAccessType.Public)) entities.addAll(this.buildIsIdenticalTo(planModel,DSPACE_OP_ADD,"/sections/publicationStep/dc.identifier.uri"));

        entities.addAll(this.applySemantics(planModel,false));
        return entities;
    }

    public List<PatchEntity> buildSingleValue(String value, String op, String path) {

        List<PatchEntity> entityList = new ArrayList<>();

        PatchEntity entity = new PatchEntity();
        entity.setOp(op);
        entity.setPath(path);
        List<Map<String, Object>> v = new ArrayList<>();
        Map<String, Object> map = new HashMap<>();
        map.put("value", value);
        v.add(map);
        entity.setValue(v);

        entityList.add(entity);

        return entityList;
    }

    public List<PatchBooleanEntity> buildBooleanValue(boolean value, String op, String path) {

        List<PatchBooleanEntity> entityList = new ArrayList<>();

        PatchBooleanEntity entity = new PatchBooleanEntity();
        entity.setOp(op);
        entity.setPath(path);
        entity.setValue(value);

        entityList.add(entity);

        return entityList;
    }

    public List<PatchEntity> buildOwners(PlanModel plan, String op, String path) {

        List<PatchEntity> entityList = new ArrayList<>();

        PatchEntity entity = new PatchEntity();
        entity.setOp(op);
        entity.setPath(path);
        List<Map<String, Object>> v = new ArrayList<>();

        int place = 0;
        if (plan.getUsers() != null) {
            for (PlanUserModel planUser: plan.getUsers()) {

                Map<String, Object> map = new HashMap<>();
                map.put("confidence", -1);
                map.put("place", place);
                map.put("value", planUser.getUser().getName());
                map.put("display", planUser.getUser().getName());
                v.add(map);

                place++;
            }
        }

        entity.setValue(v);
        entityList.add(entity);
        return entityList;
    }

    public List<PatchEntity> buildOwnerListSemantic(Collection<String> semantics, PlanModel plan, String op, String path) {
        List<Map<String, Object>> valueList = new ArrayList<>();

        int place = 0;
        if (plan.getUsers() != null) {
            for (PlanUserModel planUser: plan.getUsers()) {

                Map<String, Object> map = new HashMap<>();
                map.put("confidence", -1);
                map.put("place", place);
                map.put("value", planUser.getUser().getName());
                map.put("display", planUser.getUser().getName());
                valueList.add(map);

                place++;
            }
        }

        for (String value : semantics) {
            Map<String, Object> map = new HashMap<>();
            map.put("value", value);
            map.put("display", value);
            map.put("confidence", -1);
            map.put("place", place++);
            valueList.add(map);
        }


        PatchEntity patch = new PatchEntity();
        patch.setOp(op);
        patch.setPath(path);
        patch.setValue(valueList);

        return Collections.singletonList(patch);
    }

    public List<PatchEntity> buildDescription(String description, String op, String path) {

        List<PatchEntity> entityList = new ArrayList<>();

        PatchEntity entity = new PatchEntity();
        entity.setOp(op);
        entity.setPath(path);

        List<Map<String, Object>> v = new ArrayList<>();
        Map<String, Object> map = new HashMap<>();
        map.put("value", description);
        v.add(map);
        entity.setValue(v);

        entityList.add(entity);

        return entityList;
    }

    public List<PatchEntity> buildIsIdenticalTo(PlanModel plan, String op, String path) {

        List<PatchEntity> entityList = new ArrayList<>();

        PatchEntity entity = new PatchEntity();
        entity.setOp(op);
        entity.setPath(path);
        List<Map<String, Object>> v = new ArrayList<>();

        int place = 0;
        Map<String, Object> map = new HashMap<>();
        map.put("place", place);
        map.put("confidence", -1);
        map.put("value", dspaceServiceProperties.getDomain() + "explore-plans/overview/public/" + plan.getId().toString());
        v.add(map);
        entity.setValue(v);

        entityList.add(entity);

        return entityList;

    }

    public List<PatchEntity> buildSemantic(String value, String op, String path) {

        List<PatchEntity> entityList = new ArrayList<>();

        PatchEntity entity = new PatchEntity();
        entity.setOp(op);
        entity.setPath(path);
        List<Map<String, Object>> v = new ArrayList<>();

        int place = 0;
        Map<String, Object> map = new HashMap<>();
        map.put("place", place);
        map.put("confidence", -1);
        map.put("value", value);
        v.add(map);
        entity.setValue(v);

        entityList.add(entity);

        return entityList;

    }

    public List<PatchEntity> buildTypeListSemantic(Collection<String> semantics, String op, String path) {
        List<Map<String, Object>> valueList = new ArrayList<>();
        Map<String, Object> defaultType = new HashMap<>();

        defaultType.put("value", "Dataset");
        defaultType.put("display", "Dataset");
        defaultType.put("confidence", -1);
        defaultType.put("place", 0);
        valueList.add(defaultType);

        int place = 1;
        for (String value : semantics) {
            Map<String, Object> map = new HashMap<>();
            map.put("value", value);
            map.put("display", value);
            map.put("confidence", -1);
            map.put("place", place++);
            valueList.add(map);
        }


        PatchEntity patch = new PatchEntity();
        patch.setOp(op);
        patch.setPath(path);
        patch.setValue(valueList);

        return Collections.singletonList(patch);
    }



    public List<PatchEntity> buildListSemantic(Collection<String> semantics, String op, String path) {
        List<Map<String, Object>> valueList = new ArrayList<>();

        int place = 0;
        for (String value : semantics) {
            Map<String, Object> map = new HashMap<>();
            map.put("value", value);
            map.put("display", value);
            map.put("confidence", -1);
            map.put("place", place++);
            valueList.add(map);
        }

        PatchEntity patch = new PatchEntity();
        patch.setOp(op);
        patch.setPath(path);
        patch.setValue(valueList);

        return Collections.singletonList(patch);
    }

    private List<org.opencdmp.commonmodels.models.descriptiotemplate.FieldModel> findDescriptionSemanticValues(String relatedId, DefinitionModel definitionModel){
        return definitionModel.getAllField().stream().filter(x-> x.getSemantics() != null && x.getSemantics().contains(relatedId)).toList();
    }

    private List<org.opencdmp.commonmodels.models.planblueprint.FieldModel> getFieldOfSemantic(PlanModel plan, String semanticKey){
        List<org.opencdmp.commonmodels.models.planblueprint.FieldModel> fields = new ArrayList<>();
        if (plan == null || plan.getPlanBlueprint() == null || plan.getPlanBlueprint().getDefinition() == null || plan.getPlanBlueprint().getDefinition().getSections() == null) return fields;
        for (SectionModel sectionModel : plan.getPlanBlueprint().getDefinition().getSections()){
            if (sectionModel.getFields() != null){
                org.opencdmp.commonmodels.models.planblueprint.FieldModel fieldModel = sectionModel.getFields().stream().filter(x-> x.getSemantics() != null && x.getSemantics().contains(semanticKey)).findFirst().orElse(null);
                if (fieldModel != null) fields.add(fieldModel);
            }
        }
        return fields;
    }

    private PlanBlueprintValueModel getPlanBlueprintValue(PlanModel plan, UUID id){
        if (plan == null || plan.getProperties() == null || plan.getProperties().getPlanBlueprintValues() == null) return null;
        return plan.getProperties().getPlanBlueprintValues().stream().filter(x-> x.getFieldId().equals(id)).findFirst().orElse(null);
    }


    private Set<String> extractSchematicValues(List<org.opencdmp.commonmodels.models.descriptiotemplate.FieldModel> fields, PropertyDefinitionModel propertyDefinition) {
        Set<String> values = new HashSet<>();
        for (org.opencdmp.commonmodels.models.descriptiotemplate.FieldModel field : fields) {
            if (field.getData() == null) continue;
            List<FieldModel> valueFields = this.findValueFieldsByIds(field.getId(), propertyDefinition);
            for (FieldModel valueField : valueFields) {
                switch (field.getData().getFieldType()) {
                    case FREE_TEXT, TEXT_AREA, RICH_TEXT_AREA -> {
                        if (valueField.getTextValue() != null && !valueField.getTextValue().isBlank()) values.add(valueField.getTextValue());
                    }
                    case BOOLEAN_DECISION, CHECK_BOX -> {
                        if (valueField.getBooleanValue() != null) values.add(valueField.getBooleanValue().toString());
                    }
                    case DATE_PICKER -> {
                        if (valueField.getDateValue() != null) values.add(DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneId.systemDefault()).format(valueField.getDateValue()));
                    }
                    case DATASET_IDENTIFIER, VALIDATION -> {
                        if (valueField.getExternalIdentifier() != null && valueField.getExternalIdentifier().getIdentifier() != null && !valueField.getExternalIdentifier().getIdentifier().isBlank()) {
                            values.add(valueField.getExternalIdentifier().getIdentifier());
                        }
                    }
                    case TAGS -> {
                        if (valueField.getTextListValue() != null && !valueField.getTextListValue().isEmpty()) {
                            values.addAll(valueField.getTextListValue());
                        }
                    }
                    case SELECT -> {
                        if (valueField.getTextListValue() != null && !valueField.getTextListValue().isEmpty()) {
                            SelectDataModel selectDataModel = (SelectDataModel)field.getData();
                            if (selectDataModel != null && selectDataModel.getOptions() != null && !selectDataModel.getOptions().isEmpty()){
                                for (SelectDataModel.OptionModel option : selectDataModel.getOptions()){
                                    if (valueField.getTextListValue().contains(option.getValue()) || valueField.getTextListValue().contains(option.getLabel())) values.add(option.getLabel());
                                }
                            }
                        }
                    }
                    case RADIO_BOX -> {
                        if (valueField.getTextListValue() != null && !valueField.getTextListValue().isEmpty()) {
                            RadioBoxDataModel radioBoxModel = (RadioBoxDataModel)field.getData();
                            if (radioBoxModel != null && radioBoxModel.getOptions() != null && !radioBoxModel.getOptions().isEmpty()){
                                for (RadioBoxDataModel.RadioBoxOptionModel option : radioBoxModel.getOptions()){
                                    if (valueField.getTextListValue().contains(option.getValue()) || valueField.getTextListValue().contains(option.getLabel())) values.add(option.getLabel());
                                }
                            }
                        }
                    }
                    case REFERENCE_TYPES -> {
                        if (valueField.getReferences() != null && !valueField.getReferences().isEmpty()) {
                            for (ReferenceModel referenceModel : valueField.getReferences()) {
                                if (referenceModel == null
                                        || referenceModel.getType() == null || referenceModel.getType().getCode() == null || referenceModel.getType().getCode().isBlank()
                                        || referenceModel.getDefinition() == null || referenceModel.getDefinition().getFields() == null || referenceModel.getDefinition().getFields().isEmpty()) continue;
                                if (referenceModel.getReference() != null && !referenceModel.getReference().isBlank()) {
                                    values.add(referenceModel.getReference());
                                }
                            }
                        }
                    }
                }
            }
        }
        return values;
    }


    private List<FieldModel> findValueFieldsByIds(String fieldId, PropertyDefinitionModel definitionModel){
        List<FieldModel> models = new ArrayList<>();
        if (definitionModel == null || definitionModel.getFieldSets() == null || definitionModel.getFieldSets().isEmpty()) return models;
        for (PropertyDefinitionFieldSetModel propertyDefinitionFieldSetModel : definitionModel.getFieldSets().values()){
            if (propertyDefinitionFieldSetModel == null ||propertyDefinitionFieldSetModel.getItems() == null || propertyDefinitionFieldSetModel.getItems().isEmpty()) continue;
            for (PropertyDefinitionFieldSetItemModel propertyDefinitionFieldSetItemModel : propertyDefinitionFieldSetModel.getItems()){
                if (propertyDefinitionFieldSetItemModel == null ||propertyDefinitionFieldSetItemModel.getFields() == null || propertyDefinitionFieldSetItemModel.getFields().isEmpty()) continue;
                for (Map.Entry<String, FieldModel> entry : propertyDefinitionFieldSetItemModel.getFields().entrySet()){
                    if (entry == null || entry.getValue() == null) continue;
                    if (entry.getKey().equalsIgnoreCase(fieldId)) models.add(entry.getValue());
                }
            }
        }
        return models;
    }



    public List<PatchEntity> applySemantics(PlanModel planModel, boolean isPublished) {
        List<PatchEntity> entityList = new ArrayList<>();
        List<SemanticsProperties.PathName> acceptedSemantics = this.semanticsProperties.getAvailable();

        Map<String, Set<String>> pathToValuesMap = new HashMap<>();

        if (planModel.getDescription() != null) {
            for (DescriptionModel descriptionModel : planModel.getDescriptions()) {
                for (SemanticsProperties.PathName relatedName : acceptedSemantics) {
                    List<org.opencdmp.commonmodels.models.descriptiotemplate.FieldModel> fieldsWithSemantics =
                            this.findDescriptionSemanticValues(
                                    relatedName.getName(),
                                    descriptionModel.getDescriptionTemplate().getDefinition()
                            );
                    Set<String> values = extractSchematicValues(fieldsWithSemantics, descriptionModel.getProperties());
                    pathToValuesMap.computeIfAbsent(relatedName.getPath(), k -> new HashSet<>()).addAll(values);
                }
            }
        }

        for (SemanticsProperties.PathName relatedName : acceptedSemantics) {
            List<org.opencdmp.commonmodels.models.planblueprint.FieldModel> fieldOfSemantic =
                    this.getFieldOfSemantic(planModel, relatedName.getName());

            for (org.opencdmp.commonmodels.models.planblueprint.FieldModel field : fieldOfSemantic) {
                PlanBlueprintValueModel valueModel = this.getPlanBlueprintValue(planModel, field.getId());

                if (valueModel != null) {
                    if (valueModel.getDateValue() != null) {
                        String dateVal = DateTimeFormatter.ofPattern("yyyy-MM-dd")
                                .withZone(ZoneId.systemDefault())
                                .format(valueModel.getDateValue());
                        pathToValuesMap.computeIfAbsent(relatedName.getPath(), k -> new HashSet<>()).add(dateVal);
                    }

                    if (valueModel.getNumberValue() != null) {
                        pathToValuesMap.computeIfAbsent(relatedName.getPath(), k -> new HashSet<>())
                                .add(valueModel.getNumberValue().toString());
                    }

                    if (valueModel.getValue() != null) {
                        pathToValuesMap.computeIfAbsent(relatedName.getPath(), k -> new HashSet<>())
                                .add(valueModel.getValue());
                    }
                }
            }
        }

        for (Map.Entry<String, Set<String>> entry : pathToValuesMap.entrySet()) {
            String path = entry.getKey();
            Set<String> values = entry.getValue();

            if(!isPublished && !path.contains(SEMANTIC_PROVENANCE) && !path.contains(SEMANTIC_DATE_ACCESSIONED) && !path.contains(SEMANTIC_DATE_AVAILABLE)){
                if (path.contains(SEMANTIC_IDENTIFIER) || path.contains(SEMANTIC_TITLE_ALTERNATIVE)) {
                    entityList.addAll(this.buildListSemantic(values, DSPACE_OP_ADD, path));

                } else if (path.contains(SEMANTIC_TYPE)) {
                    List<String> allowedTypes = this.dspaceServiceProperties.getAcceptedTypeCodes();

                    Set<String> validatedValues = values.stream()
                            .map(v -> allowedTypes.contains(v) ? v : SEMANTIC_OTHER)
                            .collect(Collectors.toSet());

                    entityList.addAll(this.buildTypeListSemantic(validatedValues, DSPACE_OP_ADD, path));
                } else if (path.contains(SEMANTIC_AUTHOR)) {
                    entityList.addAll(this.buildOwnerListSemantic(values,planModel, DSPACE_OP_ADD, path));
                } else {
                    for (String value : values) {
                        entityList.addAll(this.buildSemantic(value, DSPACE_OP_ADD, path));
                    }
                }
            }else if(isPublished){
                    for (String value : values) {
                        if (path.contains(SEMANTIC_PROVENANCE))  entityList.addAll(this.buildSemantic(value, DSPACE_OP_ADD, path));
                        if (path.contains(SEMANTIC_DATE_ACCESSIONED)) entityList.addAll(this.buildSemantic(value, DSPACE_OP_ADD, path));
                        if (path.contains(SEMANTIC_DATE_AVAILABLE)) entityList.addAll(this.buildSemantic(value, DSPACE_OP_ADD, path));
                    }
            }
        }

        if(!isPublished){
            boolean hasType = entityList.stream()
                    .anyMatch(entity -> entity.getPath().equals(SEMANTIC_TYPE_PATH));

            if(!hasType) entityList.addAll(this.buildSingleValue(this.dspaceServiceProperties.getDefaultType(), DSPACE_OP_ADD, SEMANTIC_TYPE_PATH));

            boolean hasLanguage = entityList.stream()
                    .anyMatch(entity -> entity.getPath().equals(SEMANTIC_LANGUAGE_PATH));

            if(!hasLanguage) entityList.addAll(this.buildSingleValue(planModel.getLanguage(), DSPACE_OP_ADD, SEMANTIC_LANGUAGE_PATH));

            boolean hasAuthors = entityList.stream()
                    .anyMatch(entity -> entity.getPath().equals(SEMANTIC_AUTHOR_PATH));

            if(!hasAuthors) entityList.addAll(this.buildOwners(planModel, DSPACE_OP_ADD, SEMANTIC_AUTHOR_PATH));
        }

        return entityList;
    }

}

