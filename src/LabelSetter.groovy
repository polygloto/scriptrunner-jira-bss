import com.atlassian.jira.bc.issue.IssueService
import com.atlassian.jira.component.ComponentAccessor
import com.atlassian.jira.event.type.EventDispatchOption
import com.atlassian.jira.issue.Issue
import com.atlassian.jira.issue.IssueInputParametersImpl
import com.atlassian.jira.issue.label.LabelManager


def issue = event.issue as Issue
def user = ComponentAccessor.getJiraAuthenticationContext().getLoggedInUser()
def labels = issue.getLabels()
def labelManager = ComponentAccessor.getComponent(LabelManager)

if (labels.empty){
    labelManager.addLabel(user, issue.getId(), "intelmed", false)
}

def issueService = ComponentAccessor.getIssueService()
def issueInputParams = new IssueInputParametersImpl()
IssueService.UpdateValidationResult updateValidationResult = issueService.validateUpdate(user, issue.id, issueInputParams)

issueService.update(user, updateValidationResult, EventDispatchOption.ISSUE_UPDATED, false)