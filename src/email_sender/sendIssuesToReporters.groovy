package email_sender

import com.atlassian.jira.bc.issue.search.SearchService
import com.atlassian.jira.component.ComponentAccessor
import com.atlassian.jira.issue.MutableIssue
import com.atlassian.jira.user.util.UserUtil
import com.atlassian.jira.web.bean.PagerFilter
import groovy.xml.MarkupBuilder
import java.text.SimpleDateFormat
import com.atlassian.mail.Email
import com.atlassian.mail.server.SMTPMailServer

def appUser = ComponentAccessor.getJiraAuthenticationContext().getLoggedInUser()
def searchService = ComponentAccessor.getComponent(SearchService.class)
def issueManager = ComponentAccessor.getIssueManager()
def groupList = new ArrayList<String>()
def baseurl = ComponentAccessor.getApplicationProperties().getString("jira.baseurl")
def emailSubject = "У вас есть незакрытые задачи в Jira."
def simpleDateFormat = new SimpleDateFormat("dd.MM.yyyy")

UserUtil userUtil = ComponentAccessor.getUserUtil()
groupList.add("reporters-group")

def usersList = userUtil.getAllUsersInGroupNames(groupList)

usersList.each { user ->
    def jqlSearch = "status in (Open, \"In Progress\", \"To Do\", \"In Review\", \"Under Review\") AND ((labels != '1Cupd' and labels != 'mtnc1C') OR labels is EMPTY) AND reporter in (${user.username}) order by due asc, created asc"
    //Get all unclosed tasks of this user from the database.
    SearchService.ParseResult parseResult = searchService.parseQuery(appUser, jqlSearch)

    if (parseResult.isValid()) {
        def searchResult = searchService.search(appUser, parseResult.getQuery(), PagerFilter.getUnlimitedFilter())
        def issues = searchResult.results.collect { issueManager.getIssueObject(it.id) }

        if (issues.size() > 0) {
            def body = "${user.displayName}, вы являетесть автором ${issues.size()} незакрытых задач, просьба уточнить их текущий статус. \n\n"
            def writer = new StringWriter()
            def xml = new MarkupBuilder(writer)
            String issueLink, createdDate, reporter, assignee, reporterEmail = "", summary, status, projectKey, projectLink, due

            xml.table(id: "scriptField", style:"border-width:1px;border-style:solid;border-color:black;border-collapse:collapse;") {
                tr(style:"border-width:1px;border-style:solid;border-color:black;") {
                    th("Проект", style:"border-width:1px;border-style:solid;border-color:black;padding:5px;")
                    th("Задача", style:"border-width:1px;border-style:solid;border-color:black;padding:5px;")
                    th("Создана", style:"border-width:1px;border-style:solid;border-color:black;padding:5px;")
                    th("Срок", style:"border-width:1px;border-style:solid;border-color:black;padding:5px;")
                    th("Автор", style:"border-width:1px;border-style:solid;border-color:black;padding:5px;")
                    th("Исполнитель", style:"border-width:1px;border-style:solid;border-color:black;padding:5px;")
                    th("Статус", style:"border-width:1px;border-style:solid;border-color:black;padding:5px;")
                }

                issues.each { i ->
                    MutableIssue issue = (MutableIssue) i
                    issueLink = baseurl + "/browse/" + issue.key
                    createdDate = simpleDateFormat.format(issue.getCreated())
                    reporter = issue.getReporter().name
                    assignee = (issue.getAssignee() == null) ? "" : issue.getAssignee().name
                    reporterEmail = issue.getReporter().emailAddress
                    summary = issue.getSummary()
                    status = issue.getStatus().name
                    projectKey = issue.getProjectObject().key
                    projectLink = baseurl + "/projects/" + issue.getProjectObject().key
                    due = (issue.getDueDate() == null) ? "" : simpleDateFormat.format(issue.getDueDate())

                    tr {
                        td(style: "border-width:1px;border-style:solid;border-color:black;padding:5px;"){a(href: projectLink, projectKey)}
                        td(style: "border-width:1px;border-style:solid;border-color:black;padding:5px;"){a(href: issueLink, summary)}
                        td(createdDate, style:"border-width:1px;border-style:solid;border-color:black;padding:5px;")
                        td(due, style:"border-width:1px;border-style:solid;border-color:black;padding:5px;")
                        td(reporter, style:"border-width:1px;border-style:solid;border-color:black;padding:5px;")
                        td(assignee, style:"border-width:1px;border-style:solid;border-color:black;padding:5px;")
                        td(status, style:"border-width:1px;border-style:solid;border-color:black;padding:5px;")
                    }
                }
                body += writer.toString()
            }
            sendEmail(reporterEmail, emailSubject, body, user.displayName)
        } else {
            log.warn("Invalid JQL :" + jqlSearch)
        }
    }
}

static def sendEmail(String emailAddressee, String subject, String body, String employee) {
    SMTPMailServer mailServer = ComponentAccessor.getMailServerManager().getDefaultSMTPMailServer()

    if (mailServer) {
        // send email to employee
        Email email = new Email(emailAddressee)
        email.setMimeType("text/html")
        email.setSubject(subject)
        email.setBody(body)
        mailServer.send(email)

    } else {
        //Problem getting the mail server from JIRA configuration
    }
}
