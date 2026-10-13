package org.opencdmp.deposit.dspacerepository.configuration.semantics;

import org.opencdmp.commonmodels.enums.PluginEntityType;
import org.opencdmp.commonmodels.enums.SemanticKeyLevel;
import org.opencdmp.commonmodels.enums.SemanticValueFormat;
import org.opencdmp.commonmodels.models.semantics.SemanticKeyUsage;
import org.opencdmp.commonmodels.models.semantics.SemanticVocabulary;
import org.opencdmp.commonmodels.models.semantics.SemanticsManifest;
import org.opencdmp.deposit.dspacerepository.service.dspace.DspaceServiceProperties;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * The semantics manifest the DSpace deposit publishes with its configuration: one usage per entry in
 * {@code semantics.available}, each written as a Dublin Core metadata value on the deposited item at the entry's path.
 * <p>
 * Every value is "can use": the deposit always writes the title, the issued date, the abstract, the type (the
 * default item type) and the authors the organisation credits from the plan's members, so nothing a template or
 * blueprint leaves out breaks it.
 * Keys are read from blueprint and template inputs alike, and a reference input gives its reference's code. The format
 * and behaviour of each value follow the same path rules {@code DspaceBuilder.applySemantics} applies.
 */
public final class DspaceSemanticsManifest {

    public static final String PREFIX = "dspace";

    private static final String PATH_TYPE = "type";
    private static final String PATH_AUTHOR = "author";
    private static final String PATH_LANGUAGE = "language.iso";
    private static final String PATH_IDENTIFIER_URI = "identifier.uri";
    private static final String PATH_IDENTIFIER = "identifier";
    private static final String PATH_TITLE_ALTERNATIVE = "title.alternative";
    private static final String PATH_RIGHTS = "dc.rights";
    private static final String PATH_PUBLISHER = "dc.publisher";
    private static final String PATH_PROVENANCE = "provenance";
    private static final String PATH_DATE_ACCESSIONED = "date.accessioned";
    private static final String PATH_DATE_AVAILABLE = "date.available";

    private static final Map<String, String> KNOWN_LABELS = Map.ofEntries(
            Map.entry("dspace.identifier.citation", "Identifier › Citation"),
            Map.entry("dspace.identifier.govdoc", "Identifier › Government document"),
            Map.entry("dspace.identifier.isbn", "Identifier › ISBN"),
            Map.entry("dspace.identifier.ismn", "Identifier › ISMN"),
            Map.entry("dspace.identifier.other", "Identifier › Other"),
            Map.entry("dspace.identifier.uri", "Identifier › URI"),
            Map.entry("dspace.description", "Description"),
            Map.entry("dspace.description.provenance", "Description › Provenance"),
            Map.entry("dspace.description.sponsorship", "Description › Sponsorship"),
            Map.entry("dspace.title.alternative", "Title › Alternative"),
            Map.entry("dspace.publisher", "Publisher"),
            Map.entry("dspace.rights", "Rights"),
            Map.entry("dspace.rights.uri", "Rights › URI"),
            Map.entry("dspace.type", "Type"),
            Map.entry("dspace.language", "Language"),
            Map.entry("dspace.contributor.author", "Contributor › Author"),
            Map.entry("dspace.date.accessioned", "Date › Accessioned"),
            Map.entry("dspace.date.available", "Date › Available")
    );

    private DspaceSemanticsManifest() {
    }

    public static SemanticsManifest build(SemanticsProperties semantics, DspaceServiceProperties dspace) {
        List<SemanticKeyUsage> usages = new ArrayList<>();
        List<SemanticsProperties.PathName> available = semantics == null || semantics.getAvailable() == null ? List.of() : semantics.getAvailable();
        List<String> seen = new ArrayList<>();
        for (SemanticsProperties.PathName pathName : available) {
            if (pathName == null || pathName.getName() == null || seen.contains(pathName.getName())) continue;
            seen.add(pathName.getName());
            usages.add(usage(pathName.getName(), pathName.getPath(), dspace));
        }
        return new SemanticsManifest(List.of(new SemanticVocabulary(PREFIX, "DSpace", false)), usages);
    }

    private static SemanticKeyUsage usage(String key, String path, DspaceServiceProperties dspace) {
        String safePath = path == null ? "" : path;
        String element = safePath.substring(safePath.lastIndexOf('/') + 1);
        SemanticKeyUsage usage = new SemanticKeyUsage(
                List.of(key),
                false,
                SemanticKeyLevel.CanUse,
                List.of(PluginEntityType.Plan, PluginEntityType.Description),
                KNOWN_LABELS.getOrDefault(key, label(key)),
                null,
                element.isBlank() ? null : element);

        if (safePath.contains(PATH_PROVENANCE) || safePath.contains(PATH_DATE_ACCESSIONED) || safePath.contains(PATH_DATE_AVAILABLE)) {
            usage.setFormat(safePath.contains(PATH_PROVENANCE) ? SemanticValueFormat.Text : SemanticValueFormat.Date);
            usage.setMultiple(true);
            usage.setPurpose("Written to " + element + " only once the item is published, together with the accession dates and the provenance.");
        } else if (safePath.contains(PATH_TYPE)) {
            List<String> accepted = dspace == null || dspace.getAcceptedTypeCodes() == null ? List.of() : dspace.getAcceptedTypeCodes();
            usage.setFormat(SemanticValueFormat.Choice);
            usage.setValues(accepted);
            usage.setMultiple(true);
            usage.setPurpose("Written after the default item type, which the item always has as a type; a value outside the accepted types is written as \"Other\".");
            usage.setFallback(dspace == null || dspace.getDefaultType() == null ? "The organisation's default item type" : "The organisation's default item type, else " + dspace.getDefaultType());
        } else if (safePath.contains(PATH_AUTHOR)) {
            usage.setFormat(SemanticValueFormat.Text);
            usage.setMultiple(true);
            usage.setPurpose("Added after the plan members credited as authors, as the organisation's \"Authors from plan members\" setting chooses (every member by default).");
            usage.setFallback("The plan members chosen in \"Authors from plan members\"");
        } else if (safePath.contains(PATH_LANGUAGE)) {
            usage.setFormat(SemanticValueFormat.LanguageTwoLetter);
            usage.setMultiple(false);
            usage.setPurpose("Written as the language of the item.");
            usage.setFallback("The plan's language");
        } else if (safePath.contains(PATH_RIGHTS) || safePath.contains(PATH_PUBLISHER)) {
            usage.setFormat(safePath.endsWith(PATH_RIGHTS + ".uri") ? SemanticValueFormat.Uri : SemanticValueFormat.Text);
            usage.setMultiple(false);
            usage.setPurpose("Written to " + element + " on the item.");
            usage.setFallback("The organisation's default, if set");
        } else if (safePath.contains(PATH_IDENTIFIER_URI)) {
            usage.setFormat(SemanticValueFormat.Uri);
            usage.setMultiple(true);
            usage.setPurpose("Added to the item's URIs, after the plan's public page, which a public plan always gets as its first URI.");
        } else {
            usage.setFormat(SemanticValueFormat.Text);
            // Each value of any other path is its own DSpace "add", which replaces the one before it.
            usage.setMultiple(safePath.contains(PATH_IDENTIFIER) || safePath.contains(PATH_TITLE_ALTERNATIVE));
            usage.setPurpose(element.isBlank() ? "Written as a Dublin Core value on the item." : "Written to " + element + " on the item.");
        }
        return usage;
    }

    private static String label(String key) {
        String name = key.startsWith(PREFIX + ".") ? key.substring(PREFIX.length() + 1) : key;
        return Arrays.stream(name.split("\\."))
                .filter(Objects::nonNull)
                .filter(x -> !x.isBlank())
                .map(x -> Character.toUpperCase(x.charAt(0)) + x.substring(1))
                .collect(Collectors.joining(" › "));
    }
}
