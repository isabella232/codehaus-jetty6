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


package org.mortbay.jetty.security.jaspi.modules;

import java.security.Principal;

import javax.security.auth.message.callback.CallerPrincipalCallback;
import javax.security.auth.message.callback.GroupPrincipalCallback;
import javax.security.auth.Subject;

/**
 * @version $Rev:$ $Date:$
 */
public class LoginResult
{

    private final boolean success;
    private final CallerPrincipalCallback callerPrincipalCallback;
    private final GroupPrincipalCallback groupPrincipalCallback;

    public LoginResult(boolean success, Principal userPrincipal, String[] groups, Subject subject)
    {
        this.success = success;
        this.callerPrincipalCallback = new CallerPrincipalCallback(subject, userPrincipal);
        this.groupPrincipalCallback = new GroupPrincipalCallback(subject, groups);
    }

    public boolean isSuccess()
    {
        return success;
    }

    public CallerPrincipalCallback getCallerPrincipalCallback()
    {
        return callerPrincipalCallback;
    }

    public GroupPrincipalCallback getGroupPrincipalCallback()
    {
        return groupPrincipalCallback;
    }
}
