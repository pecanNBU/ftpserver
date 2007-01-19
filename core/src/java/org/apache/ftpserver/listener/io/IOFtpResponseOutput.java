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

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.InetAddress;
import java.net.Socket;

import org.apache.ftpserver.FtpWriter;
import org.apache.ftpserver.ftplet.FtpResponse;
import org.apache.ftpserver.util.IoUtils;

public class IOFtpResponseOutput extends FtpWriter {

    private Writer writer;
    private InetAddress serverAddress;

    public void write(FtpResponse response) throws IOException {

        String out = response.toString();

        spyResponse(out);
        writer.write(out);
        writer.flush();
    }

    /**
     * Set the control socket.
     */
    public void setControlSocket(Socket soc) throws IOException {
        serverAddress = soc.getLocalAddress();
        writer = new OutputStreamWriter(soc.getOutputStream(), "UTF-8");
    }
        
    /**
     * Close writer.
     */
    public void close() {
        IoUtils.close(writer);
    }

    protected InetAddress getFallbackServerAddress() {
        return serverAddress;
    }

    
}