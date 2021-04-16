package components

import com.atlassian.jira.bc.issue.IssueService
import com.atlassian.jira.component.ComponentAccessor
import com.atlassian.jira.event.type.EventDispatchOption
import com.atlassian.jira.issue.Issue
import com.atlassian.jira.issue.IssueInputParametersImpl
import com.atlassian.jira.issue.label.Label
import com.atlassian.jira.user.ApplicationUser
import com.onresolve.scriptrunner.db.DatabaseUtil

def issue = event.issue as Issue
def project = issue.getProjectObject()
def projectComponentManager = ComponentAccessor.getProjectComponentManager()
def labels = issue.getLabels()

if (issue.getComponents().size() > 0){
    log.warn('The component (${issue.getComponents().size().toString()}) is already installed, the script is stopped.')
    return
}

if (labels.size() > 1) {
    log.warn('There is more than one label in the issue, the component is not set.')
    return
} else if (labels.size() == 0) {
    log.warn('There are no labels in the task, the component is not set.')
    return
}

def stringLabel = labels[0].toString()

def queryResult = DatabaseUtil.withSql('map_users_db') { sql ->
    sql.rows('SELECT jira_component FROM map_users_table WHERE jira_label = \'' + stringLabel + '\'')
}

if (queryResult.size() > 1) {
    log.warn('There is more than one component in database, the component is not set.')
    return
} else if (queryResult.size() == 0) {
    log.warn('There is no component for label ${stringLabel} in database, the component is not set.')
    return
}

String componentName = queryResult[0].jira_component

if (componentName != null){
    log.warn('Received a component from the database successfully. Component name = '.concat(componentName))
}else{
    log.warn('Failed to get component from database. Column jira_component = null')
}

def component = projectComponentManager.findByComponentName(project.getId(), componentName)

if (component == null){
    log.warn('The component ${componentName} was not found in the project ${project.name.toString()}')
    return
}

def user = ComponentAccessor.getJiraAuthenticationContext().getLoggedInUser()
def issueService = ComponentAccessor.getIssueService()

def issueInputParams = new IssueInputParametersImpl().setComponentIds(component?.getId())
IssueService.UpdateValidationResult updateValidationResult = issueService.validateUpdate(user, issue.id, issueInputParams)

// update the issue but do not dispatch any event or send notification
issueService.update(user, updateValidationResult, EventDispatchOption.DO_NOT_DISPATCH, false)

// set assignee
ApplicationUser componentLead = component.getComponentLead()
def validateAssignResult = issueService.validateAssign(componentLead, issue.id, componentLead.username)
issueService.assign(componentLead, validateAssignResult)
log.warn('Set assignee {$componentLead.username}')