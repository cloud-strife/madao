package idv.cloudstrife.jira.customfields;

import com.atlassian.jira.exception.AttachmentNotFoundException;
import com.atlassian.jira.issue.AttachmentManager;
import com.atlassian.jira.issue.Issue;
import com.atlassian.jira.issue.attachment.Attachment;
import com.atlassian.jira.issue.customfields.impl.AbstractSingleFieldType;
import com.atlassian.jira.issue.customfields.impl.FieldValidationException;
import com.atlassian.jira.issue.customfields.manager.GenericConfigManager;
import com.atlassian.jira.issue.customfields.persistence.CustomFieldValuePersister;
import com.atlassian.jira.issue.customfields.persistence.PersistenceFieldType;
import com.atlassian.jira.issue.fields.CustomField;
import com.atlassian.jira.issue.fields.layout.field.FieldLayoutItem;
import com.atlassian.jira.issue.fields.rest.FieldJsonRepresentation;
import com.atlassian.jira.issue.fields.rest.FieldTypeInfo;
import com.atlassian.jira.issue.fields.rest.FieldTypeInfoContext;
import com.atlassian.jira.issue.fields.rest.RestAwareCustomFieldType;
import com.atlassian.jira.issue.fields.rest.json.JsonData;
import com.atlassian.jira.issue.fields.rest.json.JsonType;
import com.atlassian.jira.issue.fields.rest.json.JsonTypeBuilder;
import com.atlassian.jira.issue.fields.rest.json.beans.JiraBaseUrls;
import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import org.apache.commons.lang3.StringUtils;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Named;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static java.lang.String.format;
import static java.util.stream.Collectors.toList;

@Named
public class SingleAttachmentPicker extends AbstractSingleFieldType<Attachment> implements RestAwareCustomFieldType {
    private AttachmentManager attachmentManager;
    private JiraBaseUrls jiraBaseUrls;

    @Inject
    protected SingleAttachmentPicker(@ComponentImport CustomFieldValuePersister customFieldValuePersister,
                                     @ComponentImport GenericConfigManager genericConfigManager,
                                     @ComponentImport AttachmentManager attachmentManager,
                                     @ComponentImport JiraBaseUrls jiraBaseUrls) {
        super(customFieldValuePersister, genericConfigManager);
        this.attachmentManager = attachmentManager;
        this.jiraBaseUrls = jiraBaseUrls;
    }

    @Nonnull
    @Override
    protected PersistenceFieldType getDatabaseType() {
        return PersistenceFieldType.TYPE_LIMITED_TEXT;
    }

    @Nullable
    @Override
    protected Object getDbValueFromObject(Attachment attachment) {
        return attachment == null ? null : attachment.getId().toString();
    }

    @Nullable
    @Override
    protected Attachment getObjectFromDbValue(@Nonnull Object o) throws FieldValidationException {
        try {
            return attachmentManager.getAttachment(Long.parseLong(o.toString()));
        } catch (AttachmentNotFoundException e) {
            return null;
        }
    }

    @Override
    public String getStringFromSingularObject(Attachment attachment) {
        return attachment == null ? null : attachment.getId().toString();
    }

    @Override
    public Attachment getSingularObjectFromString(String s) throws FieldValidationException {
        return StringUtils.isBlank(s) ? null : getObjectFromDbValue(s);
    }

    @Nonnull
    @Override
    public Map<String, Object> getVelocityParameters(Issue issue, CustomField field, FieldLayoutItem fieldLayoutItem) {
        Map<String, Object> params = super.getVelocityParameters(issue, field, fieldLayoutItem);

        if (issue != null && issue.getId() != null) {
            Collection<Attachment> attachments = Optional.ofNullable(issue.getAttachments()).orElse(Lists.newArrayList());

            params.put("attachments", attachments);
            params.put("selectedAttachment", field.getValue(issue));
        }
        return params;
    }

    @Override
    public FieldTypeInfo getFieldTypeInfo(FieldTypeInfoContext fieldTypeInfoContext) {
        fieldTypeInfoContext.getIssue().getAttachments();
        List<Object> values = fieldTypeInfoContext.getIssue().getAttachments().stream().map(att ->
                ImmutableMap.builder()
                        .put("id", att.getId())
                        .put("value", att.getFilename())
                        .put("self", format("%s/rest/api/2/attachment/%s", jiraBaseUrls.baseUrl(), att.getId()))
                        .build())
                .collect(toList());


        return new FieldTypeInfo(values, null);
    }

    @Override
    public JsonType getJsonSchema(CustomField customField) {
        return JsonTypeBuilder.custom("option", this.getKey(), customField.getIdAsLong());
    }

    @Override
    public FieldJsonRepresentation getJsonFromIssue(CustomField customField, Issue issue, boolean b,
                                                    @Nullable FieldLayoutItem fieldLayoutItem) {
        Attachment attachment = this.getValueFromIssue(customField, issue);
        Object attributes = Optional.ofNullable(attachment).map(att ->
                ImmutableMap.builder()
                        .put("id", att.getId())
                        .put("filename", att.getFilename())
                        .put("content", format("%s/secure/attachment/%s/%s",
                                jiraBaseUrls.baseUrl(), att.getId(), att.getFilename()))
                        .build())
                .orElse(null);

        return new FieldJsonRepresentation(new JsonData(attributes));
    }
}
