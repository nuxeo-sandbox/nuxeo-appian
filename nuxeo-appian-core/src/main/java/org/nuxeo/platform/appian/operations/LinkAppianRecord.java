package org.nuxeo.platform.appian.operations;

import java.util.Date;

import org.nuxeo.ecm.automation.core.Constants;
import org.nuxeo.ecm.automation.core.annotations.Context;
import org.nuxeo.ecm.automation.core.annotations.Operation;
import org.nuxeo.ecm.automation.core.annotations.OperationMethod;
import org.nuxeo.ecm.automation.core.annotations.Param;
import org.nuxeo.ecm.automation.core.collectors.DocumentModelCollector;
import org.nuxeo.ecm.automation.core.util.StringList;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;

/**
 *
 */
@Operation(id = LinkAppianRecord.ID, category = Constants.CAT_DOCUMENT, label = "Appian Record Link", description = "Link a document to an Appian record.")
public class LinkAppianRecord {

    public static final String ID = "Document.LinkAppianRecord";

    @Context
    protected CoreSession session;

    @Param(name = "state", required = false)
    protected String state;

    @Param(name = "processes", required = false)
    protected StringList processes;

    @Param(name = "links", required = false)
    protected StringList links;

    @Param(name = "save", required = false)
    protected boolean save;

    @OperationMethod(collector = DocumentModelCollector.class)
    public DocumentModel run(DocumentModel input) {
        if (!input.hasFacet("Appian")) {
            input.addFacet("Appian");
        }

        if (state != null) {
            input.setPropertyValue("appian:state", state);
        }
        if (processes != null) {
            input.setPropertyValue("appian:processes", processes);
        }
        if (links != null) {
            input.setPropertyValue("appian:links", links);
        }

        input.setPropertyValue("appian:synchronized", new Date());

        input = session.saveDocument(input);
        if (save) {
            session.save();
        }

        return input;
    }
}
