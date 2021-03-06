package org.igniterealtime.openfire.plugin.sessioncheck;

import java.io.File;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;

import org.jivesoftware.openfire.Connection.ClientAuth;
import org.jivesoftware.openfire.Connection.TLSPolicy;
import org.jivesoftware.openfire.SessionManager;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.container.Plugin;
import org.jivesoftware.openfire.container.PluginManager;
import org.jivesoftware.openfire.session.ClientSession;
import org.jivesoftware.openfire.spi.ConnectionListener;
import org.jivesoftware.openfire.spi.ConnectionManagerImpl;
import org.jivesoftware.openfire.spi.ConnectionType;
import org.jivesoftware.util.PropertyEventDispatcher;
import org.jivesoftware.util.PropertyEventListener;
import org.jivesoftware.util.SystemProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An Openfire plugin that checks Sessions...
 *
 * @author 
 */
public class SessionCheckPlugin implements Plugin, PropertyEventListener
{
    private static final Logger Log = LoggerFactory.getLogger( SessionCheckPlugin.class );

    private SessionManager sessionManager = null;
    private Timer timer = null;
    private Timer restartTimer = null;
    private long lastChange = 0;

    public static final SystemProperty<Boolean> XMPP_SESSIONCHECK_ENABLED = SystemProperty.Builder.ofType(Boolean.class)
            .setKey("plugin.sessioncheck.enabled")
            .setPlugin( "sessioncheck" )
            .setDefaultValue(false)
            .setDynamic(false)
            .build();

    public static final SystemProperty<Integer> XMPP_SESSIONCHECK_INTERVAL = SystemProperty.Builder.ofType(Integer.class)
            .setKey("plugin.sessioncheck.interval")
            .setPlugin( "sessioncheck" )
            .setDefaultValue(60)
            .setDynamic(false)
            .build();

    public static final SystemProperty<Integer> XMPP_SESSIONCHECK_RESTARTINTERVAL = SystemProperty.Builder.ofType(Integer.class)
            .setKey("plugin.sessioncheck.restartinterval")
            .setPlugin( "sessioncheck" )
            .setDefaultValue(-1)
            .setDynamic(false)
            .build();

    public static final SystemProperty<Integer> XMPP_SESSIONCHECK_MINTORESTART = SystemProperty.Builder.ofType(Integer.class)
            .setKey("plugin.sessioncheck.mintorestart")
            .setPlugin( "sessioncheck" )
            .setDefaultValue(3)
            .setDynamic(true)
            .build();
    
    public static final SystemProperty<Boolean> XMPP_SESSIONCHECK_DEBUGLOGGING = SystemProperty.Builder.ofType(Boolean.class)
            .setKey("plugin.sessioncheck.debuglog")
            .setPlugin( "sessioncheck" )
            .setDefaultValue(false)
            .setDynamic(true)
            .build();

    private void setTimer() {
        cancelTimer();
        if (XMPP_SESSIONCHECK_ENABLED.getValue()) {
            if (XMPP_SESSIONCHECK_DEBUGLOGGING.getValue())
            {
                Log.info("Set Timer for future sessions checks - first start in: 5mins, interval: {} seconds",String.valueOf(XMPP_SESSIONCHECK_INTERVAL.getValue()));
            }
            timer = new Timer(true);
            timer.schedule(new TimerTask() {
                @Override
                public void run() {
                    try {
                        checkSessions();
                    } catch (Exception e) {
                        Log.error("Unknown error while checking the sessions", e.getMessage(),e);
                    }
                }
            }, 60*1000*5, XMPP_SESSIONCHECK_INTERVAL.getValue()*1000);
        }else {
            if (XMPP_SESSIONCHECK_DEBUGLOGGING.getValue())
            {
                Log.info("Session check timer is disabled");
            }
        }
    }

    private void setRestartTimer() {
        cancelRestartTimer();
        if (XMPP_SESSIONCHECK_RESTARTINTERVAL.getValue()>0)
        {
            if (XMPP_SESSIONCHECK_DEBUGLOGGING.getValue())
            {
                Log.info("Set Restart Timer for auto restarting the Connection Listener - Interval {} mins", String.valueOf(XMPP_SESSIONCHECK_RESTARTINTERVAL.getValue()));
            }
            restartTimer = new Timer(true);
            restartTimer.schedule(new TimerTask() {
                @Override
                public void run() {
                    try {
                        runRestartConnnectionListener();
                    } catch (Exception e) {
                        Log.error("Unknown error while restarting the connection listeners", e.getMessage(),e);
                    }
                }
            }, XMPP_SESSIONCHECK_RESTARTINTERVAL.getValue()*60*1000, XMPP_SESSIONCHECK_RESTARTINTERVAL.getValue()*60*1000);
        }
        else {
            if (XMPP_SESSIONCHECK_DEBUGLOGGING.getValue())
            {
                Log.info("Restart timer is disabled (-1)");
            }
        }
    }
    
    private void cancelTimer() {
        if (timer != null) {
            try {
                if (XMPP_SESSIONCHECK_DEBUGLOGGING.getValue())
                {
                    Log.debug("Cancel old running sessioncheck timer");
                }
                timer.cancel();
                timer.purge();
            } catch (Exception e) {
                Log.error("Error while canceling the timer...", e.getMessage(),e);
            }
        }
        timer = null;
    }

    private void cancelRestartTimer() {
        if (restartTimer != null) {
            try {
                if (XMPP_SESSIONCHECK_DEBUGLOGGING.getValue())
                {
                    Log.debug("Cancel old running restart timer");
                }
                restartTimer.cancel();
                restartTimer.purge();
            } catch (Exception e) {
                Log.error("Error while canceling the restarttimer...", e.getMessage(),e);
            }
        }
        restartTimer = null;
    }

    public void checkSessions() {

        Collection<ClientSession> sessions = null;
        sessions = sessionManager.getSessions();

        if (sessions != null) {
            if (XMPP_SESSIONCHECK_DEBUGLOGGING.getValue())
            {
                Log.info("Start sessioncheck");
            }
            int counter=0;
            for (ClientSession session : sessions) {
                if (session == null)
                    continue;

                try {
                    if (XMPP_SESSIONCHECK_DEBUGLOGGING.getValue())
                    {
                        Log.debug("Check session with jid {}",session.getAddress().toString());
                    }
                    session.getHostAddress();
                } catch (Exception e) {
                    Log.warn("Found invalid session on jid: {}",session.getAddress().toString());
                    counter++;
                }
            }

            if (counter>=XMPP_SESSIONCHECK_MINTORESTART.getValue())
            {
                Log.warn("Found {} invalid sessions",String.valueOf(counter));
                new Thread(new Runnable() {

                    @Override
                    public void run() {
                        if (XMPP_SESSIONCHECK_DEBUGLOGGING.getValue())
                        {
                            Log.info("Cancel Worktimer");
                        }
                        cancelTimer();
                        cancelRestartTimer();

                        runRestartConnnectionListener();

                        if (XMPP_SESSIONCHECK_DEBUGLOGGING.getValue())
                        {
                            Log.info("Restart Worktimer");
                        }
                        setTimer();
                        setRestartTimer();
                    }
                });
            }
            else {
                if (XMPP_SESSIONCHECK_DEBUGLOGGING.getValue())
                {
                    Log.info("No invalid sessions found...");
                }
            }
        }
    }

    public static void runRestartConnnectionListener() {
        final ConnectionManagerImpl manager = (ConnectionManagerImpl) XMPPServer.getInstance().getConnectionManager();
        ConnectionType connectionType = ConnectionType.valueOf( "SOCKET_C2S" );
        final ConnectionListener listenerPlain = manager.getListener( connectionType, false );
        final ConnectionListener listenerSSLLegacy = manager.getListener( connectionType, true );

        Log.info("Restart Connection Listener now");
        try {
            if (listenerPlain!=null&&listenerPlain.isEnabled())
            {
                if (XMPP_SESSIONCHECK_DEBUGLOGGING.getValue())
                {
                    Log.info("Restart plain listener");
                }
                int port = listenerPlain.getPort();
                TLSPolicy policy = listenerPlain.getTLSPolicy();
                ClientAuth auth = listenerPlain.getClientAuth();
                Set<String> encSuite = listenerPlain.getEncryptionCipherSuites();
                Set<String> encProtocol = listenerPlain.getEncryptionProtocols();
                boolean acceptSelfSigned = listenerPlain.acceptSelfSignedCertificates();
                boolean verifyValidity = listenerPlain.verifyCertificateValidity();

                listenerPlain.enable( false );
                listenerPlain.enable( true );

                listenerPlain.setPort( port );
                listenerPlain.setTLSPolicy( policy );
                listenerPlain.setClientAuth( auth );
                listenerPlain.setEncryptionProtocols( encProtocol );
                listenerPlain.setEncryptionCipherSuites( encSuite );
                listenerPlain.setAcceptSelfSignedCertificates( acceptSelfSigned );
                listenerPlain.setVerifyCertificateValidity( verifyValidity );

            }

            if (listenerSSLLegacy!=null&&listenerSSLLegacy.isEnabled())
            {
                if (XMPP_SESSIONCHECK_DEBUGLOGGING.getValue())
                {
                    Log.info("Restart legacy ssl listener");
                }
                int port = listenerSSLLegacy.getPort();
                TLSPolicy policy = listenerSSLLegacy.getTLSPolicy();
                ClientAuth auth = listenerSSLLegacy.getClientAuth();
                Set<String> encSuite = listenerSSLLegacy.getEncryptionCipherSuites();
                Set<String> encProtocol = listenerSSLLegacy.getEncryptionProtocols();
                boolean acceptSelfSigned = listenerSSLLegacy.acceptSelfSignedCertificates();
                boolean verifyValidity = listenerSSLLegacy.verifyCertificateValidity();

                listenerSSLLegacy.enable( false );
                listenerSSLLegacy.enable( true );

                listenerSSLLegacy.setPort( port );
                listenerSSLLegacy.setTLSPolicy( policy );
                listenerSSLLegacy.setClientAuth( auth );
                listenerSSLLegacy.setEncryptionProtocols( encProtocol );
                listenerSSLLegacy.setEncryptionCipherSuites( encSuite );
                listenerSSLLegacy.setAcceptSelfSignedCertificates( acceptSelfSigned );
                listenerSSLLegacy.setVerifyCertificateValidity( verifyValidity );

            }

        }
        catch (Exception e)
        {
            Log.error("Could not restart Connection Listener",e.getMessage(),e);
        }
    }

    @Override
    public void initializePlugin( PluginManager manager, File pluginDirectory )
    {
        SystemProperty.removePropertiesForPlugin("sessioncheck");
        sessionManager = SessionManager.getInstance();
        Log.info("Initialize SessionCheck Plugin enabled:"+XMPP_SESSIONCHECK_ENABLED.getDisplayValue());
        setTimer();
        setRestartTimer();
        PropertyEventDispatcher.addListener(this);
    }

    @Override
    public void destroyPlugin()
    {
        Log.info("Destroy SessionCheck Plugin");
        try {
            cancelTimer();
            cancelRestartTimer();
        } catch (Exception e) {
        }
        PropertyEventDispatcher.removeListener(this);
    }

    @Override
    public void propertySet(String property, Map<String, Object> params) {
        if (property.equals("plugin.sessioncheck.enabled"))
        {
            setTimer();
        }
        if (property.equals("plugin.sessioncheck.enabled"))
        {
            setRestartTimer();
        }
    }

    @Override
    public void propertyDeleted(String property, Map<String, Object> params) {
        if (property.equals("plugin.sessioncheck.enabled"))
        {
            cancelTimer();
        }
        if (property.equals("plugin.sessioncheck.enabled"))
        {
            cancelRestartTimer();
        }
    }

    @Override
    public void xmlPropertySet(String property, Map<String, Object> params) {
        // TODO Auto-generated method stub
        
    }

    @Override
    public void xmlPropertyDeleted(String property, Map<String, Object> params) {
        // TODO Auto-generated method stub
        
    }

}
