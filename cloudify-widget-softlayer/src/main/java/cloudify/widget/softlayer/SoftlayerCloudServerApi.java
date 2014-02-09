package cloudify.widget.softlayer;

import cloudify.widget.api.clouds.*;
import org.apache.commons.lang3.StringUtils;
import org.jclouds.compute.ComputeService;
import org.jclouds.compute.domain.*;
import org.jclouds.softlayer.SoftLayerApi;
import org.jclouds.softlayer.domain.VirtualGuest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * User: eliranm
 * Date: 2/4/14
 * Time: 3:41 PM
 */
public class SoftlayerCloudServerApi implements CloudServerApi {

    private static Logger logger = LoggerFactory.getLogger(SoftlayerCloudServerApi.class);

    private final ComputeService computeService;
    private final SoftLayerApi softLayerApi;

    public SoftlayerCloudServerApi(ComputeService computeService, SoftLayerApi softLayerApi) {
        this.computeService = computeService;
        this.softLayerApi = softLayerApi;
    }

    @Override
    public Collection<CloudServer> getAllMachinesWithTag(String tag) {
        List<CloudServer> cloudServers = new LinkedList<CloudServer>();
        for (ComputeMetadata computeMetadata : computeService.listNodes()) {
            computeService.getNodeMetadata(computeMetadata.getId());
            if (computeMetadata.getTags().contains(tag)) {
            }
            cloudServers.add(new SoftlayerCloudServer(computeMetadata));
        }
        return cloudServers;
    }

    @Override
    public CloudServer get(String serverId) {
        CloudServer cloudServer = null;

        VirtualGuest virtualGuest = softLayerApi.getVirtualGuestClient().getVirtualGuest(0);
        logger.info("virtual guest: [{}]", virtualGuest);
/*
        Server server = softLayerApi.get(serverId);
        if (server != null) {
            cloudServer = new SoftlayerCloudServer(server);
        }
*/
        return cloudServer;
    }

    @Override
    public boolean delete(String id) {
        return false;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public void rebuild(String id) {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public Collection<CloudServerCreated> create( MachineOptions machineOpts ) {

        SoftlayerMachineOptions softlayerMachineOptions = ( SoftlayerMachineOptions )machineOpts;
        String name = softlayerMachineOptions.name();
        int machinesCount = softlayerMachineOptions.machinesCount();
        Template template = createTemplate(softlayerMachineOptions);
        Set<? extends NodeMetadata> newNodes;
        try {
            newNodes = computeService.createNodesInGroup( name, machinesCount, template );
        }
        catch (org.jclouds.compute.RunNodesException e) {
            if( logger.isErrorEnabled() ){
                logger.error( "Create softlayer node failed", e );
            }
            throw new RuntimeException( e );
        }

        List<CloudServerCreated> newNodesList = new ArrayList<CloudServerCreated>( newNodes.size() );
        for( NodeMetadata newNode : newNodes ){
            newNodesList.add( new SoftlayerCloudServerCreated( newNode ) );
        }

        return newNodesList;
    }

    private Template createTemplate( SoftlayerMachineOptions machineOptions ) {
        TemplateBuilder templateBuilder = computeService.templateBuilder();

        String hardwareId = machineOptions.hardwareId();
        String locationId = machineOptions.locationId();
        OsFamily osFamily = machineOptions.osFamily();
        if( osFamily != null ){
            templateBuilder.osFamily(osFamily);
        }
        if( !StringUtils.isEmpty(hardwareId)){
            templateBuilder.hardwareId( hardwareId );
        }
        if( !StringUtils.isEmpty( locationId ) ){
            templateBuilder.locationId(locationId);
        }

        return templateBuilder.build();
    }

    @Override
    public String createCertificate() {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public void createSecurityGroup(ISecurityGroupDetails securityGroupDetails) {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public CloudExecResponse runScriptOnMachine(String script, String serverIp, ISshDetails sshDetails) {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public CloudServerCreated create(String name, String imageRef, String flavorRef, CloudCreateServerOptions... options) throws RunNodesException {
        throw new UnsupportedOperationException("this method is no longer supported, please use create(MachineOptions machineOpts) instead.");
    }
}
