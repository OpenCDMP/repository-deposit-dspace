package org.opencdmp.deposit.dspacerepository.model.form;

import org.opencdmp.commonmodels.models.deposit.PreviewFieldOption;

import java.util.*;

/**
 * The metadata fields of a collection's DSpace submission form: its "submission-form" sections and their fields,
 * as {@code config/submissionforms/{section}} describes them, or the fallback built from the plugin's configuration.
 */
public class DspaceSubmissionForm {

    public static final String INPUT_ONEBOX = "onebox";
    public static final String INPUT_TEXTAREA = "textarea";
    public static final String INPUT_DATE = "date";
    public static final String INPUT_DROPDOWN = "dropdown";
    public static final String INPUT_LIST = "list";
    public static final String INPUT_TAG = "tag";

    private final List<Section> sections;
    private final boolean fallback;
    private final Map<String, Field> fieldsByCode = new LinkedHashMap<>();
    private final Map<String, Field> fieldsByKey = new LinkedHashMap<>();

    public DspaceSubmissionForm(List<Section> sections, boolean fallback) {
        this.sections = sections;
        this.fallback = fallback;
        for (Section section : sections) {
            for (Field field : section.getFields()) {
                String code = field.baseCode();
                for (int suffix = 2; this.fieldsByCode.containsKey(code); suffix++) code = field.baseCode() + "_" + suffix;
                field.code = code;
                field.section = section.getId();
                this.fieldsByCode.put(code, field);
                for (String key : field.getKeys()) this.fieldsByKey.putIfAbsent(key, field);
            }
        }
    }

    public List<Section> getSections() {
        return sections;
    }

    /** True when the form is the configured fallback, not the one DSpace serves for the collection. */
    public boolean isFallback() {
        return fallback;
    }

    public Field fieldByCode(String code) {
        return code != null ? this.fieldsByCode.get(code) : null;
    }

    public Field fieldByKey(String key) {
        return key != null ? this.fieldsByKey.get(key) : null;
    }

    /** A form field or group code for a metadata key; the review reads codes as form paths, so they have no dots. */
    public static String code(String key) {
        return key.replace('.', '_');
    }

    public static class Section {
        private final String id;
        private final String label;
        private final List<Field> fields;

        public Section(String id, String label, List<Field> fields) {
            this.id = id;
            this.label = label;
            this.fields = fields;
        }

        public String getId() {
            return id;
        }

        public String getLabel() {
            return label;
        }

        public List<Field> getFields() {
            return fields;
        }
    }

    public static class Field {
        private final String label;
        private final String hint;
        private final String inputType;
        private final boolean repeatable;
        private final boolean mandatory;
        private final List<Metadata> metadata;
        private final List<PreviewFieldOption> options;
        private String code;
        private String section;

        public Field(String label, String hint, String inputType, boolean repeatable, boolean mandatory, List<Metadata> metadata, List<PreviewFieldOption> options) {
            this.label = label;
            this.hint = hint;
            this.inputType = inputType;
            this.repeatable = repeatable;
            this.mandatory = mandatory;
            this.metadata = metadata;
            this.options = options != null ? options : List.of();
        }

        public String getLabel() {
            return label;
        }

        public String getHint() {
            return hint;
        }

        public String getInputType() {
            return inputType;
        }

        public boolean isRepeatable() {
            return repeatable;
        }

        public boolean isMandatory() {
            return mandatory;
        }

        public List<Metadata> getMetadata() {
            return metadata;
        }

        public List<PreviewFieldOption> getOptions() {
            return options;
        }

        public String getCode() {
            return code;
        }

        public String getSection() {
            return section;
        }

        public List<String> getKeys() {
            return this.metadata.stream().map(Metadata::getKey).toList();
        }

        /** The submission path of one of the field's keys. */
        public String path(String key) {
            return "/sections/" + this.section + "/" + key;
        }

        /** One field for several keys, like the default form's "Identifiers": the user picks the key of each value. */
        public boolean isQualified() {
            return this.metadata.size() > 1;
        }

        public boolean hasOptions() {
            return INPUT_DROPDOWN.equals(this.inputType) || INPUT_LIST.equals(this.inputType);
        }

        /** Shown as one row per value in a repeatable group, not as a field of its section. */
        public boolean isGroup() {
            return this.isQualified() || (this.repeatable && !this.hasOptions() && !INPUT_TAG.equals(this.inputType));
        }

        private String baseCode() {
            if (!this.isQualified()) return code(this.metadata.getFirst().getKey());
            String prefix = this.metadata.getFirst().getKey();
            for (Metadata item : this.metadata) {
                while (!item.getKey().startsWith(prefix)) prefix = prefix.substring(0, prefix.length() - 1);
            }
            prefix = prefix.endsWith(".") ? prefix.substring(0, prefix.length() - 1) : prefix;
            return code(prefix.isEmpty() ? this.metadata.getFirst().getKey() : prefix);
        }
    }

    public static class Metadata {
        private final String key;
        private final String label;

        public Metadata(String key, String label) {
            this.key = key;
            this.label = label;
        }

        public String getKey() {
            return key;
        }

        public String getLabel() {
            return label;
        }
    }
}
