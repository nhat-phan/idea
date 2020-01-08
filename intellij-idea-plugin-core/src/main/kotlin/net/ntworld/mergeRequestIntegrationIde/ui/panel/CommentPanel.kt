package net.ntworld.mergeRequestIntegrationIde.ui.panel

import com.intellij.ide.BrowserUtil
import com.intellij.ide.util.TipUIUtil
import com.intellij.openapi.ui.Messages
import com.intellij.ui.JBColor
import com.intellij.ui.ScrollPaneFactory
import com.intellij.util.EventDispatcher
import net.ntworld.mergeRequest.Comment
import net.ntworld.mergeRequest.MergeRequest
import net.ntworld.mergeRequest.ProviderData
import net.ntworld.mergeRequest.command.DeleteCommentCommand
import net.ntworld.mergeRequestIntegration.make
import net.ntworld.mergeRequestIntegration.util.DateTimeUtil
import net.ntworld.mergeRequestIntegrationIde.service.ApplicationService
import net.ntworld.mergeRequestIntegrationIde.ui.Component
import net.ntworld.mergeRequestIntegrationIde.ui.mergeRequest.tab.MergeRequestDescriptionTab
import net.ntworld.mergeRequestIntegrationIde.ui.util.HtmlHelper
import net.ntworld.mergeRequestIntegrationIde.ui.util.Icons
import java.awt.event.ActionListener
import java.util.*
import javax.swing.*

class CommentPanel : Component {
    var myWholePanel: JPanel? = null
    var myFullName: JLabel? = null
    var myUsername: JLabel? = null
    var myReplyButton: JButton? = null
    var myOpenButton: JButton? = null
    var myDeleteButton: JButton? = null
    var myHeaderWrapper: JPanel? = null
    var myContentWrapper: JPanel? = null
    var myTime: JLabel? = null
    val dispatcher = EventDispatcher.create(Listener::class.java)

    private var myProviderData: ProviderData? = null
    private var myMergeRequest: MergeRequest? = null
    private var myComment: Comment? = null
    private var myUrl: String = ""
    private val myHtmlTemplate = MergeRequestDescriptionTab::class.java.getResource(
        "/templates/mr.comment.html"
    ).readText()

    private val myWebView = TipUIUtil.createBrowser() as TipUIUtil.Browser
    private val myDeleteButtonActionListener = ActionListener {
        val providerData = myProviderData
        val mergeRequest = myMergeRequest
        val comment = myComment
        if (null === providerData || null === mergeRequest || null === comment) {
            return@ActionListener
        }

        val result = Messages.showYesNoDialog(
            "Do you want to delete the comment?", "Are you sure", Messages.getQuestionIcon()
        )
        if (result == Messages.YES) {
            ApplicationService.instance.infrastructure.commandBus() process DeleteCommentCommand.make(
                providerId = providerData.id,
                mergeRequestId = mergeRequest.id,
                comment = comment
            )
            dispatcher.multicaster.onDestroyRequested(providerData, mergeRequest, comment)
        }
    }

    init {
        myContentWrapper!!.add(ScrollPaneFactory.createScrollPane(myWebView.component))
        myContentWrapper!!.border = BorderFactory.createMatteBorder(1, 0, 0, 0, JBColor.border())
        myOpenButton!!.addActionListener {
            if (myUrl.isNotEmpty()) {
                BrowserUtil.open(myUrl)
            }
        }

        myDeleteButton!!.icon = Icons.Trash
        myDeleteButton!!.isVisible = false
        myDeleteButton!!.addActionListener(myDeleteButtonActionListener)
        myReplyButton!!.addActionListener {
            dispatcher.multicaster.onReplyButtonClick()
        }
    }

    fun setComment(providerData: ProviderData, mergeRequest: MergeRequest, comment: Comment) {
        myProviderData = providerData
        myMergeRequest = mergeRequest
        myComment = comment

        myFullName!!.text = comment.author.name
        myUsername!!.text = "@${comment.author.username}"
        myUrl = providerData.info.createCommentUrl(mergeRequest.url, comment)

        val createdAt = DateTimeUtil.toDate(comment.createdAt)
        myTime!!.text = "${DateTimeUtil.formatDate(createdAt)} · ${DateTimeUtil.toPretty(createdAt)}"

        myDeleteButton!!.isVisible = comment.author.id == providerData.currentUser.id
        myWebView.text = buildHtml(providerData, comment)
    }

    private fun buildHtml(providerData: ProviderData, comment: Comment): String {
        val output = myHtmlTemplate
            .replace("{{content}}", HtmlHelper.convertFromMarkdown(comment.body))

        return HtmlHelper.resolveRelativePath(providerData, output)
    }

    override fun createComponent(): JComponent = myWholePanel!!

    interface Listener : EventListener {
        fun onReplyButtonClick()

        fun onDestroyRequested(
            providerData: ProviderData,
            mergeRequest: MergeRequest,
            comment: Comment
        )
    }
}