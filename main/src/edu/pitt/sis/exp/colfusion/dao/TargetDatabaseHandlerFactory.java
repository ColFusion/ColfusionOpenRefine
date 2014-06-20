/*

Copyright 2014, xxl
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are
met:

 * Redistributions of source code must retain the above copyright
notice, this list of conditions and the following disclaimer.
 * Redistributions in binary form must reproduce the above
copyright notice, this list of conditions and the following disclaimer
in the documentation and/or other materials provided with the
distribution.
 * Neither the name of Google Inc. nor the names of its
contributors may be used to endorse or promote products derived from
this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
"AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,           
DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY           
THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
(INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

 */
package edu.pitt.sis.exp.colfusion.dao;

import java.sql.SQLException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * @author xxl
 *
 */
public class TargetDatabaseHandlerFactory {
    private static MetadataDbHandler metadataDbHandler;

    final static Logger logger = LoggerFactory.getLogger(TargetDatabaseHandlerFactory.class.getName());
    
    static {
        //TODO:　Read host,port, etc. from config file and/or system properties
        DatabaseConnectionInfo connectioInfo = new DatabaseConnectionInfo("localhost", 3306, "dataverse", "dataverse", "colfusion");
        try {
            metadataDbHandler = new MetadataDbHandler(new MySQLDatabaseHandler(connectioInfo));
        } catch (ClassNotFoundException e) {
           logger.error("Couldn't intinialize meatdata db handler", e);
        }
    }
    
    public static DatabaseHandler getTargetDatabaseHandler(final int sid) throws SQLException, ClassNotFoundException {
        DatabaseConnectionInfo connectioInfo = metadataDbHandler.getTargetDbConnectionInfo(sid);
        
       //TODO: this whole thing should be in a separate project and be shared with ColFusion
        return new MySQLDatabaseHandler(connectioInfo);
    }
    
    public static MetadataDbHandler getMetadataDbHandler() {
        return metadataDbHandler;
    }
}
