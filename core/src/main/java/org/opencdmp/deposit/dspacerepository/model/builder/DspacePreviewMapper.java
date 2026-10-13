package org.opencdmp.deposit.dspacerepository.model.builder;

import org.opencdmp.commonmodels.enums.PreviewFieldSource;
import org.opencdmp.commonmodels.enums.PreviewFieldType;
import org.opencdmp.commonmodels.models.deposit.*;
import org.opencdmp.deposit.dspacerepository.configuration.semantics.SemanticsProperties;
import org.opencdmp.deposit.dspacerepository.model.PatchEntity;
import org.opencdmp.deposit.dspacerepository.service.dspace.DspaceServiceProperties;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)

public class DspacePreviewMapper {
    private final SemanticsProperties semanticsProperties;
    private final DspaceServiceProperties dspaceServiceProperties;
    private static final String GROUP_FILES_CODE = "files";
    private static final String GROUP_BASIC_INFORMATION = "basic-information";
    private static List<String> acceptablePaths;

    public DspacePreviewMapper(SemanticsProperties semanticsProperties, DspaceServiceProperties dspaceServiceProperties) {
        this.semanticsProperties = semanticsProperties;
        this.dspaceServiceProperties = dspaceServiceProperties;
        acceptablePaths = new ArrayList<>(
                this.semanticsProperties.getAvailable().stream()
                        .map(SemanticsProperties.PathName::getPath)
                        .toList()
        );
        List<String> hardcodedPaths = List.of("/sections/publicationStep/dc.title", "/sections/publicationStep/dc.date.issued", "/sections/traditionalpagetwo/dc.description.abstract", "/sections/publicationStep/dc.identifier.uri");
        acceptablePaths.addAll(hardcodedPaths);
    }

    public DepositPreviewModel toPreview(List<PatchEntity> entities, List<String> uploadFileNames) {
        DepositPreviewModel depositPreviewModel = new DepositPreviewModel();
        depositPreviewModel.setGroups(new ArrayList<>());
        List<String> consumedPaths = new ArrayList<>();

        depositPreviewModel.getGroups().add(buildAuthorGroup(entities, List.of("/sections/publicationStep/dc.contributor.author"), consumedPaths)); // done
        depositPreviewModel.getGroups().add(buildAlternativeTitleGroup(entities, List.of("/sections/publicationStep/dc.title.alternative"), consumedPaths)); // done
        depositPreviewModel.getGroups().add(buildIdentifierGroup(entities, List.of("/sections/publicationStep/dc.identifier.*"), consumedPaths)); // done
        depositPreviewModel.getGroups().add(buildTypeGroup(entities, List.of("/sections/publicationStep/dc.type"), consumedPaths)); // done
        depositPreviewModel.getGroups().add(buildFilesGroup(uploadFileNames));
        depositPreviewModel.getGroups().add(buildBasicGroup(entities, acceptablePaths, consumedPaths));
        PreviewFieldGroup removed = depositPreviewModel.getGroups().removeLast();
        depositPreviewModel.getGroups().addFirst(removed);
        return depositPreviewModel;
    }

    private PreviewFieldGroup buildIdentifierGroup(List<PatchEntity> entities, List<String> filterPaths, List<String> consumedPaths) {
        PreviewFieldGroup previewFieldGroup = buildGroup("identifierGroup", "Identifiers");
        previewFieldGroup.setRepeatable(true);
        List<PatchEntity> patchEntityList = filterPatchEntities(entities, filterPaths);
        List<String> uniqueEntityPaths = patchEntityList.stream().map(PatchEntity::getPath).distinct().toList();
        List<String> uniqueEndings = uniqueEntityPaths.stream().map(x -> x.substring(x.lastIndexOf('.') + 1)).toList();
        consumedPaths.addAll(uniqueEntityPaths);
        List<PreviewGroupItemModel> items = new ArrayList<>();
        Map<String, List<PatchEntity>> mappedPatchEntities = patchEntityList.stream()
                .collect(Collectors.groupingBy(PatchEntity::getPath));
        PreviewField typeField = buildTemplateField("identifierType", "Identifier Type");
        typeField.setType(PreviewFieldType.Select);
        typeField.setOptions(toOptions(uniqueEndings)); // need fix this slop but work
        PreviewField textField = buildTemplateField("identifierValue", "Identifier Value");
        textField.setType(PreviewFieldType.Text);
        textField.setRequired(false);
        List<PreviewField> groupFields = new ArrayList<>(List.of(typeField, textField));
        for (String uniquePath : uniqueEntityPaths) {
            String formattedName = uniquePath.substring(uniquePath.lastIndexOf('.') + 1);
            List<PatchEntity> entries = mappedPatchEntities.get(uniquePath);
            for (PatchEntity entry : entries) {
                switch (entry.getOp()) {
                    case "add": {
                        List<String> values = resolveValue(entry.getValue());
                        for (String value : values) {
                            PreviewGroupItemModel previewGroupItemModel = new PreviewGroupItemModel();
                            previewGroupItemModel.setValues(List.of(textValue("identifierType", formattedName), textValue("identifierValue", value)));
                            items.add(previewGroupItemModel);
                        }
                        break;
                    }
                    default: {
                        throw new RuntimeException("Unknown op case");
                    }
                }
            }
        }
        previewFieldGroup.setFields(groupFields);
        previewFieldGroup.setItems(items);
        return previewFieldGroup;
    }

    private PreviewFieldGroup buildTypeGroup(List<PatchEntity> entities, List<String> filterPaths, List<String> consumedPaths) { // this is actually complete do not touch
        PreviewFieldGroup previewFieldGroup = buildGroup("typeGroup", "Types");
        previewFieldGroup.setRepeatable(true);
        consumedPaths.addAll(filterPaths);
        List<PatchEntity> patchEntityList = filterPatchEntities(entities, filterPaths);
        List<PreviewField> groupFields = new ArrayList<>();
        List<PreviewGroupItemModel> items = new ArrayList<>();
        for (PatchEntity patchEntity : patchEntityList) {
            switch (patchEntity.getOp()) {
                case "add": {
                    String patchPath = patchEntity.getPath();
                    String formattedName = patchPath.substring(patchPath.lastIndexOf('.') + 1);
                    String labelName = Character.toUpperCase(formattedName.charAt(0)) + formattedName.substring(1);
                    PreviewField previewField = buildTemplateField(formattedName, labelName);
                    previewField.setType(PreviewFieldType.Select);
                    previewField.setOptions(toOptions(dspaceServiceProperties.getAcceptedTypeCodes()));
                    previewField.setVocabSearch(true);
                    previewField.setRequired(true);
                    List<String> values = resolveValue(patchEntity.getValue());
                    for (String value : values) {
                        PreviewGroupItemModel previewGroupItemModel = new PreviewGroupItemModel();
                        previewGroupItemModel.setValues(List.of(textValue(formattedName, value)));
                        items.add(previewGroupItemModel);
                    }
                    groupFields.add(previewField);
                    break;
                }
                default: {
                    throw new RuntimeException("Unknown op case");
                }
            }
        }
        previewFieldGroup.setFields(groupFields);
        previewFieldGroup.setItems(items);
        return previewFieldGroup;
    }

    private PreviewFieldGroup buildAlternativeTitleGroup(List<PatchEntity> entities, List<String> filterPaths, List<String> consumedPaths) {
        PreviewFieldGroup previewFieldGroup = buildGroup("alternativeTitleGroup", "Alternative Titles");
        previewFieldGroup.setRepeatable(true);
        consumedPaths.addAll(filterPaths);
        List<PatchEntity> patchEntityList = filterPatchEntities(entities, filterPaths);
        List<PreviewField> groupFields = new ArrayList<>();
        List<PreviewGroupItemModel> items = new ArrayList<>();
        for (PatchEntity patchEntity : patchEntityList) {
            switch (patchEntity.getOp()) {
                case "add": {
                    String patchPath = patchEntity.getPath();
                    String formattedName = patchPath.substring(patchPath.lastIndexOf('.') + 1);
                    String labelName = Character.toUpperCase(formattedName.charAt(0)) + formattedName.substring(1);
                    PreviewField previewField = buildTemplateField(formattedName, labelName);
                    previewField.setType(PreviewFieldType.Text);
                    List<String> values = resolveValue(patchEntity.getValue());
                    for (String value : values) {
                        PreviewGroupItemModel previewGroupItemModel = new PreviewGroupItemModel();
                        previewGroupItemModel.setValues(List.of(textValue(formattedName, value)));
                        items.add(previewGroupItemModel);
                    }
                    groupFields.add(previewField);
                    break;
                }
                default: {
                    throw new RuntimeException("Unknown op case");
                }
            }
        }
        previewFieldGroup.setItems(items);
        previewFieldGroup.setFields(groupFields);
        return previewFieldGroup;
    }

    private PreviewFieldGroup buildAuthorGroup(List<PatchEntity> entities, List<String> filterPaths, List<String> consumedPaths) {
        PreviewFieldGroup previewFieldGroup = buildGroup("authorGroup", "Authors");
        previewFieldGroup.setRepeatable(true);
        consumedPaths.addAll(filterPaths);
        List<PatchEntity> patchEntityList = filterPatchEntities(entities, filterPaths);
        List<PreviewField> groupFields = new ArrayList<>();
        List<PreviewGroupItemModel> items = new ArrayList<>();
        for (PatchEntity patchEntity : patchEntityList) {
            switch (patchEntity.getOp()) {
                case "add": {
                    String patchPath = patchEntity.getPath();
                    String formattedName = patchPath.substring(patchPath.lastIndexOf('.') + 1);
                    String labelName = Character.toUpperCase(formattedName.charAt(0)) + formattedName.substring(1);
                    PreviewField previewField = buildTemplateField("contributor"+labelName, labelName);
                    previewField.setType(PreviewFieldType.Text);
                    previewField.setVocabSearch(true);
                    previewField.setRequired(false);
                    List<String> values = resolveValue(patchEntity.getValue());
                    for (String value : values) {
                        PreviewGroupItemModel previewGroupItemModel = new PreviewGroupItemModel();
                        previewGroupItemModel.setValues(Collections.singletonList(textValue("contributor"+labelName, value))); // hardcoded because lazy
                        items.add(previewGroupItemModel);
                    }
                    groupFields.add(previewField);
                    break;
                }
                default: {
                    throw new RuntimeException("Unknown op case");
                }
            }
        }
        previewFieldGroup.setItems(items);
        previewFieldGroup.setFields(groupFields);
        return previewFieldGroup;
    }

    private List<String> grabAppropriatePaths(List<String> nameList) {
        List<String> validPaths = new ArrayList<>();
        for (String name : nameList) {
            for (String acceptablePath : acceptablePaths) {
                int wildcardIndex = name.indexOf('*');
                if (acceptablePath.contains(name) || ((wildcardIndex != -1) && acceptablePath.startsWith(name.substring(0, wildcardIndex))))
                    validPaths.add(acceptablePath);
            }
        }
        return validPaths;
    }


    private List<PatchEntity> filterPatchEntities(List<PatchEntity> patchEntityList, List<String> toFilterList) {
        List<String> filteredPaths = grabAppropriatePaths(toFilterList);
        List<PatchEntity> filteredPatchEntitiesList = new ArrayList<>();
        for (PatchEntity patch : patchEntityList) {
            if (filteredPaths.contains(patch.getPath())) { // existsPathName
                filteredPatchEntitiesList.add(patch);
            }
        }
        return filteredPatchEntitiesList;
    }


    private PreviewFieldGroup buildBasicGroup(List<PatchEntity> entities, List<String> toFilterList, List<String> consumedPaths) {
        // there is issue with this because of bad configuration if qualifier is same and there is entry with that and 2 different values it will pick the last one
        PreviewFieldGroup group = this.buildGroup(GROUP_BASIC_INFORMATION, "Basic information");
        List<PreviewField> groupFields = new ArrayList<>();
        toFilterList.removeAll(consumedPaths);
        List<PatchEntity> filteredEntities = filterPatchEntities(entities, toFilterList);
        for (PatchEntity patchEntity : filteredEntities) {
            switch (patchEntity.getOp()) {
                case "add": {
                    String[] pathParts = patchEntity.getPath().substring(patchEntity.getPath().indexOf("dc")).split("\\.");
                    int metadataStart = "dc".equals(pathParts[0]) ? 1 : 0;
                    String element = pathParts[metadataStart]; // The element name, e.g. "identifier"
                    String qualifier = pathParts.length > metadataStart + 1
                            ? pathParts[metadataStart + 1]
                            : null;
                    String labelName = Character.toUpperCase(element.charAt(0)) + element.substring(1);
                    switch (element) {
                        case "title" -> {
                            PreviewField previewField = buildTemplateField(element, labelName);
                            previewField.setRequired(true);
                            List<String> values = resolveValue(patchEntity, element, null);
                            for (String value : values) {
                                previewField.setValue(textValue(element, value));
                            }
                            groupFields.add(previewField);

                        }
                        case "date" -> {
                            switch (Objects.requireNonNull(qualifier)) {
                                case "issued" -> {
                                    String Uppercase = Character.toUpperCase(qualifier.charAt(0)) + qualifier.substring(1);
                                    PreviewField previewField = buildTemplateField(element + "." + qualifier, Uppercase, PreviewFieldType.Date);
                                    List<String> values = resolveValue(patchEntity, element, qualifier);
                                    for (String value : values) {
                                        previewField.setValue(dateValue(element+"."+qualifier, value));
                                    }
                                    groupFields.add(previewField);
                                }
                            }
                        }
                        case "description" -> {
                            switch (qualifier) {
                                case "sponsorship", "abstract" -> {
                                    String UppercaseQualifier = Character.toUpperCase(qualifier.charAt(0)) + qualifier.substring(1);
                                    PreviewField previewField = buildTemplateField(element + UppercaseQualifier, UppercaseQualifier, PreviewFieldType.TextArea);
                                    List<String> values = resolveValue(patchEntity, element, qualifier);
                                    for (String value : values) {
                                        previewField.setValue(textValue(element+UppercaseQualifier, value));
                                    }
                                    groupFields.add(previewField);
                                }
                                case null -> {
                                    PreviewField previewField = buildTemplateField(element, labelName, PreviewFieldType.TextArea);
                                    List<String> values = resolveValue(patchEntity, element, null);
                                    for (String value : values) {
                                        previewField.setValue(textValue(element, value));
                                    }
                                    groupFields.add(previewField);
                                }
                                default -> throw new IllegalStateException("Unexpected value: " + qualifier);
                            }

                        }
                        case "publisher" -> {
                            PreviewField previewField = buildTemplateField(element, labelName);
                            previewField.setRequired(false);
                            List<String> values = resolveValue(patchEntity, element,null);
                            for (String value : values) {
                                previewField.setValue(textValue(element, value));
                            }
                            groupFields.add(previewField);
                        }
                        case "language" -> {
                            PreviewField previewField = buildTemplateField(element, labelName, PreviewFieldType.Text);
                            previewField.setVocabSearch(true);
                            switch (qualifier) {
                                case "iso" -> {
                                    String Uppercase = Character.toUpperCase(qualifier.charAt(0)) + qualifier.substring(1);
                                    previewField.setCode(element + Uppercase);
                                    List<String> values = resolveValue(patchEntity, element);
                                    for (String value : values) {
                                        previewField.setValue(textValue(element+Uppercase, value));
                                    }
                                    groupFields.add(previewField);
                                }
                                case null -> { // non iso
                                    List<String> values = resolveValue(patchEntity, element);
                                    for (String value : values) {
                                        previewField.setValue(textValue(element, value));
                                    }
                                    groupFields.add(previewField);
                                }
                                default -> throw new IllegalStateException("Unexpected value: " + qualifier);
                            }
                        }
                    }
                    break;
                }
                default: {
                    throw new RuntimeException("Unknown op case");
                }
            }
        }
        group.setFields(groupFields);
        return group;
    }

    private PreviewField buildTemplateField(String code, String label) {
        PreviewField field = new PreviewField();
        field.setCode(code);
        field.setLabel(label);
        field.setSource(PreviewFieldSource.Mapped);
        return field;
    }

    private List<String> resolveValue(List<Map<String, Object>> value) {
        return value.stream().map(x -> x.get("value").toString()).toList();
    }


    private List<String> resolveValue(PatchEntity patchEntity, String element) {
        String[] pathParts = patchEntity.getPath().substring(patchEntity.getPath().indexOf("dc")).split("\\.");
        int metadataStart = "dc".equals(pathParts[0]) ? 1 : 0;
        if (pathParts.length <= metadataStart
                || !Objects.equals(pathParts[metadataStart], element)) {
            return List.of();
        }
        return patchEntity.getValue().stream()
                .map(item -> Objects.toString(item.get("value"), null))
                .filter(Objects::nonNull)
                .toList();
    }


    private List<String> resolveValue(PatchEntity patchEntity, String element, String qualifier) {
        String[] pathParts = patchEntity.getPath().substring(patchEntity.getPath().indexOf("dc")).split("\\.");
        int metadataStart = "dc".equals(pathParts[0]) ? 1 : 0;
        if (pathParts.length <= metadataStart
                || !Objects.equals(pathParts[metadataStart], element)) {
            return List.of();
        }
        String pathQualifier = pathParts.length > metadataStart + 1
                ? pathParts[metadataStart + 1]
                : null;
        if (!Objects.equals(pathQualifier, qualifier)) {
            return List.of();
        }
        return patchEntity.getValue().stream()
                .map(item -> Objects.toString(item.get("value"), null))
                .filter(Objects::nonNull)
                .toList();
    }

    public void applyOverrides(List<PatchEntity> entities, List<PreviewFieldValue> editedFields, List<PreviewGroupValuesModel> editedGroups) {
        applyGroupOverrides(entities,editedGroups);
        List<PatchEntity> consumedEntitiesInEditedFields = new ArrayList<>();
        for(PreviewFieldValue previewFieldValue: editedFields)
        {
            PatchEntity patchEntity = resolvePatchEntity(entities, previewFieldValue.getCode());
            if(patchEntity == null) {
                patchEntity = new PatchEntity();
                patchEntity.setPath(resolvePathFromCode(previewFieldValue.getCode()));
                applyPatchEntityOverride(patchEntity, List.of(previewFieldValue));
                entities.add(patchEntity);
            }
            else applyPatchEntityOverride(patchEntity,List.of(previewFieldValue));
            consumedEntitiesInEditedFields.add(patchEntity);
        }
        for(PatchEntity patchEntity : entities)
        {
            if(consumedEntitiesInEditedFields.stream().noneMatch(patchEntity1 -> patchEntity1.equals(patchEntity)))
            {
                entities.remove(patchEntity);
            }
        }
    }

    private void applyGroupOverrides(List<PatchEntity> entities,List<PreviewGroupValuesModel> editedGroups) {
        for(PreviewGroupValuesModel previewGroupModel: editedGroups)
        {
            List<PatchEntity> entitiesConsumedInGroup = new ArrayList<>();
            for(PreviewGroupItemModel previewGroupItemModel: previewGroupModel.getItems())
            {
                PreviewFieldValue first = previewGroupItemModel.getValues().getFirst();
                // get code // resolve entity // throw whole previewGroupItemModel.getValues at it
               // for(PreviewFieldValue previewFieldValue: previewGroupItemModel.getValues())
               // {
                if(first==null) throw new RuntimeException("amogu");
                    PatchEntity foundEntity = resolvePatchEntity(entities,first.getCode());
                    if(foundEntity!=null) applyPatchEntityOverride(foundEntity,previewGroupItemModel.getValues());
                    else {
                        foundEntity = new PatchEntity();
                        foundEntity.setPath(resolvePathFromCode(first.getCode()));
                        applyPatchEntityOverride(foundEntity,previewGroupItemModel.getValues());
                        entities.add(foundEntity);
                    } // this doesnt make sense path is not provided in editedValues and cannot be infered in any way
                    entitiesConsumedInGroup.add(foundEntity);
                //}

            }
            for(PatchEntity patchEntity : entities)
            {
                if(entitiesConsumedInGroup.stream().noneMatch(x -> x.equals(patchEntity)))
                {
                    entities.remove(patchEntity);
                }
            }
        }
    }

    private PatchEntity resolvePatchEntity(List<PatchEntity> entities, String fieldCode) {

        return entities.stream().filter(x->
        {
            String[] pathParts = x.getPath().substring(x.getPath().indexOf("dc")).split("\\.");
            int metadataStart = "dc".equals(pathParts[0]) ? 1 : 0;
            String element = pathParts[metadataStart]; // The element name, e.g. "identifier"
            String qualifier = pathParts.length > metadataStart + 1
                ? pathParts[metadataStart + 1]
                : null;
            String uppercaseQualifier = qualifier!=null? Character.toUpperCase(qualifier.charAt(0)) + qualifier.substring(1) : null;
            String finalToCompare = uppercaseQualifier !=null ? element+ uppercaseQualifier : element;
            return finalToCompare.equals(fieldCode);
        }
            ).findFirst().orElse(null);
    }


    private static List<String> getSetFieldNames(PreviewFieldValue value)
            throws IllegalAccessException {
        List<String> names = new ArrayList<>();

        for (Field field : PreviewFieldValue.class.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers())) {
                continue;
            }
            if (!field.trySetAccessible()) {
                throw new IllegalAccessException(
                        "Cannot access field: " + field.getName()
                );
            }
            if (field.get(value) != null) {
                names.add(field.getName());
            }
        }
        return names;
    }



    private String resolvePathFromCode(String code) {
        String[] parts = code.split("(?=[A-Z])", 2);
        String constructedPath = "dc." + Arrays.stream(parts)
                .filter(part -> !part.isEmpty())
                .map(part -> part.toLowerCase(Locale.ROOT))
                .collect(Collectors.joining("."));

        return acceptablePaths.stream()
                .filter(path -> path.contains(constructedPath))
                .findFirst()
                .orElse(null);
    }


    private void applyPatchEntityOverride(PatchEntity patchEntity, List<PreviewFieldValue> previewFieldValueList,String operator)
    {
        patchEntity.setOp(operator);
        applyPatchEntityOverride(patchEntity,previewFieldValueList);
    }

    private void applyPatchEntityOverride(PatchEntity patchEntity, List<PreviewFieldValue> previewFieldValueList)
    {
        // hmmm maybe we need to pass list of previewfieldvalues
        if(patchEntity.getOp() == null || patchEntity.getOp().isEmpty()) patchEntity.setOp("add");
        if(patchEntity.getPath() == null) return;

        List<Map<String,Object>> constructedValues = List.of();
        for(PreviewFieldValue previewFieldValue1: previewFieldValueList)
        {
            // idk construct here

            getValueFromPreviewFieldValue(previewFieldValue1);
        }
        patchEntity.setValue(constructedValues);
    }

    private Object getValueFromPreviewFieldValue(PreviewFieldValue previewFieldValue)
    {
        try {
            for(String name: getSetFieldNames(previewFieldValue)) {
                return switch (name) {
                    case "textValue"-> previewFieldValue.getTextValue();
                    case "textListValue" -> previewFieldValue.getTextListValue();
                    case "booleanValue" -> previewFieldValue.getBooleanValue();
                    case "numberValue" -> previewFieldValue.getNumberValue();
                    case "dateValue" -> previewFieldValue.getDateValue();
                    default -> throw new IllegalStateException("Unexpected value: " + name);
                };
            }
        } catch (IllegalAccessException e) {
            return null;
        }
        return null;
    }


    private PreviewFieldGroup buildFilesGroup(List<String> uploadFileNames) {
        PreviewFieldGroup group = this.buildGroup(GROUP_FILES_CODE, "Files");
        group.getFields().add(this.buildField(GROUP_FILES_CODE, "Files to be uploaded", PreviewFieldType.ReadOnly, PreviewFieldSource.System, false,
                () -> this.textListValue(GROUP_FILES_CODE, uploadFileNames)));
        return group;
    }


    private PreviewFieldGroup buildGroup(String code, String label) {
        PreviewFieldGroup group = new PreviewFieldGroup();
        group.setCode(code);
        group.setLabel(label);
        group.setFields(new ArrayList<>());
        return group;
    }

    private PreviewField buildField(String code, String label, PreviewFieldType type, PreviewFieldSource source, boolean required, Supplier<PreviewFieldValue> valueSupplier) {
        PreviewField field = new PreviewField();
        field.setCode(code);
        field.setLabel(label);
        field.setType(type);
        field.setSource(source);
        field.setRequired(required);
        field.setValue(valueSupplier.get());
        field.setBaseValue(valueSupplier.get());
        return field;
    }

    private PreviewFieldValue textValue(String code, String text) {
        PreviewFieldValue value = new PreviewFieldValue();
        value.setCode(code);
        value.setTextValue(text);
        return value;
    }

    private PreviewFieldValue booleanValue(String code, String string) {
        PreviewFieldValue value = new PreviewFieldValue();
        value.setCode(code);
        value.setBooleanValue(Boolean.getBoolean(string));
        return value;
    }

    private PreviewFieldValue textListValue(String code, List<String> texts) {
        PreviewFieldValue value = new PreviewFieldValue();
        value.setCode(code);
        value.setTextListValue(texts != null && !texts.isEmpty() ? texts : null);
        return value;
    }


    private PreviewFieldValue dateValue(String code, String formattedDate) {
        PreviewFieldValue value = new PreviewFieldValue();
        value.setCode(code);
        if (formattedDate != null && !formattedDate.isBlank()) {
            try {
                value.setDateValue(LocalDate.parse(formattedDate).atStartOfDay(ZoneOffset.UTC).toInstant());
            } catch (Exception e) {
                // leave the value empty if the mapped date is not in yyyy-MM-dd form
            }
        }
        return value;
    }

    private PreviewField buildTemplateField(String code, String label, PreviewFieldType type) {
        PreviewField field = new PreviewField();
        field.setCode(code);
        field.setLabel(label);
        field.setType(type);
        field.setSource(PreviewFieldSource.Mapped);
        return field;
    }


    private static List<PreviewFieldOption> toOptions(List<String> vocabularyLabels) {
        if (vocabularyLabels == null) return List.of();
        return vocabularyLabels.stream()
                .filter(label -> label != null && !label.isBlank())
                .map(label -> new PreviewFieldOption(label, label))
                .toList();
    }

    public List<PreviewFieldOption> searchVocabulary(String fieldCode, String like) {
        return List.of();
//        this.dspaceServiceProperties.getAcceptedTypeCodes();
//        List<PreviewFieldOption>
//        Object values = switch (fieldCode) {
//            case FIELD_SUBJECT -> getSchemaVocabularyValues(schema, "subject");
//            case FIELD_PUBLICATION_RELATION_TYPE -> getSchemaVocabularyValues(schema, "publication", FIELD_PUBLICATION_RELATION_TYPE);
//            case FIELD_PUBLICATION_ID_TYPE -> getSchemaVocabularyValues(schema, "publication", FIELD_PUBLICATION_ID_TYPE);
//            default -> null;
//        };
//        List<String> vocabularyLabels = values instanceof List ? (List<String>) values : List.of();
//        List<PreviewFieldOption> options = toOptions(vocabularyLabels);
//        if (like == null || like.isBlank()) return options;
//
//        String term = like.trim().toLowerCase(Locale.ROOT);
//        return options.stream().filter(option -> option.getLabel().toLowerCase(Locale.ROOT).contains(term)).toList();
//
    }
}


