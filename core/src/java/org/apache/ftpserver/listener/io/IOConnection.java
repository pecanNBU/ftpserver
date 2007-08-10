/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */  

package org.apache.ftpserver.listener.io;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;
import java.net.SocketException;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

import org.apache.ftpserver.IODataConnectionFactory;
import org.apache.ftpserver.FtpRequestImpl;
import org.apache.ftpserver.FtpSessionImpl;
import org.apache.ftpserver.FtpWriter;
import org.apache.ftpserver.ftplet.FtpException;
import org.apache.ftpserver.interfaces.ClientAuth;
import org.apache.ftpserver.interfaces.FtpServerContext;
import org.apache.ftpserver.interfaces.FtpServerSession;
import org.apache.ftpserver.interfaces.Ssl;
import org.apache.ftpserver.listener.AbstractConnection;
import org.apache.ftpserver.listener.ConnectionObserver;
import org.apache.ftpserver.listener.FtpProtocolHandler;
import org.apache.ftpserver.util.IoUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * This is a generic request handler. It delegates 
 * the request to appropriate method in subclass.
 */
public class IOConnection extends AbstractConnection implements Runnable {
    
    private final Logger LOG = LoggerFactory.getLogger(IOConnection.class);
    
    private Socket controlSocket;
    private IOFtpResponseOutput writer;
    private BufferedReader reader;
    private boolean isConnectionClosed;
    
    private FtpProtocolHandler protocolHandler;
    
    
    /**
     * Constructor - set the control socket.
     */
    public IOConnection(FtpServerContext serverContext, Socket controlSocket, IOListener listener) throws IOException {
        super(serverContext);
        
        this.controlSocket = controlSocket;
        
        // TODO how can we share this between all connections?
        protocolHandler = new FtpProtocolHandler(serverContext);
        
        // data connection object
        
        // reader object
        ftpSession = new FtpSessionImpl(serverContext);
        ftpSession.setClientAddress(this.controlSocket.getInetAddress());
        ftpSession.setServerAddress(this.controlSocket.getLocalAddress());
        ftpSession.setServerPort(this.controlSocket.getLocalPort());
        ftpSession.setListener(listener);

        IODataConnectionFactory dataCon = new IODataConnectionFactory(this.serverContext, ftpSession);
        dataCon.setServerControlAddress(controlSocket.getLocalAddress());
        
        ftpSession.setFtpDataConnection(dataCon);

        if(this.controlSocket instanceof SSLSocket) {
            SSLSocket sslControlSocket = (SSLSocket) this.controlSocket;
            
            try {
                ftpSession.setClientCertificates(sslControlSocket.getSession().getPeerCertificates());
            } catch(SSLPeerUnverifiedException e) {
                // ignore, certificate will not be available to the session
            }
        }
        
        // writer object
        writer = new IOFtpResponseOutput(this.controlSocket);
    }
    
    /**
     * Set observer.
     */
    public void setObserver(ConnectionObserver observer) {
        
        // set writer observer
        FtpWriter writer = this.writer;
        if(writer != null) {
            writer.setObserver(observer);
        }
        
        super.setObserver(observer);
    }   
    
    /**
     * Server one FTP client connection.
     */
    public void run() {
        if(ftpSession == null ) {
            return;
        }
        if(serverContext == null) {
        	return;
        }
        
        try {
            protocolHandler.onConnectionOpened(this, ftpSession, writer);
            
            reader = new BufferedReader(new InputStreamReader(controlSocket.getInputStream(), "UTF-8"));
            do {
                notifyObserver();
                String commandLine = reader.readLine();
                
                // test command line
                if(commandLine == null) {
                    break;
                }
                commandLine = commandLine.trim();
                if(commandLine.equals("")) {
                    continue;
                }
                
                spyRequest(commandLine);
                
                // parse and check permission
                FtpRequestImpl request = new FtpRequestImpl(commandLine);
                
                protocolHandler.onRequestReceived(this, ftpSession, writer, request);
            }
            while(!isConnectionClosed);
        } catch(SocketException ex) {
            // socket closed - no need to do anything
        } catch(SSLException ex) {
            LOG.warn("The client did not initiate the SSL connection correctly", ex);
        } catch(Exception ex) {
            LOG.warn("Client error, closing session", ex);
        }
        finally {
            // close all resources if not done already
            if(!isConnectionClosed) {
                 serverContext.getConnectionManager().closeConnection(this);
            }
        }
    }

    /**
     * Close connection. This is called by the connection service.
     */
    public void close() {
        
        // check whether already closed or not
        synchronized(this) {
            if(isConnectionClosed) {
                return;
            }
            isConnectionClosed = true;
        }

        protocolHandler.onConnectionClosed(this, ftpSession, writer);
        
        // close buffered reader
        BufferedReader reader = this.reader;
        if(reader != null) {
            IoUtils.close(reader);
            reader = null;
        }
        
        // close control socket
        Socket controlSocket = this.controlSocket;
        if (controlSocket != null) {
            try {
                controlSocket.close();
            }
            catch(Exception ex) {
                LOG.warn("RequestHandler.close()", ex);
            }
            controlSocket = null;
        }
    }   
    
    /**
     * Returns a socket layered over an existing socket.
     */
    private Socket createSocket(Ssl ssl, String protocol,
                               Socket soc, 
                               boolean clientMode) throws Exception {
        // already wrapped - no need to do anything
        if(soc instanceof SSLSocket) {
            return soc;
        }
        
        // get socket factory
        SSLContext ctx = ssl.getSSLContext(protocol);
        SSLSocketFactory socFactory = ctx.getSocketFactory();
        
        // create socket
        String host = soc.getInetAddress().getHostAddress();
        int port = soc.getLocalPort();
        SSLSocket ssoc = (SSLSocket)socFactory.createSocket(soc, host, port, true);
        ssoc.setUseClientMode(clientMode);
        
        // initialize socket
        if(ssl.getClientAuth() == ClientAuth.NEED) {
            ssoc.setNeedClientAuth(true);
        } else if(ssl.getClientAuth() == ClientAuth.WANT) {
            ssoc.setWantClientAuth(true);
        }

        if(ssl.getEnabledCipherSuites() != null) {
            ssoc.setEnabledCipherSuites(ssl.getEnabledCipherSuites());
        }

        
        return ssoc;
    }

    
    /**
     * Create secure socket.
     */
    public void afterSecureControlChannel(FtpServerSession ftpSession, String protocol) throws Exception {

        // change socket to SSL socket
        Ssl ssl = ftpSession.getListener().getSsl();
        if(ssl == null) {
            throw new FtpException("Socket factory SSL not configured");
        }
        Socket ssoc = createSocket(ssl, protocol, controlSocket, false);
        
        // change streams
        reader = new BufferedReader(new InputStreamReader(ssoc.getInputStream(), "UTF-8"));
        writer.setControlSocket(ssoc);
        
        // set control socket
        controlSocket = ssoc;
    }

    public void beforeSecureControlChannel(FtpServerSession ftpSession, String type) throws Exception {
        // do nothing
        
    }
}
