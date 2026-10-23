package org.opencdmp.deposit.dspacerepository.model.builder;

import gr.cite.tools.logging.LoggerService;
import org.opencdmp.commonmodels.enums.PlanAccessType;
import org.opencdmp.commonmodels.models.PlanUserModel;
import org.opencdmp.commonmodels.models.description.*;
import org.opencdmp.commonmodels.models.planblueprintdescriptiontemplatefields.fielddata.RadioBoxDataModel;
import org.opencdmp.commonmodels.models.planblueprintdescriptiontemplatefields.fielddata.SelectDataModel;
import org.opencdmp.commonmodels.models.plan.PlanModel;
import org.opencdmp.commonmodels.models.plandescriptionfields.FieldModel;
import org.opencdmp.commonmodels.models.plandescriptionfields.PropertyDefinitionFieldSetItemModel;
import org.opencdmp.commonmodels.models.plandescriptionfields.PropertyDefinitionFieldSetModel;
import org.opencdmp.commonmodels.models.reference.ReferenceModel;
import org.opencdmp.deposit.dspacerepository.configuration.semantics.SemanticsProperties;
import org.opencdmp.deposit.dspacerepository.model.*;
import org.opencdmp.deposit.dspacerepository.service.dspace.DspaceDepositServiceImpl;
import org.opencdmp.deposit.dspacerepository.service.dspace.DspaceServiceProperties;
import org.opencdmp.depositbase.repository.DepositPluginSettings;
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

    public static final String SEMANTIC_PROVENANCE = "provenance";
    public static final String SEMANTIC_IDENTIFIER = "identifier";
    public static final String SEMANTIC_TITLE_ALTERNATIVE = "title.alternative";
    public static final String SEMANTIC_AUTHOR = "author";
    private static final String SEMANTIC_AUTHOR_PATH = "/sections/publicationStep/dc.contributor.author";
    public static final String SEMANTIC_TYPE = "type";
    private static final String SEMANTIC_TYPE_PATH = "/sections/publicationStep/dc.type";
    private static final String SEMANTIC_OTHER = "other";
    public static final String SEMANTIC_DATE_ACCESSIONED = "date.accessioned";
    public static final String SEMANTIC_DATE_AVAILABLE = "date.available";
    private static final String SEMANTIC_LANGUAGE_PATH = "/sections/publicationStep/dc.language.iso";
    private static final String SEMANTIC_PUBLISHER_PATH = "/sections/publicationStep/dc.publisher";
    private static final String SEMANTIC_RIGHTS_PATH = "/sections/publicationStep/dc.rights";
    private static final String SEMANTIC_RIGHTS_URI_PATH = "/sections/publicationStep/dc.rights.uri";
    private static final String SETTING_ITEM_TYPE = "item-type";
    private static final String SETTING_PUBLISHER = "publisher";
    private static final String SETTING_RIGHTS = "rights";
    private static final String SETTING_RIGHTS_URI = "rights-uri";
    private static final String PUBLIC_PAGE_PATH = "/sections/publicationStep/dc.identifier.uri";
    private static final String TITLE_PATH = "/sections/publicationStep/dc.title";
    private static final String DATE_ISSUED_PATH = "/sections/publicationStep/dc.date.issued";
    private static final String ABSTRACT_PATH = "/sections/traditionalpagetwo/dc.description.abstract";

    /** The submission paths the builder writes whatever the semantics configuration says. */
    public static final List<String> FIXED_PATHS = List.of(TITLE_PATH, DATE_ISSUED_PATH, SEMANTIC_AUTHOR_PATH, SEMANTIC_TYPE_PATH, SEMANTIC_LANGUAGE_PATH,
            SEMANTIC_PUBLISHER_PATH, PUBLIC_PAGE_PATH, SEMANTIC_RIGHTS_PATH, SEMANTIC_RIGHTS_URI_PATH, ABSTRACT_PATH);


    @Autowired
    public DspaceBuilder(DspaceServiceProperties dspaceServiceProperties, SemanticsProperties semanticsProperties){
            this.dspaceServiceProperties = dspaceServiceProperties;
            this.semanticsProperties = semanticsProperties;
    }

    public List<PatchEntity> build(PlanModel planModel) {

        List<PatchEntity> entities = new ArrayList<>();

        entities.addAll(this.buildSingleValue(planModel.getLabel(), DSPACE_OP_ADD, TITLE_PATH));

        DateFormat df = new SimpleDateFormat("yyyy-MM-dd");
        entities.addAll(this.buildSingleValue(df.format(new Date()),DSPACE_OP_ADD, DATE_ISSUED_PATH));
        entities.addAll(this.buildDescription(planModel.getDescription(), DSPACE_OP_ADD, ABSTRACT_PATH));
        List<PatchEntity> semantics = this.applySemantics(planModel,false);
        if(planModel.getAccessType().equals(PlanAccessType.Public)) {
            PatchEntity publicPage = this.buildIsIdenticalTo(planModel, DSPACE_OP_ADD, PUBLIC_PAGE_PATH).get(0);
            PatchEntity uris = semantics.stream().filter(x -> PUBLIC_PAGE_PATH.equals(x.getPath())).findFirst().orElse(null);
            if (uris == null) entities.add(publicPage);
            else {
                // the answers for the URIs are one "add" that replaces the whole field, so the public page goes first in that same list
                List<Map<String, Object>> values = new ArrayList<>(publicPage.getValue());
                Object publicPageUrl = publicPage.getValue().get(0).get("value");
                uris.getValue().stream().filter(x -> !publicPageUrl.equals(x.get("value"))).forEach(values::add);
                for (int place = 0; place < values.size(); place++) values.get(place).put("place", place);
                uris.setValue(values);
            }
        }

        entities.addAll(semantics);
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

    public List<PatchEntity> buildOwners(List<PlanUserModel> users, String op, String path) {

        List<PatchEntity> entityList = new ArrayList<>();

        PatchEntity entity = new PatchEntity();
        entity.setOp(op);
        entity.setPath(path);
        List<Map<String, Object>> v = new ArrayList<>();

        int place = 0;
        for (PlanUserModel planUser: users) {

            Map<String, Object> map = new HashMap<>();
            map.put("confidence", -1);
            map.put("place", place);
            map.put("value", planUser.getUser().getName());
            map.put("display", planUser.getUser().getName());
            v.add(map);

            place++;
        }

        entity.setValue(v);
        entityList.add(entity);
        return entityList;
    }

    public List<PatchEntity> buildOwnerListSemantic(Collection<String> semantics, List<PlanUserModel> users, String op, String path) {
        List<Map<String, Object>> valueList = new ArrayList<>();

        int place = 0;
        for (PlanUserModel planUser: users) {

            Map<String, Object> map = new HashMap<>();
            map.put("confidence", -1);
            map.put("place", place);
            map.put("value", planUser.getUser().getName());
            map.put("display", planUser.getUser().getName());
            valueList.add(map);

            place++;
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

    public List<PatchEntity> buildTypeListSemantic(String defaultTypeValue, Collection<String> semantics, String op, String path) {
        List<Map<String, Object>> valueList = new ArrayList<>();
        Map<String, Object> defaultType = new HashMap<>();

        defaultType.put("value", defaultTypeValue);
        defaultType.put("display", defaultTypeValue);
        defaultType.put("confidence", -1);
        defaultType.put("place", 0);
        valueList.add(defaultType);

        int place = 1;
        for (String value : semantics) {
            if (value.equals(defaultTypeValue)) continue;
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

    private List<org.opencdmp.commonmodels.models.planblueprintdescriptiontemplatefields.FieldModel> findSchematicValues(String relatedId, List<org.opencdmp.commonmodels.models.planblueprintdescriptiontemplatefields.FieldModel> fields){
        return fields.stream().filter(x-> x.getSemantics() != null && x.getSemantics().contains(relatedId)).toList();
    }

    private Set<String> extractSchematicValues(List<org.opencdmp.commonmodels.models.planblueprintdescriptiontemplatefields.FieldModel> fields, Map<String, PropertyDefinitionFieldSetModel> fieldSets) {
        Set<String> values = new LinkedHashSet<>();
        for (org.opencdmp.commonmodels.models.planblueprintdescriptiontemplatefields.FieldModel field : fields) {
            if (field.getData() == null) continue;
            List<FieldModel> valueFields = this.findValueFieldsByIds(field.getId(), fieldSets);
            for (FieldModel valueField : valueFields) {
                switch (field.getData().getFieldType()) {
                    case FREE_TEXT, TEXT_AREA, RICH_TEXT_AREA -> {
                        if (valueField.getTextValue() != null && !valueField.getTextValue().isBlank()) values.add(valueField.getTextValue());
                    }
                    case NUMBER -> {
                        if (valueField.getNumberValue() != null) values.add(String.valueOf(valueField.getNumberValue()));
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


    private List<FieldModel> findValueFieldsByIds(String fieldId, Map<String, PropertyDefinitionFieldSetModel> fieldSets){
        List<FieldModel> models = new ArrayList<>();
        if (fieldSets == null || fieldSets.isEmpty()) return models;
        for (PropertyDefinitionFieldSetModel propertyDefinitionFieldSetModel : fieldSets.values()){
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
        List<SemanticsProperties.PathName> acceptedSemantics = this.semanticsProperties.getAvailable() != null ? this.semanticsProperties.getAvailable() : List.of();

        Map<String, Set<String>> pathToValuesMap = new LinkedHashMap<>();
        DepositPluginSettings settings = DepositPluginSettings.of(planModel, this.dspaceServiceProperties.getDepositConfiguration());
        String defaultType = settings.text(SETTING_ITEM_TYPE, this.dspaceServiceProperties.getDefaultType());
        List<PlanUserModel> authors = settings.authors(planModel.getUsers());

        if (planModel.getDescriptions() != null) {
            for (DescriptionModel descriptionModel : planModel.getDescriptions()) {
                for (SemanticsProperties.PathName relatedName : acceptedSemantics) {
                    List<org.opencdmp.commonmodels.models.planblueprintdescriptiontemplatefields.FieldModel> fieldsWithSemantics = this.findSchematicValues(relatedName.getName(), descriptionModel.getDescriptionTemplate().getDefinition().getAllField());
                    Set<String> values = extractSchematicValues(fieldsWithSemantics, descriptionModel.getProperties().getFieldSets());
                    if (!values.isEmpty()) pathToValuesMap.computeIfAbsent(relatedName.getPath(), k -> new LinkedHashSet<>()).addAll(values);
                }
            }
        }

        for (SemanticsProperties.PathName relatedName : acceptedSemantics) {
            List<org.opencdmp.commonmodels.models.planblueprintdescriptiontemplatefields.FieldModel> fieldsWithSemantics = this.findSchematicValues(relatedName.getName(), planModel.getPlanBlueprint().getDefinition().getAllField());
            Set<String> values = extractSchematicValues(fieldsWithSemantics, planModel.getProperties().getFieldSets());
            if (!values.isEmpty()) pathToValuesMap.computeIfAbsent(relatedName.getPath(), k -> new LinkedHashSet<>()).addAll(values);
        }

        for (Map.Entry<String, Set<String>> entry : pathToValuesMap.entrySet()) {
            String path = entry.getKey();
            Set<String> values = entry.getValue();

            if(!isPublished && !path.contains(SEMANTIC_PROVENANCE) && !path.contains(SEMANTIC_DATE_ACCESSIONED) && !path.contains(SEMANTIC_DATE_AVAILABLE)){
                if (path.contains(SEMANTIC_IDENTIFIER) || path.contains(SEMANTIC_TITLE_ALTERNATIVE)) {
                    entityList.addAll(this.buildListSemantic(values, DSPACE_OP_ADD, path));

                } else if (path.contains(SEMANTIC_TYPE)) {
                    List<String> allowedTypes = this.dspaceServiceProperties.getAcceptedTypeCodes() != null ? this.dspaceServiceProperties.getAcceptedTypeCodes() : List.of();
                    String otherType = allowedTypes.stream().filter(SEMANTIC_OTHER::equalsIgnoreCase).findFirst().orElse(SEMANTIC_OTHER);

                    Set<String> validatedValues = values.stream()
                            .map(v -> allowedTypes.stream().filter(v::equalsIgnoreCase).findFirst().orElse(otherType))
                            .collect(Collectors.toCollection(LinkedHashSet::new));

                    entityList.addAll(this.buildTypeListSemantic(defaultType, validatedValues, DSPACE_OP_ADD, path));
                } else if (path.contains(SEMANTIC_AUTHOR)) {
                    entityList.addAll(this.buildOwnerListSemantic(values, authors, DSPACE_OP_ADD, path));
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

            if(!hasType) entityList.addAll(this.buildSingleValue(defaultType, DSPACE_OP_ADD, SEMANTIC_TYPE_PATH));

            boolean hasLanguage = entityList.stream()
                    .anyMatch(entity -> entity.getPath().equals(SEMANTIC_LANGUAGE_PATH));

            if(!hasLanguage) entityList.addAll(this.buildSingleValue(planModel.getLanguage(), DSPACE_OP_ADD, SEMANTIC_LANGUAGE_PATH));

            boolean hasAuthors = entityList.stream()
                    .anyMatch(entity -> entity.getPath().equals(SEMANTIC_AUTHOR_PATH));

            if(!hasAuthors && !authors.isEmpty()) entityList.addAll(this.buildOwners(authors, DSPACE_OP_ADD, SEMANTIC_AUTHOR_PATH));

            entityList.addAll(this.buildDefault(entityList, settings.text(SETTING_PUBLISHER), SEMANTIC_PUBLISHER_PATH));
            entityList.addAll(this.buildDefault(entityList, settings.text(SETTING_RIGHTS), SEMANTIC_RIGHTS_PATH));
            entityList.addAll(this.buildDefault(entityList, settings.text(SETTING_RIGHTS_URI), SEMANTIC_RIGHTS_URI_PATH));
        }

        return entityList;
    }

    private List<PatchEntity> buildDefault(List<PatchEntity> entityList, String value, String path) {
        if (value == null || entityList.stream().anyMatch(entity -> entity.getPath().equals(path))) return List.of();
        return this.buildSemantic(value, DSPACE_OP_ADD, path);
    }

}

