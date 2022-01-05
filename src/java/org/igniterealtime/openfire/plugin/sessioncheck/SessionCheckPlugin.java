package org.igniterealtime.openfire.plugin.sessioncheck;

import java.io.File;
import java.util.Collection;
import java.util.Timer;
import java.util.TimerTask;

import org.jivesoftware.openfire.SessionManager;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.container.Plugin;
import org.jivesoftware.openfire.container.PluginManager;
import org.jivesoftware.openfire.session.ClientSession;
import org.jivesoftware.openfire.spi.ConnectionListener;
import org.jivesoftware.openfire.spi.ConnectionManagerImpl;
import org.jivesoftware.openfire.spi.ConnectionType;
import org.jivesoftware.util.SystemProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An Openfire plugin that checks Sessions...
 *
 * @author 
 */
public class SessionCheckPlugin implements Plugin
{
    private static final Logger Log = LoggerFactory.getLogger( SessionCheckPlugin.class );

    private SessionManager sessionManager = null;
    private Timer timer = null;

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
        if (XMPP_SESSIONCHECK_DEBUGLOGGING.getValue())
        {
            Log.debug("Set Timer for future sessions checks - first start in: 5mins, interval: {} seconds",String.valueOf(XMPP_SESSIONCHECK_INTERVAL.getValue()));
        }
        timer = new Timer(true);
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                try {
                    if (XMPP_SESSIONCHECK_ENABLED.getValue())
                        checkSessions();
                } catch (Exception e) {
                    Log.error("Unknown error while checking the sessions", e.getMessage(),e);
                }
            }
        }, 60*1000*5, XMPP_SESSIONCHECK_INTERVAL.getValue()*1000);
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

                        runRestartConnnectionListener();

                        if (XMPP_SESSIONCHECK_DEBUGLOGGING.getValue())
                        {
                            Log.info("Restart Worktimer");
                        }
                        setTimer();
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
                listenerPlain.enable( false );
                listenerPlain.enable( true );
            }

            if (listenerSSLLegacy!=null&&listenerSSLLegacy.isEnabled())
            {
                if (XMPP_SESSIONCHECK_DEBUGLOGGING.getValue())
                {
                    Log.info("Restart legacy ssl listener");
                }
                listenerSSLLegacy.enable( false );
                listenerSSLLegacy.enable( true );
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
    }

    @Override
    public void destroyPlugin()
    {
        Log.info("Destroy SessionCheck Plugin");
        try {
            cancelTimer();
        } catch (Exception e) {
        }
    }

}
