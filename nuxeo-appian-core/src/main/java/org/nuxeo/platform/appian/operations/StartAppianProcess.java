package org.nuxeo.platform.appian.operations;

import java.io.InputStream;
import java.io.Serializable;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import javax.ws.rs.core.MediaType;

import org.apache.commons.lang3.StringUtils;
import org.nuxeo.ecm.automation.AutomationService;
import org.nuxeo.ecm.automation.OperationContext;
import org.nuxeo.ecm.automation.OperationException;
import org.nuxeo.ecm.automation.core.Constants;
import org.nuxeo.ecm.automation.core.annotations.Context;
import org.nuxeo.ecm.automation.core.annotations.Operation;
import org.nuxeo.ecm.automation.core.annotations.OperationMethod;
import org.nuxeo.ecm.automation.core.annotations.Param;
import org.nuxeo.ecm.automation.core.collectors.DocumentModelCollector;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.IdRef;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.core.api.model.Property;
import org.nuxeo.platform.appian.model.AppianRequest;
import org.nuxeo.platform.appian.model.AppianResponse;
import org.nuxeo.runtime.api.Framework;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.api.client.config.ClientConfig;
import com.sun.jersey.api.client.config.DefaultClientConfig;
import com.sun.jersey.api.client.filter.HTTPBasicAuthFilter;

/**
 *
 */
@Operation(id = StartAppianProcess.ID, category = Constants.CAT_DOCUMENT, label = "Start Appian Process", description = "Start a named Appian process with the input document.")
public class StartAppianProcess {

    protected static final Logger LOG = LoggerFactory.getLogger(StartAppianProcess.class);

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

    protected Client getClient() {
        if (client == null) {
            ClientConfig cc = new DefaultClientConfig();
            cc.getProperties().put(ClientConfig.PROPERTY_FOLLOW_REDIRECTS, true);
            client = Client.create(cc);

            String user = Framework.getProperty("appian.username");
            String pass = Framework.getProperty("appian.password");
            client.addFilter(new HTTPBasicAuthFilter(user, pass));
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
        AppianRequest request = new AppianRequest();

        request.setVariable("documentIds", Collections.singletonList(input.getId()));

        String base = Framework.getProperty("appian.base.url", "https://appian");

        Serializable path = process.getPropertyValue("appianendpoint:path");
        WebResource resource = getClient().resource(base + path);
        Property query = process.getPropertyObject("appianendpoint", "queryParameters");
        if (query != null) {
            for (int i = 0; i < query.size(); i++) {
                Property p = query.get(i);
                resource.queryParam((String) p.get("key").getValue(), (String) p.get("value").getValue());
            }
        }

        WebResource.Builder builder = resource.accept(MediaType.WILDCARD);
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
            String body = mapper.writerFor(AppianRequest.class).writeValueAsString(request);

            builder.type(MediaType.APPLICATION_JSON);
            ClientResponse response = builder.post(ClientResponse.class, body);
            if (response.getStatus() < 200 || response.getStatus() >= 400) {
                LOG.warn("Unable to reach Appian API: {} {}@{}", response.getStatusInfo(),
                        Framework.getProperty("appian.username"), base + path);
            } else {
                success = true;
                AppianResponse result = null;
                try (InputStream in = response.getEntityInputStream()) {
                    result = mapper.readerFor(AppianResponse.class).readValue(in);
                }
                String uuid = (String) result.getProcessInstance().get("uuid");
                params.put("processes", Collections.singletonList(uuid));
                LOG.warn("Started process with ID: {} ({})", uuid, result);
            }
        } catch (Exception ex) {
            LOG.warn("Error connecting to Appian API", ex);
        }
        return success;
    }

}
