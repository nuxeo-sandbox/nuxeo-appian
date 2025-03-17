package org.nuxeo.platform.appian.operations;

import java.io.Serializable;
import java.util.*;

import jakarta.ws.rs.client.*;
import jakarta.ws.rs.core.MediaType;

import jakarta.ws.rs.core.Response;
import org.apache.commons.lang3.StringUtils;
import org.glassfish.jersey.client.ClientProperties;
import org.glassfish.jersey.client.authentication.HttpAuthenticationFeature;
import org.nuxeo.ecm.automation.AutomationService;
import org.nuxeo.ecm.automation.OperationContext;
import org.nuxeo.ecm.automation.OperationException;
import org.nuxeo.ecm.automation.core.Constants;
import org.nuxeo.ecm.automation.core.annotations.Context;
import org.nuxeo.ecm.automation.core.annotations.Operation;
import org.nuxeo.ecm.automation.core.annotations.OperationMethod;
import org.nuxeo.ecm.automation.core.annotations.Param;
import org.nuxeo.ecm.automation.core.collectors.DocumentModelCollector;
import org.nuxeo.ecm.automation.core.util.StringList;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.IdRef;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.core.api.model.Property;
import org.nuxeo.platform.appian.model.AppianResponse;
import org.nuxeo.runtime.api.Framework;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.glassfish.jersey.client.ClientConfig;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 *
 */
@Operation(id = StartAppianProcess.ID, category = Constants.CAT_DOCUMENT, label = "Start Appian Process", description = "Start a named Appian process with the input document.")
public class StartAppianProcess {

    protected static final Logger LOG = LogManager.getLogger(StartAppianProcess.class);

    public static final String ID = "Document.StartAppianProcess";

    private Client client;

    @Context
    protected CoreSession session;

    @Context
    protected AutomationService automation;

    @Context
    protected OperationContext ctx;

    @Param(name = "process", required = true)
    protected String process;
    
    @Param(name = "reviewers", required = false)
    protected StringList reviewers;

    protected Client getClient() {
        if (client == null) {
            String user = Framework.getProperty("appian.username");
            String pass = Framework.getProperty("appian.password");

            HttpAuthenticationFeature feature = HttpAuthenticationFeature.basicBuilder()
                    .nonPreemptive()
                    .credentials(user, pass)
                    .build();

            ClientConfig clientConfig = new ClientConfig();
            clientConfig.property(ClientProperties.FOLLOW_REDIRECTS, true);
            clientConfig.register(feature) ;

            client = ClientBuilder.newClient(clientConfig);
        }
        return client;
    }

    @OperationMethod(collector = DocumentModelCollector.class)
    public DocumentModel run(DocumentModel input) {
        if (StringUtils.isBlank(process)) {
            throw new NuxeoException("No process specified");
        }

        DocumentModel processInfo = session.getDocument(new IdRef(process));

        Map<String, Object> params = new HashMap<>();
        if (invokeEndpoint(input, processInfo, params)) {
            params.put("state", "Started");
            params.put("save", true);
            try {
                input = (DocumentModel) automation.run(ctx, LinkAppianRecord.ID, params);
            } catch (OperationException e) {
                throw new NuxeoException(e);
            }
        }

        return input;
    }

    private boolean invokeEndpoint(DocumentModel input, DocumentModel process, Map<String, Object> params) {
        Map<String, Object> request = new HashMap<>();

        request.put("documentIds", Collections.singletonList(input.getId()));
        if (reviewers != null && !reviewers.isEmpty()) {
            List<String> reviewerList = new ArrayList<>(reviewers);
            request.put("reviewers", reviewerList);
        } else {
            request.put("reviewers", null);
        }

        String base = Framework.getProperty("appian.base.url", "https://appian");

        Serializable path = process.getPropertyValue("appianendpoint:path");
        WebTarget target = getClient().target(base + path);
        Property query = process.getPropertyObject("appianendpoint", "queryParameters");
        if (query != null) {
            for (int i = 0; i < query.size(); i++) {
                Property p = query.get(i);
                target.queryParam((String) p.get("key").getValue(), p.get("value").getValue());
            }
        }

        Invocation.Builder builder = target.request(MediaType.WILDCARD);
        Property headers = process.getPropertyObject("appianendpoint", "headers");
        if (headers != null) {
            for (int i = 0; i < headers.size(); i++) {
                Property p = headers.get(i);
                builder.header((String) p.get("key").getValue(), p.get("value").getValue());
            }
        }

        boolean success = false;
        try {
            ObjectMapper mapper = new ObjectMapper();
            String body = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(request);
            try (Response response = builder.post(Entity.entity(body, MediaType.APPLICATION_JSON))) {
                if (response.getStatus() < 200 || response.getStatus() >= 400) {
                    LOG.warn("Unable to reach Appian API: {} {}@{}", response.getStatusInfo(),
                            Framework.getProperty("appian.username"), base + path);
                } else {
                    success = true;
                    AppianResponse result;
                    result = mapper.readerFor(AppianResponse.class).readValue(response.getEntity().toString());
                    String uuid = (String) result.getProcessInstance().get("uuid");
                    params.put("processes", Collections.singletonList(uuid));
                    LOG.warn("Started process with ID: {} ({})", uuid, result);
                }
            };

        } catch (Exception ex) {
            LOG.warn("Error connecting to Appian API", ex);
        }
        return success;
    }

}
