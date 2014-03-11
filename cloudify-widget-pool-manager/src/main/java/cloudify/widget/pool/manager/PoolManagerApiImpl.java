package cloudify.widget.pool.manager;

import cloudify.widget.pool.manager.dto.*;
import cloudify.widget.pool.manager.tasks.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * User: eliranm
 * Date: 3/9/14
 * Time: 7:50 PM
 */
public class PoolManagerApiImpl implements PoolManagerApi {

    private static Logger logger = LoggerFactory.getLogger(PoolManagerApiImpl.class);

    @Autowired
    private NodesDataAccessManager nodesDataAccessManager;

    @Autowired
    private TaskErrorsDataAccessManager taskErrorsDataAccessManager;

    @Autowired
    private SettingsDataAccessManager settingsDataAccessManager;

    @Autowired
    private StatusManager statusManager;

    @Autowired
    private TaskExecutor taskExecutor;

    private String bootstrapScriptResourcePath;


    @Override
    public PoolStatus getStatus(PoolSettings poolSettings) {
        if (poolSettings == null) return null;
        return statusManager.getStatus(poolSettings);

        // mocking
//        return new PoolStatus().minNodes(poolSettings.getMinNodes()).maxNodes(poolSettings.getMaxNodes()).currentSize(poolSettings.getMaxNodes() - 1);
    }

    @Override
    public Collection<PoolStatus> listStatuses() {
        Collection<PoolStatus> poolStatuses = new ArrayList<PoolStatus>();
        PoolsSettingsList poolSettingsList = _getPools();
        if (poolSettingsList == null) return null;
        for (PoolSettings poolSettings : poolSettingsList) {
            poolStatuses.add(statusManager.getStatus(poolSettings));
        }
        return poolStatuses;

//        mocking
//        ArrayList<PoolStatus> statuses = new ArrayList<PoolStatus>();
//        PoolsSettingsList poolSettingsList = _getPools();
//        if (poolSettingsList == null) return null;
//        for (PoolSettings poolSettings : poolSettingsList) {
//            statuses.add(getStatus(poolSettings));
//        }
//        return statuses;
    }

    @Override
    public ManagerSettings getSettings() {
        return settingsDataAccessManager.read();
    }

    @Override
    public List<NodeModel> listNodes(PoolSettings poolSettings) {
        if (poolSettings == null) return null;
        return nodesDataAccessManager.listNodes(poolSettings);
    }

    @Override
    public NodeModel getNode(long nodeId) {
        return nodesDataAccessManager.getNode(nodeId);
    }

    @Override
    public void createNode(PoolSettings poolSettings, TaskCallback taskCallback) {
        if (poolSettings == null) return;
        taskExecutor.execute(CreateMachineTask.class, null, poolSettings, taskCallback);
    }

    @Override
    public void deleteNode(long nodeId) {
        final NodeModel node = _getNodeModel(nodeId);
        if (node == null) return;
        PoolSettings poolSettings = _getPoolSettings(node.poolId);
        if (poolSettings == null) return;
        taskExecutor.execute(DeleteMachineTask.class, new DeleteMachineTaskConfig() {
            @Override
            public NodeModel getNodeModel() {
                return node;
            }
        }, poolSettings, null);
    }

    @Override
    public void bootstrapNode(long nodeId) {
        final NodeModel node = _getNodeModel(nodeId);
        PoolSettings poolSettings = _getPoolSettings(node.poolId);
        taskExecutor.execute(BootstrapMachineTask.class, new BootstrapMachineTaskConfig() {
            @Override
            public String getBootstrapScriptResourcePath() {
                return bootstrapScriptResourcePath;
            }
            @Override
            public NodeModel getNodeModel() {
                return node;
            }
        }, poolSettings, null);

    }

    @Override
    public List<TaskErrorModel> listTaskErrors(PoolSettings poolSettings) {
        if (poolSettings == null) return null;
        return taskErrorsDataAccessManager.listTaskErrors(poolSettings);
    }

    @Override
    public TaskErrorModel getTaskError(long errorId) {
        return taskErrorsDataAccessManager.getTaskError(errorId);
    }

    @Override
    public void removeTaskError(long errorId) {
        taskErrorsDataAccessManager.removeTaskError(errorId);
    }


    private NodeModel _getNodeModel(long nodeId) {
        final NodeModel node = nodesDataAccessManager.getNode(nodeId);
        if (node == null) {
            logger.error("node with id [{}] not found", nodeId);
        }
        return node;
    }

    private PoolsSettingsList _getPools() {
        PoolsSettingsList poolsSettingsList = settingsDataAccessManager.read().getPools();
        if (poolsSettingsList == null || poolsSettingsList.isEmpty()) {
            logger.error("pool settings list not found, or is empty");
        }
        return poolsSettingsList;
    }

    private PoolSettings _getPoolSettings(String poolSettingsId) {
        PoolSettings poolSettings = _getPools().getById(poolSettingsId);
        if (poolSettings == null) {
            logger.error("pool settings with id [{}] not found.", poolSettingsId);
        }
        return poolSettings;
    }


    public void setSettingsDataAccessManager(SettingsDataAccessManager settingsDataAccessManager) {
        this.settingsDataAccessManager = settingsDataAccessManager;
    }

    public void setTaskErrorsDataAccessManager(TaskErrorsDataAccessManager taskErrorsDataAccessManager) {
        this.taskErrorsDataAccessManager = taskErrorsDataAccessManager;
    }

    public void setNodesDataAccessManager(NodesDataAccessManager nodesDataAccessManager) {
        this.nodesDataAccessManager = nodesDataAccessManager;
    }

    public void setStatusManager(StatusManager statusManager) {
        this.statusManager = statusManager;
    }

    public void setTaskExecutor(TaskExecutor taskExecutor) {
        this.taskExecutor = taskExecutor;
    }

    public void setBootstrapScriptResourcePath(String bootstrapScriptResourcePath) {
        this.bootstrapScriptResourcePath = bootstrapScriptResourcePath;
    }
}
