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

boolean componentIsEmpty = issue.components.empty

if (!componentIsEmpty){
    log.warn('The component is already installed, the script is stopped.')
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
    sql.rows('SELECT jira_component, jira_user FROM map_users_table WHERE jira_label = \'' + stringLabel + '\'')
}

if (queryResult.size() > 1) {
    log.warn('There is more than one component in database, the component is not set.')
    return
} else if (queryResult.size() == 0) {
    log.warn('There is no component for label '.concat(stringLabel).concat(' in database, the component is not set.'))
    return
}

String componentName = queryResult[0].jira_component
String jiraUserFromDb = queryResult[0].jira_user

if (componentName != null){
    log.warn('Received a component from the database successfully. Component name = '.concat(componentName))
}else{
    log.warn('Failed to get component from database. Column jira_component = null')
}

def component = projectComponentManager.findByComponentName(project.getId(), componentName)

if (component == null){
    log.warn('The component '.concat(componentName).concat(' was not found in the project '.concat(project.name)))
    return
}

def user = ComponentAccessor.getJiraAuthenticationContext().getLoggedInUser()
def issueService = ComponentAccessor.getIssueService()

def issueInputParams = new IssueInputParametersImpl().setComponentIds(component?.getId())
IssueService.UpdateValidationResult updateValidationResult = issueService.validateUpdate(user, issue.id, issueInputParams)

// update the issue but do not dispatch any event or send notification
issueService.update(user, updateValidationResult, EventDispatchOption.DO_NOT_DISPATCH, false)

// set assignee
ApplicationUser assignee
String userSource = ""

if (jiraUserFromDb != null){
    def userManager = ComponentAccessor.getUserManager()
    assignee = userManager.getUserByName(jiraUserFromDb)
    userSource = "database"
}

if (assignee == null){
    assignee = component.getComponentLead()
    userSource = "component leader"
}

def validateAssignResult = issueService.validateAssign(assignee, issue.id, assignee.username)
issueService.assign(assignee, validateAssignResult)
log.warn('Set assignee '.concat(assignee.username).concat(' from '.concat(userSource)))