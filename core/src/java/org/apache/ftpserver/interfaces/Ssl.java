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

package org.apache.ftpserver.interfaces;

import java.security.GeneralSecurityException;

import javax.net.ssl.SSLContext;

/**
 * SSL interface.
 */
public 
interface Ssl {
    
    SSLContext getSSLContext() throws GeneralSecurityException;
    SSLContext getSSLContext(String protocol) throws GeneralSecurityException;
    
    /**
     * Returns the cipher suites that should be enabled for this connection.
     * Must return null if the default (as decided by the JVM) cipher suites
     * should be used.
     * @return An array of cipher suites, or null.
     */
    String[] getEnabledCipherSuites();
    ClientAuth getClientAuth();
}
