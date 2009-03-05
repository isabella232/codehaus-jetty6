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

package org.mortbay.jetty.security;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.mortbay.jetty.util.LazyList;

/**
 * 
 * Badly named class that holds the role and user data constraint info for a
 * path/http method combination, extracted and combined from security
 * constraints.
 * 
 * @version $Rev$ $Date$
 */
public class RoleInfo
{
    private final static String[] NO_ROLES={};
    private boolean _isAnyRole;
    private boolean _unchecked;
    private boolean _forbidden;
    private UserDataConstraint _userDataConstraint;

    private String[] _roles = NO_ROLES;

    public boolean isUnchecked()
    {
        return _unchecked;
    }

    public void setUnchecked(boolean unchecked)
    {
        this._unchecked = unchecked;
        if (unchecked)
        {
            _forbidden=false;
            _roles=NO_ROLES;
            _isAnyRole=false;
        }
    }

    public boolean isForbidden()
    {
        return _forbidden;
    }

    public void setForbidden(boolean forbidden)
    {
        this._forbidden = forbidden;
        if (forbidden)
        {
            _unchecked = false;
            _userDataConstraint = null;
            _isAnyRole=false;
            _roles=NO_ROLES;
        }
    }

    public boolean isAnyRole()
    {
        return _isAnyRole;
    }

    public void setAnyRole(boolean anyRole)
    {
        this._isAnyRole=anyRole;
        if (anyRole)
        {
            _unchecked = false;
            _roles=NO_ROLES;
        }
    }

    public UserDataConstraint getUserDataConstraint()
    {
        return _userDataConstraint;
    }

    public void setUserDataConstraint(UserDataConstraint userDataConstraint)
    {
        if (userDataConstraint == null) throw new NullPointerException("Null UserDataConstraint");
        if (this._userDataConstraint == null)
        {
            this._userDataConstraint = userDataConstraint;
        }
        else
        {
            this._userDataConstraint = this._userDataConstraint.combine(userDataConstraint);
        }
    }

    public String[] getRoles()
    {
        return _roles;
    }
    
    public void addRole(String role)
    {
        _roles=(String[])LazyList.addToArray(_roles,role,String.class);
    }

    public void combine(RoleInfo other)
    {
        if (other._forbidden)
            setForbidden(true);
        else if (other._unchecked) 
            setUnchecked(true);
        else if (other._isAnyRole)
            setAnyRole(true);
        else if (!_isAnyRole)
        {
            for (String r : other._roles)
                _roles=(String[])LazyList.addToArray(_roles,r,String.class);
        }
        
        setUserDataConstraint(other._userDataConstraint);
    }
    
    public String toString()
    {
        return "{RoleInfo"+(_forbidden?",F":"")+(_unchecked?",U":"")+(_isAnyRole?",*":Arrays.asList(_roles).toString())+"}";
    }
}