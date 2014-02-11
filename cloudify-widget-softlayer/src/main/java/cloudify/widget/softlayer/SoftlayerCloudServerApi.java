package cloudify.widget.softlayer;

import static com.google.common.collect.Collections2.*;

import cloudify.widget.api.clouds.*;
import cloudify.widget.common.CloudExecResponseImpl;
import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.net.HostAndPort;
import com.google.inject.Guice;
import com.google.inject.Injector;
import org.apache.commons.lang3.StringUtils;
import org.jclouds.compute.ComputeService;
import org.jclouds.compute.domain.*;
import org.jclouds.domain.LoginCredentials;
import org.jclouds.javax.annotation.Nullable;
import org.jclouds.logging.config.NullLoggingModule;
import org.jclouds.softlayer.SoftLayerApi;
import org.jclouds.softlayer.domain.VirtualGuest;
import org.jclouds.ssh.SshClient;
import org.jclouds.sshj.config.SshjSshClientModule;
import org.jclouds.util.Strings2;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.util.*;

/**
 * User: eliranm
 * Date: 2/4/14
 * Time: 3:41 PM
 */
public class SoftlayerCloudServerApi implements CloudServerApi {

    private static Logger logger = LoggerFactory.getLogger(SoftlayerCloudServerApi.class);

    private ComputeService computeService = null;

    private SoftlayerConnectDetails connectDetails;


    public SoftlayerCloudServerApi(){

    }



    @Override
    public void connect(IConnectDetails connectDetails) {
       setConnectDetails( connectDetails);
        connect();
    }

    @Override
    public Collection<CloudServer> getAllMachinesWithTag(final String tag) {
        logger.info("getting all machines with tag [{}]",tag);
        Set<? extends NodeMetadata> nodeMetadatas = computeService.listNodesDetailsMatching(new Predicate<ComputeMetadata>() {
            @Override
            public boolean apply(@Nullable ComputeMetadata computeMetadata) {
                return computeMetadata.getName().startsWith(tag);
            }
        });

        return transform(nodeMetadatas, new Function<NodeMetadata, CloudServer>() {
            @Override
            public SoftlayerCloudServer apply(@Nullable NodeMetadata o) {
                return new SoftlayerCloudServer(computeService, o);
            }
        });
    }

    @Override
    public CloudServer get(String serverId) {
        CloudServer cloudServer = null;
        NodeMetadata nodeMetadata = computeService.getNodeMetadata(serverId);
        if (nodeMetadata != null) {
            cloudServer = new SoftlayerCloudServer(computeService, nodeMetadata);
        }
        return cloudServer;
    }

    @Override
    public boolean delete(String id) {
        boolean deleted = false;
        SoftlayerCloudServer cloudServer = null;
        if (id != null) {
            cloudServer = (SoftlayerCloudServer) get(id);
        }
        if (cloudServer != null) {
            if (logger.isDebugEnabled()) {
                logger.debug("calling destroyNode, status is [{}]", cloudServer.getStatus());
            }
            try {
                computeService.destroyNode(id);
                deleted = true;
            } catch (RuntimeException e) {
                throw new SoftlayerCloudServerApiOperationFailureException(
                        String.format("delete operation failed for server with id [%s].", id), e);
            }
        }
        if (!deleted) {
            throw new SoftlayerCloudServerApiOperationFailureException(
                    String.format("delete operation failed for server with id [%s].", id));
        }
        return deleted;
    }

    @Override
    public void rebuild(String id) {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public void setConnectDetails(IConnectDetails connectDetails) {
        logger.info("connecting");
        if (!( connectDetails instanceof SoftlayerConnectDetails )){
            throw new RuntimeException("expected SoftlayerConnectDetails implementation");
        }
        this.connectDetails = (SoftlayerConnectDetails) connectDetails;

    }

    @Override
    public void connect() {
        computeService = SoftlayerCloudUtils.computeServiceContext( connectDetails.username, connectDetails.key, connectDetails.isApiKey ).getComputeService();
    }

    @Override
    public Collection<? extends CloudServerCreated> create( MachineOptions machineOpts ) {

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
        throw new UnsupportedOperationException("create certificate is unsupported");
    }

    @Override
    public void createSecurityGroup(ISecurityGroupDetails securityGroupDetails) {
        throw new UnsupportedOperationException("create security group is unsupported in this implementation");
        //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public CloudExecResponse runScriptOnMachine(String script, String serverIp, ISshDetails sshDetails) {

        SoftlayerSshDetails softlayerSshDetails = getMachineCredentialsByIp( serverIp );
        //retrieve missing ssh details
        String user = softlayerSshDetails.user();
        String password = softlayerSshDetails.password();
        int port = softlayerSshDetails.port();

        logger.debug("Run ssh on server: {} script: {}" , serverIp, script );
        Injector i = Guice.createInjector(new SshjSshClientModule(), new NullLoggingModule());
        SshClient.Factory factory = i.getInstance(SshClient.Factory.class);
        LoginCredentials loginCredentials = LoginCredentials.builder().user(user).password(password).build();
        //.privateKey(Strings2.toStringAndClose(new FileInputStream(conf.server.bootstrap.ssh.privateKey)))

        SshClient sshConnection = factory.create(HostAndPort.fromParts(serverIp, port),
                loginCredentials );
        ExecResponse execResponse = null;
        try{
            sshConnection.connect();
            logger.info("ssh connected, executing");
            execResponse = sshConnection.exec(script);
            logger.info("finished execution");
        }
        finally{
            if (sshConnection != null)
                sshConnection.disconnect();
        }

        return new CloudExecResponseImpl( execResponse );
    }


    private SoftlayerSshDetails getMachineCredentialsByIp( final String ip ){

        Set<? extends NodeMetadata> nodeMetadatas = computeService.listNodesDetailsMatching(new Predicate<ComputeMetadata>() {
            @Override
            public boolean apply(ComputeMetadata computeMetadata) {
                NodeMetadata nodeMetadata = (NodeMetadata) computeMetadata;
                Set<String> publicAddresses = nodeMetadata.getPublicAddresses();
                return publicAddresses.contains(ip);
            }
        });

//        NodeMetadata nodeMetadata = computeService.getNodeMetadata(nodeId);
        if( nodeMetadatas.isEmpty() ){
            throw new RuntimeException( "Machine [" + ip + "] was not found" );
        }

        NodeMetadata nodeMetadata = nodeMetadatas.iterator().next();

        LoginCredentials loginCredentials = nodeMetadata.getCredentials();
        String user = loginCredentials.getUser();
        String password = loginCredentials.getPassword();
        int port = nodeMetadata.getLoginPort();

        return new SoftlayerSshDetails( port, user, password );
    }

}
