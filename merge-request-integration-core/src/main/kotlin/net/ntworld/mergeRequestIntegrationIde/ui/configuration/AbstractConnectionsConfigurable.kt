package net.ntworld.mergeRequestIntegrationIde.ui.configuration

import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.ui.tabs.TabInfo
import com.intellij.openapi.project.Project as IdeaProject
import net.ntworld.mergeRequest.ProviderInfo
import net.ntworld.mergeRequest.api.ApiConnection
import net.ntworld.mergeRequest.api.ApiCredentials
import net.ntworld.mergeRequestIntegrationIde.internal.ApiCredentialsImpl
import net.ntworld.mergeRequestIntegrationIde.internal.ProviderSettingsImpl
import net.ntworld.mergeRequestIntegrationIde.service.ApplicationService
import net.ntworld.mergeRequestIntegrationIde.service.ProjectService
import net.ntworld.mergeRequestIntegrationIde.service.ProviderSettings
import net.ntworld.mergeRequestIntegrationIde.ui.util.Tabs
import net.ntworld.mergeRequestIntegrationIde.ui.util.TabsUI
import javax.swing.JComponent

abstract class AbstractConnectionsConfigurable(
    project: IdeaProject
) : SearchableConfigurable, Disposable {
    private val logger = Logger.getInstance(AbstractConnectionsConfigurable::class.java)
    private val ideaProject = project
    private val myData = mutableMapOf<String, MyProviderSettings>()
    private val myTabInfos = mutableMapOf<String, TabInfo>()
    private val myInitializedData: Map<String, MyProviderSettings> by lazy {
        val connections = mutableMapOf<String, MyProviderSettings>()
        val appConnections = ApplicationService.instance.getProviderConfigurations()
        ProjectService.getInstance(ideaProject).getProviderConfigurations().forEach {
            connections[it.id] = MyProviderSettings(
                id = it.id,
                info = it.info,
                credentials = it.credentials,
                repository = it.repository,
                sharable = it.sharable,
                deleted = false
            )
        }
        for (appConnection in appConnections) {
            if (connections.containsKey(appConnection.id)) {
                connections[appConnection.id] = MyProviderSettings(
                    id = connections[appConnection.id]!!.id,
                    info = connections[appConnection.id]!!.info,
                    credentials = connections[appConnection.id]!!.credentials,
                    repository = connections[appConnection.id]!!.repository,
                    sharable = true,
                    deleted = false
                )
            } else {
                connections[appConnection.id] = MyProviderSettings(
                    id = appConnection.id,
                    info = appConnection.info,
                    credentials = appConnection.credentials,
                    repository = "",
                    sharable = true,
                    deleted = false
                )
            }
        }
        connections.forEach { (key, value) ->
            val connectionUI = initConnection(value)
            addConnectionToTabPane(value, connectionUI)
            myData[key] = value
        }

        connections
    }
    private val myTabs: TabsUI by lazy {
        val tabs = Tabs(ideaProject, this)

        tabs.setCommonCenterActionGroupFactory {
            val actionGroup = DefaultActionGroup()
            actionGroup.add(myAddTabAction)
            actionGroup
        }

        tabs
    }
    private val myAddTabAction = object : AnAction(
        "New Connection", "Add new connection", AllIcons.Actions.OpenNewTab
    ) {
        override fun actionPerformed(e: AnActionEvent) {
            perform()
        }

        fun perform() {
            var count = myTabs.getTabs().tabCount + 1
            var name = "Connection $count"
            while (myData.containsKey(findIdFromName(name))) {
                count++
                name = "Connection $count"
            }
            val id = findIdFromName(name)
            val data = MyProviderSettings.makeDefault(id, makeProviderInfo())
            val connection = makeConnectionWithEventListener()
            connection.setName(name)

            myData[id] = data
            addConnectionToTabPane(data, connection, true)
        }
    }
    private val myConnectionListener = object : ConnectionUI.Listener {
        override fun test(connectionUI: ConnectionUI, name: String, connection: ApiConnection, shared: Boolean) {
            logger.debug("Start testing connection $name")
            try {
                assertConnectionIsValid(connection)
                logger.debug("Connection $name tested")
                connectionUI.onConnectionTested(name, connection, shared)
            } catch (exception: Exception) {
                logger.debug("Connection $name has error ${exception.message}")
                connectionUI.onConnectionError(name, connection, shared, exception)
            }
        }

        override fun verify(connectionUI: ConnectionUI, name: String, credentials: ApiCredentials, repository: String) {
            TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
        }

        override fun delete(connectionUI: ConnectionUI, name: String) {
            logger.debug("Delete connection $name")
            val id = findIdFromName(name)
            val settings = myData[id]
            if (null !== settings) {
                logger.debug("Update 'deleted' of connection $name to true")
                myData[id] = settings.copy(deleted = true)
            }

            val tabs = myTabs.getTabs()
            val tabInfo = myTabInfos[id]
            if (null !== tabInfo) {
                tabs.removeTab(tabInfo)
            }

            if (tabs.tabCount == 0) {
                myAddTabAction.perform()
            } else {
                tabs.select(tabs.getTabAt(0), true)
            }
        }

        override fun changeName(connectionUI: ConnectionUI, oldName: String, newName: String) {
            logger.debug("Name of connection change from $oldName to $newName")
            val oldId = findIdFromName(oldName)
            val newId = findIdFromName(newName)

            val oldData = myData[oldId]
            if (null !== oldData) {
                myData[newId] = oldData.copy(id = newId, deleted = false)
                myData[oldId] = oldData.copy(deleted = true)
            }

            val tabInfo = myTabInfos[oldId]
            if (null !== tabInfo) {
                tabInfo.text = newName
                myTabInfos[newId] = tabInfo
                myTabInfos.remove(oldId)
            }
        }
    }

    abstract fun makeProviderInfo(): ProviderInfo

    abstract fun findNameFromId(id: String): String

    abstract fun findIdFromName(name: String): String

    abstract fun makeConnection(): ConnectionUI

    abstract fun validateConnection(connection: ApiConnection): Boolean

    abstract fun assertConnectionIsValid(connection: ApiConnection)

    private fun makeConnectionWithEventListener(): ConnectionUI {
        val connection = makeConnection()
        connection.dispatcher.addListener(myConnectionListener)

        return connection
    }

    private fun initConnection(data: ProviderSettings): ConnectionUI {
        val ui = makeConnectionWithEventListener()
        ui.initialize(findNameFromId(data.id), data.credentials, data.sharable, data.repository)
        return ui
    }

    private fun addConnectionToTabPane(data: ProviderSettings, connectionUI: ConnectionUI, selected: Boolean = false) {
        val tabInfo = TabInfo(connectionUI.createComponent())
        tabInfo.text = findNameFromId(data.id)

        myTabInfos[data.id] = tabInfo
        myTabs.addTab(tabInfo)
        if (selected) {
            myTabs.getTabs().select(tabInfo, true)
        }
    }

    override fun isModified(): Boolean {
        val initializedData = myInitializedData.filter { validateProviderSettings(it.value) }
        val validData = myData.filter { validateProviderSettings(it.value) }
        if (validData.size != initializedData.size) {
            logger.info("Size of valid data & initialized data is not match")
            return true
        }

        for (entry in validData) {
            if (!initializedData.containsKey(entry.key)) {
                logger.info("Initialized data does not contains ${entry.key}")
                return true
            }
            val initializedItem = initializedData[entry.key]
            if (null === initializedItem) {
                logger.info("Initialized data does not contains ${entry.key}")
                return true
            }
            if (!initializedItem.isEquals(entry.value)) {
                logger.info("Initialized data and data of ${entry.key} is not equals")
                return true
            }
        }

        logger.info("All data are matched, not modified")
        return false
    }

    override fun apply() {
        for (entry in myData) {
            if (entry.value.deleted) {
                println("Delete connection ${entry.key}")
                logger.info("Delete connection ${entry.key}")
                continue
            }

            if (validateProviderSettings(entry.value)) {
                logger.info("Save connection ${entry.key}")
                println("Save connection ${entry.key}")
                println("id: ${entry.value.id}")
                println("projectId: ${entry.value.credentials.projectId}")
                println("url: ${entry.value.credentials.url}")
                println("login: ${entry.value.credentials.login}")
                println("token: ${entry.value.credentials.token}")
                println("ignoreSSLCertificateErrors: ${entry.value.credentials.ignoreSSLCertificateErrors}")
                println("repository: ${entry.value.repository}")
                println("--------------------------")
            }
        }
    }

    override fun dispose() {
        myData.clear()
        myTabInfos.clear()
    }

    override fun createComponent(): JComponent? = myTabs.component

    private fun validateProviderSettings(providerSettings: ProviderSettings): Boolean {
        return validateConnection(providerSettings.credentials) &&
            providerSettings.repository.isNotEmpty() &&
            providerSettings.credentials.projectId.isNotEmpty()
    }

    private data class MyProviderSettings(
        override val id: String,
        override val info: ProviderInfo,
        override val credentials: ApiCredentials,
        override val repository: String,
        override val sharable: Boolean,
        val deleted: Boolean
    ) : ProviderSettings {

        fun isEquals(other: MyProviderSettings): Boolean {
            return id == other.id &&
                info.id == other.info.id &&
                isCredentialsEquals(other.credentials) &&
                repository == other.repository &&
                sharable == other.sharable &&
                deleted == other.deleted
        }

        private fun isCredentialsEquals(other: ApiCredentials): Boolean {
            return credentials.projectId == other.projectId &&
                credentials.version == other.version &&
                credentials.info == other.info &&
                credentials.url == other.url &&
                credentials.ignoreSSLCertificateErrors == other.ignoreSSLCertificateErrors &&
                credentials.login == other.login &&
                credentials.token == other.token
        }

        companion object {
            fun makeDefault(id: String, info: ProviderInfo): MyProviderSettings {
                return MyProviderSettings(
                    id = id,
                    info = info,
                    credentials = ApiCredentialsImpl(
                        url = "",
                        login = "",
                        token = "",
                        projectId = "",
                        version = "",
                        info = "",
                        ignoreSSLCertificateErrors = false
                    ),
                    repository = "",
                    sharable = false,
                    deleted = false
                )
            }
        }
    }
}