package org.opencdmp.deposit.dspacerepository.service.dspace;

import gr.cite.tools.logging.LoggerService;
import gr.cite.tools.logging.MapLogEntry;
import org.opencdmp.commonmodels.models.deposit.PreviewFieldOption;
import org.opencdmp.deposit.dspacerepository.configuration.semantics.SemanticsProperties;
import org.opencdmp.deposit.dspacerepository.model.builder.DspaceBuilder;
import org.opencdmp.deposit.dspacerepository.model.form.DspaceSubmissionForm;
import org.opencdmp.deposit.dspacerepository.model.form.DspaceSubmissionForm.Field;
import org.opencdmp.deposit.dspacerepository.model.form.DspaceSubmissionForm.Metadata;
import org.opencdmp.deposit.dspacerepository.model.form.DspaceSubmissionForm.Section;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.*;
import java.util.function.Supplier;

/**
 * Reads a collection's submission form from DSpace, so the deposit writes each value to the section the collection's
 * form has it in and the review shows that form's fields.
 * <p>
 * When the form cannot be read, the form described by {@code semantics.available} and the builder's fixed paths is
 * used instead.
 */
@Component
public class DspaceSubmissionFormService {
    private static final LoggerService logger = new LoggerService(LoggerFactory.getLogger(DspaceSubmissionFormService.class));

    private static final String SECTION_TYPE_FORM = "submission-form";
    private static final String SECTIONS_PREFIX = "/sections/";
    private static final String KEY_IDENTIFIER_PREFIX = "dc.identifier.";
    private static final String KEY_CITATION = "dc.identifier.citation";
    private static final String KEY_DESCRIPTION = "dc.description";

    private static final Map<String, String> SECTION_HEADERS = Map.of(
            "submit.progressbar.describe.stepone", "Describe",
            "submit.progressbar.describe.steptwo", "Describe (continued)");

    private static final Map<String, String> FALLBACK_LABELS = Map.ofEntries(
            Map.entry("dc.title", "Title"),
            Map.entry("dc.title.alternative", "Other titles"),
            Map.entry("dc.date.issued", "Date of issue"),
            Map.entry("dc.contributor.author", "Authors"),
            Map.entry("dc.type", "Type"),
            Map.entry("dc.language.iso", "Language"),
            Map.entry("dc.publisher", "Publisher"),
            Map.entry("dc.identifier.citation", "Citation"),
            Map.entry("dc.description", "Description"),
            Map.entry("dc.description.abstract", "Abstract"),
            Map.entry("dc.description.sponsorship", "Sponsors"),
            Map.entry("dc.rights", "Rights"),
            Map.entry("dc.rights.uri", "Rights URI"));

    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final DspaceServiceProperties dspaceServiceProperties;
    private final SemanticsProperties semanticsProperties;
    private final DspaceSubmissionFormCacheService formCacheService;

    public DspaceSubmissionFormService(DspaceServiceProperties dspaceServiceProperties, SemanticsProperties semanticsProperties, DspaceSubmissionFormCacheService formCacheService) {
        this.dspaceServiceProperties = dspaceServiceProperties;
        this.semanticsProperties = semanticsProperties;
        this.formCacheService = formCacheService;
    }

    /**
     * The collection's submission form, from the cache or read from DSpace, or the fallback form when DSpace does not
     * give it. The token is asked for only when the form is read from DSpace.
     */
    public DspaceSubmissionForm getForm(String collection, Supplier<String> token) {
        if (collection == null || collection.isBlank()) return this.fallbackForm();
        String repositoryUrl = this.dspaceServiceProperties.getDepositConfiguration().getRepositoryUrl();
        String cacheKey = this.formCacheService.buildKey(repositoryUrl, collection);
        DspaceSubmissionFormCacheService.DspaceSubmissionFormCacheValue cached = this.formCacheService.lookup(cacheKey);
        if (cached != null && cached.getForm() != null) return cached.getForm();
        try {
            DspaceSubmissionForm form = this.fetchForm(collection, token.get());
            this.formCacheService.put(new DspaceSubmissionFormCacheService.DspaceSubmissionFormCacheValue(repositoryUrl, collection, form));
            return form;
        } catch (Exception ex) {
            logger.warn(new MapLogEntry("Could not read the DSpace submission form; using the fallback form").And("collection", collection).And("error", ex.getMessage()));
            return this.fallbackForm();
        }
    }

    // ---- live form ----

    private DspaceSubmissionForm fetchForm(String collection, String token) {
        String baseUrl = this.dspaceServiceProperties.getDepositConfiguration().getRepositoryUrl();
        RestTemplate restTemplate = new RestTemplate();
        HttpEntity<Void> request = new HttpEntity<>(this.headers(token));

        // the definition's own "sections" link answers 204 on some versions, so the sections are embedded
        JsonNode definition = this.get(restTemplate, baseUrl + "config/submissiondefinitions/search/findByCollection?uuid=" + collection + "&embed=sections", request);
        Map<String, List<PreviewFieldOption>> vocabularies = new HashMap<>();
        Map<String, Optional<Set<String>>> registry = new HashMap<>();
        List<Section> sections = new ArrayList<>();
        for (JsonNode section : definition.path("_embedded").path("sections").path("_embedded").path("sections")) {
            if (!SECTION_TYPE_FORM.equals(section.path("sectionType").asString(null))) continue;
            String id = section.path("id").asString(null);
            if (id == null) continue;
            JsonNode form = this.get(restTemplate, baseUrl + "config/submissionforms/" + id, request);
            List<Field> fields = new ArrayList<>();
            for (JsonNode row : form.path("rows")) {
                for (JsonNode field : row.path("fields")) {
                    Field parsed = this.parseField(field, restTemplate, request, baseUrl, vocabularies, registry);
                    if (parsed != null) fields.add(parsed);
                }
            }
            if (!fields.isEmpty()) sections.add(new Section(id, this.sectionLabel(id, section.path("header").asString(null)), fields));
        }
        if (sections.isEmpty()) throw new IllegalStateException("The submission definition of the collection has no form sections");
        return new DspaceSubmissionForm(sections, false);
    }

    private Field parseField(JsonNode field, RestTemplate restTemplate, HttpEntity<Void> request, String baseUrl, Map<String, List<PreviewFieldOption>> vocabularies, Map<String, Optional<Set<String>>> registry) {
        List<Metadata> metadata = new ArrayList<>();
        String vocabulary = null;
        for (JsonNode selectable : field.path("selectableMetadata")) {
            String key = selectable.path("metadata").asString(null);
            if (key == null || key.isBlank()) continue;
            if (!this.isRegistered(key, restTemplate, request, baseUrl, registry)) {
                // a form can name a field the registry does not have (or has in another case); DSpace fails the whole patch on it
                logger.warn(new MapLogEntry("Submission form field left out: the metadata registry has no such field").And("metadata", key));
                continue;
            }
            metadata.add(new Metadata(key, selectable.path("label").asString(null)));
            if (vocabulary == null) vocabulary = selectable.path("controlledVocabulary").asString(null);
        }
        if (metadata.isEmpty()) return null; // relationship-only fields have no metadata of their own

        String inputType = field.path("input").path("type").asString(DspaceSubmissionForm.INPUT_ONEBOX);
        List<PreviewFieldOption> options = List.of();
        if ((DspaceSubmissionForm.INPUT_DROPDOWN.equals(inputType) || DspaceSubmissionForm.INPUT_LIST.equals(inputType)) && vocabulary != null) {
            String name = vocabulary;
            options = vocabularies.computeIfAbsent(name, k -> this.vocabularyOptions(restTemplate, request, baseUrl, k));
        }
        String hint = field.path("hints").asString(null);
        return new Field(field.path("label").asString(metadata.getFirst().getKey()), hint != null && !hint.isBlank() ? hint : null, inputType,
                field.path("repeatable").asBoolean(false), field.path("mandatory").asBoolean(false), metadata, options);
    }

    private List<PreviewFieldOption> vocabularyOptions(RestTemplate restTemplate, HttpEntity<Void> request, String baseUrl, String vocabulary) {
        JsonNode entries = this.get(restTemplate, baseUrl + "submission/vocabularies/" + vocabulary + "/entries?size=1000", request);
        List<PreviewFieldOption> options = new ArrayList<>();
        for (JsonNode entry : entries.path("_embedded").path("entries")) {
            String value = entry.path("value").asString(null);
            if (value == null || value.isBlank()) continue; // the "N/A" entry has no value
            options.add(new PreviewFieldOption(entry.path("display").asString(value), value));
        }
        return options;
    }

    /** Whether the metadata registry has the key, exactly; a schema whose fields cannot be listed is not checked. */
    private boolean isRegistered(String key, RestTemplate restTemplate, HttpEntity<Void> request, String baseUrl, Map<String, Optional<Set<String>>> registry) {
        int dot = key.indexOf('.');
        if (dot <= 0) return true;
        Optional<Set<String>> fields = registry.computeIfAbsent(key.substring(0, dot), schema -> this.registryFields(restTemplate, request, baseUrl, schema));
        return fields.map(keys -> keys.contains(key)).orElse(true);
    }

    private Optional<Set<String>> registryFields(RestTemplate restTemplate, HttpEntity<Void> request, String baseUrl, String schema) {
        try {
            Set<String> keys = new HashSet<>();
            int totalPages = 1;
            for (int page = 0; page < totalPages; page++) {
                JsonNode result = this.get(restTemplate, baseUrl + "core/metadatafields/search/bySchema?schema=" + schema + "&size=1000&page=" + page, request);
                for (JsonNode field : result.path("_embedded").path("metadatafields")) {
                    String element = field.path("element").asString(null);
                    String qualifier = field.path("qualifier").asString(null);
                    if (element != null) keys.add(schema + "." + element + (qualifier != null && !qualifier.isBlank() ? "." + qualifier : ""));
                }
                totalPages = result.path("page").path("totalPages").asInt(1);
            }
            return Optional.of(keys);
        } catch (Exception ex) {
            logger.warn(new MapLogEntry("Could not list the metadata registry; the form's fields are not checked against it").And("schema", schema).And("error", ex.getMessage()));
            return Optional.empty();
        }
    }

    private JsonNode get(RestTemplate restTemplate, String url, HttpEntity<Void> request) {
        String body = restTemplate.exchange(url, HttpMethod.GET, request, String.class).getBody();
        if (body == null || body.isBlank()) throw new IllegalStateException("Empty response from " + url);
        return objectMapper.readTree(body);
    }

    private HttpHeaders headers(String token) {
        HttpHeaders headers = new HttpHeaders();
        if (token != null) headers.add(HttpHeaders.AUTHORIZATION, token);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        return headers;
    }

    private String sectionLabel(String id, String header) {
        if (header != null && SECTION_HEADERS.containsKey(header)) return SECTION_HEADERS.get(header);
        String words = id.replaceAll("([a-z])([A-Z])", "$1 $2").toLowerCase(Locale.ROOT);
        return Character.toUpperCase(words.charAt(0)) + words.substring(1);
    }

    // ---- fallback form ----

    /** The form the builder's paths describe: its fixed paths, then the semantics paths, each in its own section. */
    public DspaceSubmissionForm fallbackForm() {
        Map<String, Map<String, String>> keysBySection = new LinkedHashMap<>();
        List<String> paths = new ArrayList<>(DspaceBuilder.FIXED_PATHS);
        if (this.semanticsProperties.getAvailable() != null) {
            this.semanticsProperties.getAvailable().stream().map(SemanticsProperties.PathName::getPath).filter(Objects::nonNull).forEach(paths::add);
        }
        Set<String> seen = new HashSet<>();
        for (String path : paths) {
            if (!path.startsWith(SECTIONS_PREFIX)) continue; // /metadata paths are written after publishing, not in the form
            String section = path.substring(SECTIONS_PREFIX.length(), path.lastIndexOf('/'));
            String key = path.substring(path.lastIndexOf('/') + 1);
            if (section.isBlank() || key.isBlank() || !seen.add(key)) continue;
            keysBySection.computeIfAbsent(section, k -> new LinkedHashMap<>()).put(key, path);
        }

        List<Section> sections = new ArrayList<>();
        for (Map.Entry<String, Map<String, String>> entry : keysBySection.entrySet()) {
            List<Field> fields = new ArrayList<>();
            List<Metadata> identifiers = new ArrayList<>();
            for (String key : entry.getValue().keySet()) {
                if (key.startsWith(KEY_IDENTIFIER_PREFIX) && !KEY_CITATION.equals(key)) {
                    identifiers.add(new Metadata(key, key.substring(KEY_IDENTIFIER_PREFIX.length()).toUpperCase(Locale.ROOT)));
                    continue;
                }
                fields.add(this.fallbackField(key));
            }
            if (!identifiers.isEmpty()) fields.add(new Field("Identifiers", null, DspaceSubmissionForm.INPUT_ONEBOX, true, false, identifiers, null));
            sections.add(new Section(entry.getKey(), this.sectionLabel(entry.getKey(), null), fields));
        }
        return new DspaceSubmissionForm(sections, true);
    }

    private Field fallbackField(String key) {
        String label = FALLBACK_LABELS.getOrDefault(key, key);
        List<Metadata> metadata = List.of(new Metadata(key, null));
        return switch (key) {
            case "dc.title" -> new Field(label, null, DspaceSubmissionForm.INPUT_ONEBOX, false, true, metadata, null);
            case "dc.date.issued" -> new Field(label, null, DspaceSubmissionForm.INPUT_DATE, false, true, metadata, null);
            case "dc.type" -> new Field(label, null, DspaceSubmissionForm.INPUT_DROPDOWN, true, true, metadata, this.typeOptions());
            case "dc.contributor.author", "dc.title.alternative" -> new Field(label, null, DspaceSubmissionForm.INPUT_ONEBOX, true, false, metadata, null);
            default -> new Field(label, null, key.startsWith(KEY_DESCRIPTION) ? DspaceSubmissionForm.INPUT_TEXTAREA : DspaceSubmissionForm.INPUT_ONEBOX, false, false, metadata, null);
        };
    }

    private List<PreviewFieldOption> typeOptions() {
        List<String> types = this.dspaceServiceProperties.getAcceptedTypeCodes() != null ? this.dspaceServiceProperties.getAcceptedTypeCodes() : List.of();
        return types.stream().filter(type -> type != null && !type.isBlank()).map(type -> new PreviewFieldOption(type, type)).toList();
    }
}
