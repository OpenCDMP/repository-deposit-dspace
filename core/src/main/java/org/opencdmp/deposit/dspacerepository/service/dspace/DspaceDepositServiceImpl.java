package org.opencdmp.deposit.dspacerepository.service.dspace;

import gr.cite.tools.logging.LoggerService;
import gr.cite.tools.logging.MapLogEntry;
import org.json.JSONObject;
import org.opencdmp.commonmodels.enums.PlanAccessType;
import org.opencdmp.commonmodels.models.FileEnvelopeModel;
import org.opencdmp.commonmodels.models.plan.PlanModel;
import org.opencdmp.commonmodels.models.plugin.PluginUserFieldModel;
import org.opencdmp.deposit.dspacerepository.model.*;
import org.opencdmp.deposit.dspacerepository.model.builder.DspaceBuilder;
import org.opencdmp.deposit.dspacerepository.service.storage.FileStorageService;
import org.opencdmp.depositbase.repository.DepositConfiguration;
import org.opencdmp.depositbase.repository.PlanDepositModel;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.*;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.io.*;
import java.util.*;


import reactor.netty.http.client.HttpClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class DspaceDepositServiceImpl implements DspaceDepositService {
    private static final LoggerService logger = new LoggerService(LoggerFactory.getLogger(DspaceDepositServiceImpl.class));

    private static final String CONFIGURATION_FIELD_EMAIL = "dspace-email";
    private static final String CONFIGURATION_FIELD_PASSWORD = "dspace-password";
    private static final String DSPACE_OP_ADD = "add";

    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final DspaceServiceProperties dspaceServiceProperties;
    private final DspaceBuilder dspaceBuilder;
    private final FileStorageService storageService;
    private final ResourceLoader resourceLoader;

    private byte[] logo;
    private String csrfToken;
    private String submitterId;
    
    @Autowired
    public DspaceDepositServiceImpl(DspaceServiceProperties dspaceServiceProperties, DspaceBuilder mapper, FileStorageService storageService, ResourceLoader resourceLoader){
        this.dspaceServiceProperties = dspaceServiceProperties;
        this.dspaceBuilder = mapper;
	    this.storageService = storageService;
        this.resourceLoader = resourceLoader;
        this.logo = null;
    }

    @Override
    public String deposit(PlanDepositModel planDepositModel) {

        DepositConfiguration depositConfiguration = this.getConfiguration();

        if(depositConfiguration != null && planDepositModel != null && planDepositModel.getPlanModel() != null) {

            this.setCsrfToken();
            String token = null;
            if (planDepositModel.getAuthInfo() != null) {
                if (planDepositModel.getAuthInfo().getAuthFields() != null && !planDepositModel.getAuthInfo().getAuthFields().isEmpty() && depositConfiguration.getUserConfigurationFields() != null) {
                    PluginUserFieldModel emailFieldModel = planDepositModel.getAuthInfo().getAuthFields().stream().filter(x -> x.getCode().equals(CONFIGURATION_FIELD_EMAIL)).findFirst().orElse(null);
                    PluginUserFieldModel passwordFieldModel = planDepositModel.getAuthInfo().getAuthFields().stream().filter(x -> x.getCode().equals(CONFIGURATION_FIELD_PASSWORD)).findFirst().orElse(null);
                    if (emailFieldModel != null && emailFieldModel.getTextValue() != null && !emailFieldModel.getTextValue().isBlank()
                        && passwordFieldModel != null && passwordFieldModel.getTextValue() != null && !passwordFieldModel.getTextValue().isBlank()) {
                        token = this.setBearerToken(emailFieldModel.getTextValue(), passwordFieldModel.getTextValue());
                    }
                }
            }

            if (token == null || token.isBlank()) {
                token = this.setBearerToken(this.dspaceServiceProperties.getUsername(), this.dspaceServiceProperties.getPassword());
            }

            String baseUrl = depositConfiguration.getRepositoryUrl();

            //get sumbitter
            this.setSubmitterId(token);

            // First step, post call to Zenodo, to create the entry.
            WebClient client = this.getWebClient();

            DepositConfiguration config = this.dspaceServiceProperties.getDepositConfiguration();
            if (config == null) return null;

            String previousDOI = planDepositModel.getPlanModel().getPreviousDOI();

            try {

                if (previousDOI == null) {
                    return deposit(token, baseUrl, client, planDepositModel.getPlanModel());
                } else {
                    return depositNewVersion(token, baseUrl, client, planDepositModel.getPlanModel());
                }

            } catch (HttpClientErrorException | HttpServerErrorException ex) {
                logger.error(ex.getMessage(), ex);
                Map<String, String> parsedException = objectMapper.readValue(ex.getResponseBodyAsString(), Map.class);
                try {
                    throw new IOException(parsedException.get("message"), ex);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }

        }

        return null;

    }

    private void setCsrfToken(){
        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = restTemplate.exchange(this.dspaceServiceProperties.getDepositConfiguration().getRepositoryUrl() + "security/csrf", HttpMethod.GET, null, Object.class).getHeaders();
        this.csrfToken = headers.get("DSPACE-XSRF-TOKEN").get(0);
    }

    private String setBearerToken(String email, String password){
        RestTemplate restTemplate = new RestTemplate();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.add("X-XSRF-TOKEN", this.csrfToken);
        headers.add("Cookie", "DSPACE-XSRF-COOKIE=" + this.csrfToken);

        MultiValueMap<String, String> map = new LinkedMultiValueMap<>();
        map.add("user", email);
        map.add("password", password);

        HttpEntity<MultiValueMap<String, String>> entity = new HttpEntity<>(map, headers);
        return restTemplate.exchange(this.dspaceServiceProperties.getDepositConfiguration().getRepositoryUrl() + "authn/login", HttpMethod.POST, entity, Object.class).getHeaders().get("Authorization").get(0);
    }

    private void setSubmitterId(String token){
        if(token != null){
            String splitToken = token.split(" ")[1];
            String[] chunks = splitToken.split("\\.");
            Base64.Decoder decoder = Base64.getUrlDecoder();
            String payload = new String(decoder.decode(chunks[1]));
            JSONObject object = new JSONObject(payload);
            this.submitterId = object.getString("eid");
        }
    }

    private HttpHeaders createHeaders(String zenodoToken, boolean isPatch, boolean isFile, boolean isUriList) {
        HttpHeaders headers = new HttpHeaders();
        headers.add("X-XSRF-TOKEN", this.csrfToken);
        headers.add("Cookie", "DSPACE-XSRF-COOKIE=" + this.csrfToken);
        headers.add("Authorization", zenodoToken);
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
        if (isPatch) headers.setContentType(MediaType.valueOf("application/json-patch+json"));
        else if (isFile) headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        else if (isUriList) headers.setContentType(MediaType.parseMediaType("text/uri-list"));
        else headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private String depositNewVersion(String token, String baseUrl, WebClient client, PlanModel planModel) {


        String itemId = this.getItemIdFromHandle(baseUrl, planModel.getPreviousDOI());

        String workSpaceItemId = this.createNewVersion(itemId, token, client);

        this.deleteFiles(workSpaceItemId, token);

        String workSpaceItemUrl = baseUrl + "submission/workspaceitems/" + workSpaceItemId;

        this.sendDataFields(token, workSpaceItemUrl, client, planModel,false);

        this.uploadFiles(planModel, workSpaceItemUrl, token);

        String workFlowId = this.createWorkflow(workSpaceItemUrl, token);

        String newItemId = this.getItemId(workFlowId, token);

        String claimedTaskId = this.createClaimedTask(workFlowId, token);

        this.submitTask(claimedTaskId, token);

        String accessUrl = baseUrl + "core/items/" + newItemId;
        this.sendDataFields(token, accessUrl, client, planModel, true);

        if(planModel.getAccessType().equals(PlanAccessType.Restricted)) this.sendPatchBooleanRequest(client, accessUrl, this.dspaceBuilder.buildBooleanValue(false, "replace","/discoverable"), token);

        return this.getHandle(newItemId, token);
    }

    private String getItemIdFromHandle(String recordUrl, String handle) {
        ResponseEntity<Map> response = this.getWebClient()
                .get()
                .uri(recordUrl + "pid/find?id=" + handle)
                .retrieve()
                .toEntity(Map.class)
                .block();

        if (response == null || response.getBody() == null) {
            return null;
        }

        Map<String, Object> responseBody = response.getBody();
        return responseBody.get("id").toString();

    }

    private String createNewVersion(String itemId, String token, WebClient client) {
        String versioningUrl = this.dspaceServiceProperties.getDepositConfiguration().getRepositoryUrl() + "versioning/versions";
        String itemUrl = this.dspaceServiceProperties.getDepositConfiguration().getRepositoryUrl() + "core/items/" + itemId;

        HttpHeaders headers = createHeaders(token, false, false, true); // ✅ Added "true" for text/uri-list

        Map<String, Object> response = client.post()
                .uri(versioningUrl)
                .headers(httpHeaders -> httpHeaders.addAll(headers))
                .bodyValue(itemUrl)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                .block();

        if (response == null || !response.containsKey("_links")) {
            throw new RuntimeException("Failed to create new version.");
        }

        Map<String, Object> links = (Map<String, Object>) response.get("_links");
        if (!links.containsKey("versionhistory")) {
            throw new RuntimeException("Missing 'versionhistory' link in response.");
        }
        String versionHistoryUrl = ((Map<String, Object>) links.get("versionhistory")).get("href").toString();

        response = client.get()
                .uri(versionHistoryUrl)
                .headers(httpHeaders -> httpHeaders.addAll(headers))
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                .block();

        if (response == null || !response.containsKey("_links")) {
            throw new RuntimeException("Failed to retrieve version history.");
        }

        links = (Map<String, Object>) response.get("_links");
        if (!links.containsKey("draftVersion")) {
            throw new RuntimeException("Missing 'draftVersion' link in response.");
        }
        String draftVersionUrl = ((Map<String, Object>) links.get("draftVersion")).get("href").toString();

        response = client.get()
                .uri(draftVersionUrl)
                .headers(httpHeaders -> httpHeaders.addAll(headers))
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                .block();

        if (response == null || !response.containsKey("id")) {
            throw new RuntimeException("Failed to retrieve draft version.");
        }

        return String.valueOf(response.get("id"));
    }



    private void deleteFiles(String workSpaceItemId, String token) {
        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = this.createHeaders(token, false, false, false);

        String workSpaceItemUrl = this.dspaceServiceProperties.getDepositConfiguration().getRepositoryUrl()
                + "submission/workspaceitems/" + workSpaceItemId;

        ResponseEntity<Map> responseEntity = restTemplate.exchange(
                workSpaceItemUrl, HttpMethod.GET, new HttpEntity<>(headers), Map.class
        );

        if (responseEntity.getBody() == null) {
            throw new RuntimeException("Workspace item not found: " + workSpaceItemId);
        }

        Map<String, Object> response = responseEntity.getBody();

        Map<String, Object> sections = (Map<String, Object>) response.get("sections");
        if (sections == null || !sections.containsKey("upload")) {
            System.out.println("No uploaded files found for workspace item: " + workSpaceItemId);
            return;
        }

        Map<String, Object> uploadSection = (Map<String, Object>) sections.get("upload");
        if (uploadSection == null || !uploadSection.containsKey("files")) {
            System.out.println("Upload section has no files.");
            return;
        }

        List<Map<String, Object>> files = (List<Map<String, Object>>) uploadSection.get("files");

        for (Map<String, Object> file : files) {
            String fileId = String.valueOf(file.get("uuid"));

            if (fileId == null || fileId.isEmpty() || fileId.equals("null")) {
                System.out.println("Skipping invalid file entry.");
                continue;
            }

            String bitStreamUrl = this.dspaceServiceProperties.getDepositConfiguration().getRepositoryUrl()
                    + "core/bitstreams/" + fileId;

            restTemplate.exchange(bitStreamUrl, HttpMethod.DELETE, new HttpEntity<>(headers), Void.class);
            System.out.println("Deleted file (bitstream ID): " + fileId);
        }
    }


    private String deposit(String token, String baseUrl, WebClient client, PlanModel planModel) {
        Map<String, Object> response;
        String url = baseUrl + "submission/workspaceitems?owningCollection=" + dspaceServiceProperties.getCollection();
        logger.debug(new MapLogEntry("Deposit")
                .And("url", url)
                .And("plan", planModel));

        response = client.post().uri(url).headers(httpHeaders -> {
            httpHeaders.putAll(this.createHeaders(token, false, false,false));
        })
        .exchangeToMono(mono ->
                        mono.statusCode().isError() ?
                                mono.createException().flatMap(Mono::error) :
                                mono.bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})).block();

        if (response == null) return null;
        String id = String.valueOf(response.get("id"));

        url = baseUrl + "submission/workspaceitems/" + id;

        this.sendDataFields(token, url, client, planModel, false);

        this.uploadFiles(planModel, url, token);

        String workFlowId = this.createWorkflow(url, token);

        String itemId = this.getItemId(workFlowId, token);

        String claimedTaskId = this.createClaimedTask(workFlowId, token);

        this.submitTask(claimedTaskId, token);

        String accessUrl = baseUrl + "core/items/" + itemId;
        this.sendDataFields(token, accessUrl, client, planModel, true);

        if(planModel.getAccessType().equals(PlanAccessType.Restricted)) this.sendPatchBooleanRequest(client, accessUrl, this.dspaceBuilder.buildBooleanValue(false, "replace","/discoverable"), token);

        return this.getHandle(itemId, token);
    }

    private void sendDataFields(String token, String url, WebClient client, PlanModel planModel, boolean isPublished){
        if(!isPublished){
            List<PatchEntity> entities = dspaceBuilder.build(planModel);
            this.sendPatchRequest(client, url, entities, token);
            this.sendPatchBooleanRequest(client, url, this.dspaceBuilder.buildBooleanValue(true, DSPACE_OP_ADD, "/sections/license/granted"), token);


        }else{
            List<PatchEntity> entities = dspaceBuilder.applySemantics(planModel,true);
            this.sendPatchRequest(client, url, entities, token);
        }


    }

    private void uploadFiles(PlanModel planModel, String url, String token) {
        if (planModel.getPdfFile() != null) this.uploadFile(planModel.getPdfFile(), url, token);
        if (planModel.getRdaJsonFile() != null) this.uploadFile(planModel.getRdaJsonFile(), url, token);
        if (planModel.getSupportingFilesZip() != null) this.uploadFile(planModel.getSupportingFilesZip(), url, token);
    }

    private void sendPatchRequest(WebClient client, String url, List<PatchEntity> entities, String token) {

        client.patch().uri(url)
                .headers(httpHeaders -> {
                    httpHeaders.putAll(this.createHeaders(token, true, false,false));
                })
                .bodyValue(entities)
                .retrieve().toEntity(Object.class).block();
    }

    private void sendPatchBooleanRequest(WebClient client, String url, List<PatchBooleanEntity> entities, String token) {

        client.patch().uri(url)
                .headers(httpHeaders -> {
                    httpHeaders.putAll(this.createHeaders(token, true, false,false));
                })
                .bodyValue(entities)
                .retrieve().toEntity(Object.class).block();
    }

    private void uploadFile(FileEnvelopeModel fileEnvelopeModel, String url, String token) {

        byte[] content = null;
        if (this.getConfiguration().isUseSharedStorage() && fileEnvelopeModel.getFileRef() != null && !fileEnvelopeModel.getFileRef().isBlank()) {
            content = this.storageService.readFile(fileEnvelopeModel.getFileRef());
        }
        if (content == null || content.length == 0){
            content = fileEnvelopeModel.getFile();
        }

        HttpHeaders headers = this.createHeaders(token, false, true,false);
        MultiValueMap<String, String> fileMap = new LinkedMultiValueMap<>();
        ContentDisposition contentDisposition = ContentDisposition
                .builder("form-data")
                .name("file")
                .filename(fileEnvelopeModel.getFilename())
                .build();
        fileMap.add(HttpHeaders.CONTENT_DISPOSITION, contentDisposition.toString());
        HttpEntity<byte[]> fileEntity = new HttpEntity<>(content, fileMap);
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", fileEntity);
        HttpEntity<MultiValueMap<String, Object>> requestEntity
                = new HttpEntity<>(body, headers);

        RestTemplate restTemplate = new RestTemplate();
        restTemplate.postForEntity(url, requestEntity, Object.class);
    }

    private String createWorkflow(String workSpaceItemUrl, String token){
        RestTemplate restTemplate = new RestTemplate();

        HttpHeaders headers = this.createHeaders(token, false, false,true);

        Object response = restTemplate.postForEntity(this.dspaceServiceProperties.getDepositConfiguration().getRepositoryUrl() + "workflow/workflowitems", new HttpEntity<>(workSpaceItemUrl, headers), Object.class).getBody();
        Map<String, Object> respMap = objectMapper.convertValue(response, Map.class);

        return String.valueOf(respMap.get("id"));
    }

    private String getItemId(String workflowId, String token){
        RestTemplate restTemplate = new RestTemplate();

        HttpHeaders headers = this.createHeaders(token, false, false,false);
        String url = this.dspaceServiceProperties.getDepositConfiguration().getRepositoryUrl() + "workflow/workflowitems/" + workflowId + "/item";
        Object response = restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), Object.class).getBody();
        Map<String, Object> respMap = objectMapper.convertValue(response, Map.class);

        return String.valueOf(respMap.get("id"));
    }

    private String createClaimedTask(String workFlowId, String token){
        RestTemplate restTemplate = new RestTemplate();

        HttpHeaders headers = this.createHeaders(token, false, false,true);

        String poolTaskId = this.getPoolTaskId(workFlowId, token);

        String pooltaskUrl = this.dspaceServiceProperties.getDepositConfiguration().getRepositoryUrl() + "workflow/pooltasks/" + poolTaskId;
        Object response = restTemplate.postForEntity(this.dspaceServiceProperties.getDepositConfiguration().getRepositoryUrl() + "workflow/claimedtasks", new HttpEntity<>(pooltaskUrl, headers), Object.class).getBody();
        Map<String, Object> respMap = objectMapper.convertValue(response, Map.class);

        return String.valueOf(respMap.get("id"));
    }

    private String getPoolTaskId(String workFlowId, String token){
        RestTemplate restTemplate = new RestTemplate();

        HttpHeaders headers = this.createHeaders(token, false, false,false);
        String url = this.dspaceServiceProperties.getDepositConfiguration().getRepositoryUrl() + "workflow/pooltasks/search/findByUser?uuid=" + this.submitterId;
        Object response = restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), Object.class).getBody();
        Map<String, Object> respMap = objectMapper.convertValue(response, Map.class);
        Object embedded = respMap.get("_embedded");
        respMap = objectMapper.convertValue(embedded, Map.class);
        List<Object> pooltasks = (List<Object>) respMap.get("pooltasks");
        for(Object pooltask: pooltasks){
            JsonNode task = objectMapper.valueToTree(pooltask);
            JsonNode workFlowItem = task.get("_embedded").get("workflowitem");
            int wfId = workFlowItem.get("id").asInt();
            if(wfId == Integer.parseInt(workFlowId)){
                return String.valueOf(task.get("id").asInt());
            }
        }
        return null;
    }

    private void submitTask(String claimedTaskId, String token){
        RestTemplate restTemplate = new RestTemplate();

        HttpHeaders headers = this.createHeaders(token, false, false,false);
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, Boolean> map = new LinkedMultiValueMap<>();
        map.add("submit_approve", true);

        HttpEntity<MultiValueMap<String, Boolean>> entity = new HttpEntity<>(map, headers);
        String url = this.dspaceServiceProperties.getDepositConfiguration().getRepositoryUrl() + "workflow/claimedtasks/" + claimedTaskId;
        restTemplate.exchange(url, HttpMethod.POST, entity, Object.class);
    }

    private String getHandle(String itemId, String token){
        RestTemplate restTemplate = new RestTemplate();

        HttpHeaders headers = this.createHeaders(token, false, false,false);
        String url = this.dspaceServiceProperties.getDepositConfiguration().getRepositoryUrl() + "core/items/" + itemId;
        Object response = restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), Object.class).getBody();
        Map<String, Object> respMap = objectMapper.convertValue(response, Map.class);

        return (String) respMap.get("handle");
    }


    @Override
    public DepositConfiguration getConfiguration() {
        return this.dspaceServiceProperties.getDepositConfiguration();
    }
    
    @Override
    public String authenticate(String code){

        return null;

    }

    @Override
    public String getLogo() {
        DepositConfiguration zenodoConfig = this.dspaceServiceProperties.getDepositConfiguration();
        if(zenodoConfig != null && zenodoConfig.isHasLogo() && this.dspaceServiceProperties.getLogo() != null && !this.dspaceServiceProperties.getLogo().isBlank()) {
            if (this.logo == null) {
                try {
                    Resource resource = resourceLoader.getResource(this.dspaceServiceProperties.getLogo());
                    if(!resource.isReadable()) return null;
                    try(InputStream inputStream = resource.getInputStream()) {
                        this.logo = inputStream.readAllBytes();
                    }
                } catch (IOException e) {
                    logger.error(e.getMessage(), e);
                    throw new RuntimeException(e);
                }
            }
            return (this.logo != null && this.logo.length != 0) ? Base64.getEncoder().encodeToString(this.logo) : null;
        }
        return null;
    }

    private WebClient getWebClient() {
        HttpClient httpClient = HttpClient.create()
                .followRedirect(true);

        return WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .filters(exchangeFilterFunctions -> {
                    exchangeFilterFunctions.add(logRequest());
                    exchangeFilterFunctions.add(logResponse());
                })
                .codecs(codecs -> codecs
                        .defaultCodecs()
                        .maxInMemorySize(this.dspaceServiceProperties.getMaxInMemorySizeInBytes())
                )
                .build();
    }

    private static ExchangeFilterFunction logRequest() {
        return ExchangeFilterFunction.ofRequestProcessor(clientRequest -> {
            logger.debug(new MapLogEntry("Request").And("method", clientRequest.method().toString()).And("url", clientRequest.url().toString()));
            return Mono.just(clientRequest);
        });
    }

    private static ExchangeFilterFunction logResponse() {
        return ExchangeFilterFunction.ofResponseProcessor(response -> {
            if (response.statusCode().isError()) {
                return response.mutate().build().bodyToMono(String.class)
                    .flatMap(body -> {
                        logger.error(new MapLogEntry("Response").And("method", response.request().getMethod().toString()).And("url", response.request().getURI()).And("status", response.statusCode().toString()).And("body", body));
                        return Mono.just(response);
                    });
            }
            return Mono.just(response);
            
        });
    }
}
