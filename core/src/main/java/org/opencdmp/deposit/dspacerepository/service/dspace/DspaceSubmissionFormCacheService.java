package org.opencdmp.deposit.dspacerepository.service.dspace;

import gr.cite.tools.cache.CacheService;
import org.opencdmp.deposit.dspacerepository.model.form.DspaceSubmissionForm;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;

/** The submission forms read from DSpace, by repository and collection. Fallback forms are not cached. */
@Service
public class DspaceSubmissionFormCacheService extends CacheService<DspaceSubmissionFormCacheService.DspaceSubmissionFormCacheValue> {

    public static class DspaceSubmissionFormCacheValue {

        public DspaceSubmissionFormCacheValue() {
        }

        public DspaceSubmissionFormCacheValue(String repositoryUrl, String collection, DspaceSubmissionForm form) {
            this.repositoryUrl = repositoryUrl;
            this.collection = collection;
            this.form = form;
        }

        private String repositoryUrl;
        private String collection;
        private DspaceSubmissionForm form;

        public String getRepositoryUrl() {
            return repositoryUrl;
        }

        public void setRepositoryUrl(String repositoryUrl) {
            this.repositoryUrl = repositoryUrl;
        }

        public String getCollection() {
            return collection;
        }

        public void setCollection(String collection) {
            this.collection = collection;
        }

        public DspaceSubmissionForm getForm() {
            return form;
        }

        public void setForm(DspaceSubmissionForm form) {
            this.form = form;
        }
    }

    @Autowired
    public DspaceSubmissionFormCacheService(DspaceSubmissionFormCacheOptions options) {
        super(options);
    }

    @Override
    protected Class<DspaceSubmissionFormCacheValue> valueClass() {
        return DspaceSubmissionFormCacheValue.class;
    }

    @Override
    public String keyOf(DspaceSubmissionFormCacheValue value) {
        return this.buildKey(value.getRepositoryUrl(), value.getCollection());
    }

    public String buildKey(String repositoryUrl, String collection) {
        HashMap<String, String> keyParts = new HashMap<>();
        keyParts.put("$repositoryUrl$", repositoryUrl);
        keyParts.put("$collection$", collection);
        return this.generateKey(keyParts);
    }
}
