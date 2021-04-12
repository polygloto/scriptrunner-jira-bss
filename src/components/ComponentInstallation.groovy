package components

import com.atlassian.jira.bc.issue.IssueService
import com.atlassian.jira.component.ComponentAccessor
import com.atlassian.jira.event.type.EventDispatchOption
import com.atlassian.jira.issue.Issue
import com.atlassian.jira.issue.IssueInputParametersImpl
import com.atlassian.jira.issue.label.Label

// script settings
String labelDefault = "test-label"
String labelZup = "test-label-zup"
String componentDefault = "test component"
String componentZup = "test component zup"

def issue = event.issue as Issue
def project = issue.getProjectObject()
def projectComponentManager = ComponentAccessor.getProjectComponentManager()
def component
List<String> stringLabelList = new ArrayList<>()

for (Label label : issue.getLabels()) {
    stringLabelList.add(label.toString())
}

if (stringLabelList.contains(labelZup)) {
    component = projectComponentManager.findByComponentName(project.getId(), componentZup)
    log.warn('setting zup component')
}else if (stringLabelList.contains(labelDefault)){
    component = projectComponentManager.findByComponentName(project.getId(), componentDefault)
    log.warn('setting default component')
}else {
    log.warn('labels not found in script settings')
    return
}

def user = ComponentAccessor.getJiraAuthenticationContext().getLoggedInUser()
def issueService = ComponentAccessor.getIssueService()

def issueInputParams = new IssueInputParametersImpl().setComponentIds(component?.getId())
IssueService.UpdateValidationResult updateValidationResult = issueService.validateUpdate(user, issue.id, issueInputParams)
// update the issue but do not dispatch any event or send notification
issueService.update(user, updateValidationResult, EventDispatchOption.DO_NOT_DISPATCH, false)