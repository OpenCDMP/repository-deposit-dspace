package org.opencdmp.deposit.dspacerepository.model.builder;

import gr.cite.tools.logging.LoggerService;
import gr.cite.tools.logging.MapLogEntry;
import org.opencdmp.commonmodels.enums.PreviewFieldSource;
import org.opencdmp.commonmodels.enums.PreviewFieldType;
import org.opencdmp.commonmodels.models.deposit.*;
import org.opencdmp.deposit.dspacerepository.model.PatchEntity;
import org.opencdmp.deposit.dspacerepository.model.form.DspaceSubmissionForm;
import org.opencdmp.deposit.dspacerepository.model.form.DspaceSubmissionForm.Field;
import org.opencdmp.deposit.dspacerepository.model.form.DspaceSubmissionForm.Metadata;
import org.opencdmp.deposit.dspacerepository.model.form.DspaceSubmissionForm.Section;
import org.opencdmp.depositbase.repository.DepositPluginSettings;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Builds the review from the collection's submission form ({@link DspaceSubmissionForm}) and applies the user's
 * review edits back onto the submission patch.
 * <p>
 * Every field of the form is one review field, or one repeatable group when it holds a list of free values or lets
 * the user pick the metadata key of each value (the default form's "Identifiers"). Codes are the metadata keys with
 * "_" for "." (e.g. "dc_title"). A DSpace "add" on a submission path replaces every value of that key, so the patch
 * carries one entity per key and an override replaces that entity.
 */
@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class DspacePreviewMapper {
    private static final LoggerService logger = new LoggerService(LoggerFactory.getLogger(DspacePreviewMapper.class));

    private static final String DSPACE_OP_ADD = "add";
    private static final String SECTIONS_PREFIX = "/sections/";
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneOffset.UTC);

    public static final String GROUP_FILES_CODE = "files";
    public static final String FIELD_FILES = "files";
    /** The columns of a repeatable group: the value, and the metadata key when the field lets the user pick it. */
    public static final String ITEM_VALUE = "value";
    public static final String ITEM_METADATA = "metadata";

    /** Fields the organisation can lock, by the review-locked option that locks each. */
    private static final Map<String, String> LOCKABLE_KEYS = Map.of(
            "dc.type", "item-type",
            "dc.publisher", "publisher",
            "dc.rights", "rights",
            "dc.rights.uri", "rights-uri");

    // ---- form alignment ----

    /**
     * Fits the built patch to the form: each value goes to the section the form has its key in, keys the form does
     * not have are left out (DSpace rejects the whole patch otherwise), values a select field's vocabulary does not
     * have are left out, and the values of a key are sent in one "add", all of them for a repeatable field and the
     * first one otherwise.
     */
    public List<PatchEntity> alignToForm(List<PatchEntity> entities, DspaceSubmissionForm form) {
        List<PatchEntity> aligned = new ArrayList<>();
        Map<String, List<String>> valuesByKey = new LinkedHashMap<>();
        Map<String, Integer> positions = new HashMap<>();
        List<String> dropped = new ArrayList<>();
        Map<String, List<String>> notInVocabulary = new LinkedHashMap<>();
        for (PatchEntity entity : entities) {
            if (entity.getPath() == null || !entity.getPath().startsWith(SECTIONS_PREFIX)) {
                aligned.add(entity);
                continue;
            }
            String key = metadataKey(entity.getPath());
            if (form.fieldByKey(key) == null) {
                if (!dropped.contains(key)) dropped.add(key);
                continue;
            }
            if (!positions.containsKey(key)) {
                positions.put(key, aligned.size());
                aligned.add(null); // the key's place, filled below
            }
            List<String> values = valuesByKey.computeIfAbsent(key, k -> new ArrayList<>());
            for (String value : entryValues(entity)) {
                String option = optionValue(form.fieldByKey(key), value);
                if (option == null) notInVocabulary.computeIfAbsent(key, k -> new ArrayList<>()).add(value);
                else if (!values.contains(option)) values.add(option);
            }
        }
        if (!dropped.isEmpty()) logger.warn(new MapLogEntry("Values left out: the collection's submission form has no field for them").And("metadata", dropped).And("fallbackForm", form.isFallback()));
        if (!notInVocabulary.isEmpty()) logger.warn(new MapLogEntry("Values left out: the field's vocabulary has no such option").And("values", notInVocabulary).And("fallbackForm", form.isFallback()));

        for (Map.Entry<String, List<String>> entry : valuesByKey.entrySet()) {
            Field field = form.fieldByKey(entry.getKey());
            aligned.set(positions.get(entry.getKey()), entry.getValue().isEmpty() ? null : entity(field.path(entry.getKey()), entry.getValue(), field.isRepeatable()));
        }
        aligned.removeIf(Objects::isNull);
        return aligned;
    }

    // ---- preview ----

    public DepositPreviewModel toPreview(List<PatchEntity> entities, DspaceSubmissionForm form, List<String> uploadFileNames, DepositPluginSettings settings) {
        DepositPreviewModel model = new DepositPreviewModel();
        List<PreviewFieldGroup> groups = new ArrayList<>();
        if (uploadFileNames != null && !uploadFileNames.isEmpty()) groups.add(this.buildFilesGroup(uploadFileNames));
        for (Section section : form.getSections()) {
            if (settings.isHidden(section.getId())) continue;
            PreviewFieldGroup sectionGroup = this.buildGroup(section.getId(), section.getLabel());
            List<PreviewFieldGroup> repeatableGroups = new ArrayList<>();
            for (Field field : section.getFields()) {
                if (this.isHidden(field, settings)) continue;
                if (field.isGroup()) repeatableGroups.add(this.buildRepeatableGroup(field, entities));
                else sectionGroup.getFields().add(this.buildField(field, entities, settings));
            }
            if (!sectionGroup.getFields().isEmpty()) groups.add(sectionGroup);
            groups.addAll(repeatableGroups);
        }
        model.setGroups(groups);
        return model;
    }

    private PreviewField buildField(Field field, List<PatchEntity> entities, DepositPluginSettings settings) {
        String key = field.getKeys().getFirst();
        List<String> values = this.values(entities, key);

        PreviewField previewField = new PreviewField();
        previewField.setCode(field.getCode());
        previewField.setLabel(field.getLabel());
        previewField.setDescription(field.getHint());
        previewField.setRequired(field.isMandatory());
        previewField.setSource(this.isLocked(key, settings) ? PreviewFieldSource.System : PreviewFieldSource.Mapped);

        PreviewFieldValue value;
        if (field.hasOptions()) {
            previewField.setType(PreviewFieldType.Select);
            previewField.setMultipleSelect(field.isRepeatable());
            previewField.setOptions(field.getOptions());
            value = field.isRepeatable() ? this.textListValue(field.getCode(), values) : this.textValue(field.getCode(), first(values));
        } else if (DspaceSubmissionForm.INPUT_TAG.equals(field.getInputType())) {
            previewField.setType(PreviewFieldType.Tags);
            value = this.textListValue(field.getCode(), values);
        } else if (DspaceSubmissionForm.INPUT_DATE.equals(field.getInputType())) {
            previewField.setType(PreviewFieldType.Date);
            value = this.dateValue(field.getCode(), first(values));
        } else {
            previewField.setType(DspaceSubmissionForm.INPUT_TEXTAREA.equals(field.getInputType()) ? PreviewFieldType.TextArea : PreviewFieldType.Text);
            value = this.textValue(field.getCode(), first(values));
        }
        previewField.setValue(value);
        previewField.setBaseValue(this.copy(value));
        return previewField;
    }

    private PreviewFieldGroup buildRepeatableGroup(Field field, List<PatchEntity> entities) {
        PreviewFieldGroup group = this.buildGroup(field.getCode(), field.getLabel());
        group.setRepeatable(true);
        group.setRequired(field.isMandatory());
        List<PreviewGroupItemModel> items = new ArrayList<>();
        PreviewFieldType valueType = DspaceSubmissionForm.INPUT_TEXTAREA.equals(field.getInputType()) ? PreviewFieldType.TextArea : PreviewFieldType.Text;
        if (field.isQualified()) {
            PreviewField metadata = this.buildTemplateField(ITEM_METADATA, "Type", PreviewFieldType.Select);
            metadata.setOptions(field.getMetadata().stream().map(item -> new PreviewFieldOption(item.getLabel() != null ? item.getLabel() : item.getKey(), item.getKey())).toList());
            metadata.setRequired(true);
            group.getFields().add(metadata);
            group.getFields().add(this.buildTemplateField(ITEM_VALUE, "Value", valueType));
            for (String key : field.getKeys()) {
                for (String value : this.values(entities, key)) items.add(this.item(this.textValue(ITEM_METADATA, key), this.textValue(ITEM_VALUE, value)));
            }
        } else {
            PreviewField value = this.buildTemplateField(ITEM_VALUE, field.getLabel(), valueType);
            value.setDescription(field.getHint());
            value.setRequired(field.isMandatory());
            group.getFields().add(value);
            for (String text : this.values(entities, field.getKeys().getFirst())) items.add(this.item(this.textValue(ITEM_VALUE, text)));
        }
        group.setItems(items);
        group.setBaseItems(items);
        return group;
    }

    private PreviewFieldGroup buildFilesGroup(List<String> uploadFileNames) {
        PreviewFieldGroup group = this.buildGroup(GROUP_FILES_CODE, "Files");
        PreviewField files = this.buildTemplateField(FIELD_FILES, "Files to be uploaded", PreviewFieldType.ReadOnly);
        files.setSource(PreviewFieldSource.System);
        files.setValue(this.textListValue(FIELD_FILES, uploadFileNames));
        files.setBaseValue(this.textListValue(FIELD_FILES, uploadFileNames));
        group.getFields().add(files);
        return group;
    }

    /** Hidden by its section, its group or field code, or its metadata key. */
    private boolean isHidden(Field field, DepositPluginSettings settings) {
        return settings.isHidden(field.getCode()) || field.getKeys().stream().anyMatch(settings::isHidden);
    }

    private boolean isLocked(String key, DepositPluginSettings settings) {
        return settings != null && LOCKABLE_KEYS.containsKey(key) && settings.isLocked(LOCKABLE_KEYS.get(key));
    }

    // ---- overrides ----

    /** Applies the review edits onto the aligned workspace item patch; codes the form does not have are ignored. */
    public void applyOverrides(List<PatchEntity> entities, DspaceSubmissionForm form, List<PreviewFieldValue> editedFields, List<PreviewGroupValuesModel> editedGroups, DepositPluginSettings settings) {
        if (entities == null || form == null) return;
        if (editedFields != null) {
            for (PreviewFieldValue value : editedFields) {
                Field field = value != null ? form.fieldByCode(value.getCode()) : null;
                if (field == null || field.isGroup() || this.isLocked(field.getKeys().getFirst(), settings)) continue;
                this.applyFieldOverride(entities, field, value);
            }
        }
        if (editedGroups != null) {
            for (PreviewGroupValuesModel group : editedGroups) {
                Field field = group != null ? form.fieldByCode(group.getGroupCode()) : null;
                if (field == null || !field.isGroup()) continue;
                if (field.isQualified()) this.applyQualifiedOverride(entities, field, group.getItems());
                else this.replace(entities, field, field.getKeys().getFirst(), this.itemTexts(group.getItems(), ITEM_VALUE));
            }
        }
    }

    private void applyFieldOverride(List<PatchEntity> entities, Field field, PreviewFieldValue value) {
        List<String> values;
        if (DspaceSubmissionForm.INPUT_DATE.equals(field.getInputType())) {
            values = nonBlankList(value.getDateValue() != null ? formatDate(value.getDateValue()) : value.getTextValue());
        } else if (value.getTextListValue() != null) {
            values = value.getTextListValue().stream().map(DspacePreviewMapper::normalize).filter(Objects::nonNull).distinct().toList();
        } else {
            values = nonBlankList(value.getTextValue());
        }
        values = values.stream().map(text -> optionValue(field, text)).filter(Objects::nonNull).distinct().toList();
        if (values.isEmpty() && field.isMandatory()) return; // a mandatory field keeps its mapped value
        this.replace(entities, field, field.getKeys().getFirst(), values);
    }

    private void applyQualifiedOverride(List<PatchEntity> entities, Field field, List<PreviewGroupItemModel> items) {
        Map<String, List<String>> valuesByKey = new LinkedHashMap<>();
        field.getKeys().forEach(key -> valuesByKey.put(key, new ArrayList<>()));
        String fallbackKey = field.getKeys().stream().filter(key -> key.endsWith(".other")).findFirst().orElse(field.getKeys().getFirst());
        if (items != null) {
            for (PreviewGroupItemModel item : items) {
                String value = normalize(this.itemText(item, ITEM_VALUE));
                if (value == null) continue;
                String key = this.itemText(item, ITEM_METADATA);
                valuesByKey.get(key != null && valuesByKey.containsKey(key) ? key : fallbackKey).add(value);
            }
        }
        valuesByKey.forEach((key, values) -> this.replace(entities, field, key, values));
    }

    /** Replaces the key's entity with one holding the values, in its place; no values leaves the key out. */
    private void replace(List<PatchEntity> entities, Field field, String key, List<String> values) {
        int index = -1;
        for (int i = 0; i < entities.size() && index < 0; i++) {
            if (key.equals(metadataKey(entities.get(i).getPath()))) index = i;
        }
        entities.removeIf(entity -> key.equals(metadataKey(entity.getPath())));
        if (values == null || values.isEmpty()) return;
        PatchEntity entity = entity(field.path(key), values, field.isRepeatable());
        if (index >= 0) entities.add(index, entity);
        else entities.add(entity);
    }

    // ---- patch entities ----

    private static PatchEntity entity(String path, List<String> values, boolean repeatable) {
        PatchEntity entity = new PatchEntity();
        entity.setOp(DSPACE_OP_ADD);
        entity.setPath(path);
        List<Map<String, Object>> entries = new ArrayList<>();
        for (String value : repeatable ? values : values.subList(0, 1)) {
            Map<String, Object> entry = new HashMap<>();
            entry.put("value", value);
            if (repeatable) {
                entry.put("display", value);
                entry.put("confidence", -1);
                entry.put("place", entries.size());
            }
            entries.add(entry);
        }
        entity.setValue(entries);
        return entity;
    }

    private static String metadataKey(String path) {
        return path == null ? null : path.substring(path.lastIndexOf('/') + 1);
    }

    private List<String> values(List<PatchEntity> entities, String key) {
        List<String> values = new ArrayList<>();
        for (PatchEntity entity : entities) {
            if (key.equals(metadataKey(entity.getPath())) && entity.getPath().startsWith(SECTIONS_PREFIX)) values.addAll(entryValues(entity));
        }
        return values;
    }

    private static List<String> entryValues(PatchEntity entity) {
        if (entity.getValue() == null) return List.of();
        return entity.getValue().stream()
                .filter(Objects::nonNull)
                .map(entry -> entry.get("value"))
                .filter(Objects::nonNull)
                .map(Object::toString)
                .filter(value -> !value.isBlank())
                .toList();
    }

    // ---- review values ----

    private List<String> itemTexts(List<PreviewGroupItemModel> items, String code) {
        List<String> values = new ArrayList<>();
        if (items == null) return values;
        for (PreviewGroupItemModel item : items) {
            String value = normalize(this.itemText(item, code));
            if (value != null) values.add(value);
        }
        return values;
    }

    private String itemText(PreviewGroupItemModel item, String code) {
        if (item == null || item.getValues() == null) return null;
        return item.getValues().stream()
                .filter(value -> value != null && code.equals(value.getCode()))
                .findFirst()
                .map(PreviewFieldValue::getTextValue)
                .orElse(null);
    }

    private static String first(List<String> values) {
        return values.isEmpty() ? null : values.getFirst();
    }

    private static List<String> nonBlankList(String value) {
        String normalized = normalize(value);
        return normalized != null ? List.of(normalized) : List.of();
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    /**
     * The review sends a picked day as the user's local midnight, so the nearest UTC midnight is that day for any
     * offset within twelve hours.
     */
    private static String formatDate(Instant date) {
        return DATE_FORMAT.format(date.plus(Duration.ofHours(12)));
    }

    /**
     * The stored value of the field's option for a value: the option with that value, or else the one whose value or
     * label matches it ignoring case; null when the vocabulary has none. Fields without options take any value, as do
     * select fields whose vocabulary DSpace did not return.
     */
    private static String optionValue(Field field, String value) {
        if (value == null || field == null || !field.hasOptions() || field.getOptions().isEmpty()) return value;
        for (PreviewFieldOption option : field.getOptions()) {
            if (value.equals(option.getValue())) return option.getValue();
        }
        for (PreviewFieldOption option : field.getOptions()) {
            if (value.equalsIgnoreCase(option.getValue()) || value.equalsIgnoreCase(option.getLabel())) return option.getValue();
        }
        return null;
    }

    // ---- descriptor ----

    private PreviewFieldGroup buildGroup(String code, String label) {
        PreviewFieldGroup group = new PreviewFieldGroup();
        group.setCode(code);
        group.setLabel(label);
        group.setFields(new ArrayList<>());
        return group;
    }

    private PreviewField buildTemplateField(String code, String label, PreviewFieldType type) {
        PreviewField field = new PreviewField();
        field.setCode(code);
        field.setLabel(label);
        field.setType(type);
        field.setSource(PreviewFieldSource.Mapped);
        return field;
    }

    private PreviewGroupItemModel item(PreviewFieldValue... values) {
        PreviewGroupItemModel item = new PreviewGroupItemModel();
        item.setValues(new ArrayList<>(List.of(values)));
        return item;
    }

    private PreviewFieldValue copy(PreviewFieldValue source) {
        PreviewFieldValue value = new PreviewFieldValue();
        value.setCode(source.getCode());
        value.setTextValue(source.getTextValue());
        value.setTextListValue(source.getTextListValue());
        value.setDateValue(source.getDateValue());
        return value;
    }

    private PreviewFieldValue textValue(String code, String text) {
        PreviewFieldValue value = new PreviewFieldValue();
        value.setCode(code);
        value.setTextValue(text);
        return value;
    }

    private PreviewFieldValue textListValue(String code, List<String> texts) {
        PreviewFieldValue value = new PreviewFieldValue();
        value.setCode(code);
        value.setTextListValue(texts != null && !texts.isEmpty() ? new ArrayList<>(texts) : null);
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
}
